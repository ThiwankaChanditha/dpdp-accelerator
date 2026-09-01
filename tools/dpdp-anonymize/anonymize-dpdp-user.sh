#!/usr/bin/env bash
#
# Quick, standalone pseudonymization script for WSO2DPDP_DB (MySQL).
# Not part of the accelerator or PR #67 - a lean personal alternative that covers
# the same tables via plain SQL instead of a multi-module Java tool.
#
# --user-id is the accelerator's own identifier, which throughout this schema IS the username
# (PrivilegedCarbonContext.getUsername(), enforced elsewhere to look like an email) - COMPLAINT.USER_ID,
# COMPLAINT_EVENT.ACTOR_USER_ID, DPDP_CONSENT_STATUS_AUDIT.ACTION_BY, and DPDP_CONSENT_HISTORY.ACTION_BY
# are all written from that exact same value, confirmed by reading the accelerator's own DAO/service
# code. It is NOT a separate WSO2 IS SCIM UUID. --pseudonym can be anything that isn't a real
# identifier - a random UUID (e.g. via `uuidgen`) is a good choice precisely because it can't
# collide with a real username.
#
# Known residual gap: EVENT.PAYLOAD's userId field (for user.data.change/user.account.delete) is
# populated by code that prefers an opaque identity-mgt USER_ID property and only falls back to
# the username - what WSO2 IS actually puts there under the hood isn't confirmable from this repo's
# source. If a tenant's event payloads turn out to hold something other than the username you pass
# here, those specific rows won't match and will need a second pass with that other value.
#
# Deliberate simplifications vs. the PR #67 tool (know these before you rely on it):
#   - No separate "trusted username alias" concept - moot here, since USER_ID/ACTION_BY already
#     *is* the username in this schema (see above), so a single exact-match value covers it.
#   - JSON fields (DPDP_CONSENT_HISTORY.SNAPSHOT, EVENT.PAYLOAD) are rewritten with a plain
#     substring REPLACE() on the identifier text, not exact JSON-path replacement. Safe in practice
#     for a value as distinctive as an email address or UUID, but not as surgical as path-based
#     replacement.
#   - No SELECT ... FOR UPDATE pre-scan / no long-held locks across the whole tenant - each
#     UPDATE takes its own row locks atomically. Simpler, but you lose the "verify zero remaining
#     before commit, else roll back" guarantee; verification here happens via a query issued
#     AFTER commit, purely for the operator's confirmation, not as a rollback trigger.
#
# Still stop WSO2 IS and every other writer to WSO2DPDP_DB, and take a verified backup, before
# running this with --execute. Default is dry-run (report only, no writes).
#
# Usage:
#   DPDP_DB_USER=... DPDP_DB_PASSWORD=... ./anonymize-dpdp-user.sh \
#     --tenant-domain example.com \
#     --user-id alice@example.com \
#     --pseudonym 216d6aac-7e84-4484-a71e-c52f89b3cb1d \
#     [--execute]
#
# Required env vars: DPDP_DB_USER, DPDP_DB_PASSWORD
# Optional env vars: DPDP_DB_HOST (default localhost), DPDP_DB_PORT (default 3306),
#                     DPDP_DB_NAME (default WSO2DPDP_DB)

set -euo pipefail

: "${DPDP_DB_HOST:=localhost}"
: "${DPDP_DB_PORT:=3306}"
: "${DPDP_DB_NAME:=WSO2DPDP_DB}"
: "${DPDP_DB_USER:?Set DPDP_DB_USER}"
: "${DPDP_DB_PASSWORD:?Set DPDP_DB_PASSWORD}"

usage() {
  cat >&2 <<EOF
Usage: $0 --tenant-domain <domain> --user-id <username-or-id> --pseudonym <replacement-id> [--execute]
EOF
}

TENANT=""
SOURCE=""
TARGET=""
EXECUTE=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --tenant-domain) TENANT="${2:-}"; shift 2 ;;
    --user-id) SOURCE="${2:-}"; shift 2 ;;
    --pseudonym) TARGET="${2:-}"; shift 2 ;;
    --execute) EXECUTE=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown argument: $1" >&2; usage; exit 2 ;;
  esac
done

if [[ -z "$TENANT" || -z "$SOURCE" || -z "$TARGET" ]]; then
  echo "Missing required argument." >&2
  usage
  exit 2
fi

# Strict validation before these values are ever interpolated into SQL text below - this is
# what makes the string interpolation safe. Do not relax these patterns without keeping the
# excluded characters (quotes, backslash, semicolon, whitespace, control chars) excluded.
#
# --user-id/--pseudonym are NOT required to be UUIDs: in this schema the identifier is the
# username (typically an email address) - see the header comment. IDENTIFIER_RE accepts the
# common username/email/UUID charset while still rejecting anything that could break out of a
# single-quoted SQL string literal.
IDENTIFIER_RE='^[A-Za-z0-9._%+@-]{1,255}$'
TENANT_RE='^[A-Za-z0-9.-]+$'

if [[ ! "$SOURCE" =~ $IDENTIFIER_RE ]]; then
  echo "Source user id contains unexpected characters (allowed: letters, digits, . _ % + @ -)." >&2
  exit 2
fi
if [[ ! "$TARGET" =~ $IDENTIFIER_RE ]]; then
  echo "Pseudonym contains unexpected characters (allowed: letters, digits, . _ % + @ -)." >&2
  exit 2
fi
if [[ "$SOURCE" == "$TARGET" ]]; then
  echo "Source user id and pseudonym must be different." >&2
  exit 2
fi
if [[ ! "$TENANT" =~ $TENANT_RE ]]; then
  echo "Tenant domain contains unexpected characters." >&2
  exit 2
fi

MYSQL_CNF=$(mktemp)
trap 'rm -f "$MYSQL_CNF"' EXIT
cat > "$MYSQL_CNF" <<EOF
[client]
host=$DPDP_DB_HOST
port=$DPDP_DB_PORT
user=$DPDP_DB_USER
password=$DPDP_DB_PASSWORD
EOF
chmod 600 "$MYSQL_CNF"

MYSQL=(mysql --defaults-extra-file="$MYSQL_CNF" --batch --raw -N "$DPDP_DB_NAME")

count_matches() {
  local id="$1"
  "${MYSQL[@]}" -e "
    SELECT
      (SELECT COUNT(*) FROM COMPLAINT WHERE ORG_ID='$TENANT' AND USER_ID='$id') +
      (SELECT COUNT(*) FROM COMPLAINT_EVENT WHERE ORG_ID='$TENANT' AND ACTOR_USER_ID='$id') +
      (SELECT COUNT(*) FROM DPDP_CONSENT_STATUS_AUDIT WHERE ORG_ID='$TENANT' AND ACTION_BY='$id') +
      (SELECT COUNT(*) FROM DPDP_CONSENT_HISTORY WHERE ORG_ID='$TENANT'
         AND (ACTION_BY='$id' OR SNAPSHOT LIKE CONCAT('%','$id','%'))) +
      (SELECT COUNT(*) FROM EVENT E JOIN TOPIC T ON T.TOPIC_ID = E.TOPIC_ID AND T.ORG_ID = E.ORG_ID
         WHERE E.ORG_ID='$TENANT' AND T.NAME IN ('user.data.change','user.account.delete')
         AND E.PAYLOAD LIKE CONCAT('%','$id','%'));
  "
}

echo "Tenant:    $TENANT"
echo "Source:    $SOURCE"
echo "Pseudonym: $TARGET"
echo

SOURCE_COUNT=$(count_matches "$SOURCE")
echo "Rows currently referencing the source identifier: $SOURCE_COUNT"

if [[ "$SOURCE_COUNT" -eq 0 ]]; then
  echo "Nothing to do."
  exit 0
fi

TARGET_COUNT=$(count_matches "$TARGET")
if [[ "$TARGET_COUNT" -gt 0 ]]; then
  echo "The pseudonym is already present in covered fields for this tenant." >&2
  echo "Refusing to run - this would merge two identities together." >&2
  exit 1
fi

if [[ "$EXECUTE" -ne 1 ]]; then
  echo
  echo "Dry run only - no changes made. Re-run with --execute to commit."
  exit 0
fi

echo
echo "Applying changes in one transaction..."

"${MYSQL[@]}" <<SQL
START TRANSACTION;

UPDATE COMPLAINT
  SET USER_ID = '$TARGET', USER_NAME = '$TARGET'
  WHERE ORG_ID = '$TENANT' AND USER_ID = '$SOURCE';

UPDATE COMPLAINT_EVENT
  SET ACTOR_USER_ID = '$TARGET', ACTOR_USER_NAME = '$TARGET'
  WHERE ORG_ID = '$TENANT' AND ACTOR_USER_ID = '$SOURCE';

UPDATE DPDP_CONSENT_STATUS_AUDIT
  SET ACTION_BY = '$TARGET'
  WHERE ORG_ID = '$TENANT' AND ACTION_BY = '$SOURCE';

UPDATE DPDP_CONSENT_HISTORY
  SET ACTION_BY = '$TARGET'
  WHERE ORG_ID = '$TENANT' AND ACTION_BY = '$SOURCE';

UPDATE DPDP_CONSENT_HISTORY
  SET SNAPSHOT = REPLACE(SNAPSHOT, '$SOURCE', '$TARGET')
  WHERE ORG_ID = '$TENANT' AND SNAPSHOT LIKE CONCAT('%','$SOURCE','%');

UPDATE EVENT E JOIN TOPIC T ON T.TOPIC_ID = E.TOPIC_ID AND T.ORG_ID = E.ORG_ID
  SET E.PAYLOAD = REPLACE(E.PAYLOAD, '$SOURCE', '$TARGET')
  WHERE E.ORG_ID = '$TENANT'
    AND T.NAME IN ('user.data.change','user.account.delete')
    AND E.PAYLOAD LIKE CONCAT('%','$SOURCE','%');

COMMIT;
SQL

# mysql aborts the whole batch on the first SQL error and closes the connection without ever
# reaching COMMIT; MySQL then rolls back the open transaction as part of tearing down that
# connection. So an error above means nothing was committed - but there is no automatic
# confirmation of that here, hence the explicit post-check below.

echo
echo "Verifying..."
REMAINING=$(count_matches "$SOURCE")
if [[ "$REMAINING" -ne 0 ]]; then
  echo "WARNING: $REMAINING row(s) still reference the source identifier after commit." >&2
  echo "This likely means the data didn't match the exact shape this script expects" >&2
  echo "(e.g. case difference, or an identity recorded only as a username). Investigate manually." >&2
  exit 1
fi

echo "Done. No remaining references to the source identifier for this tenant."

#!/usr/bin/env bash
# Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
#
# WSO2 LLC. licenses this file to you under the Apache License,
# Version 2.0 (the "License"); you may not use this file except
# in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied. See the License for the
# specific language governing permissions and limitations
# under the License.

# Creates the three accounts the integration suite needs and assigns their roles.
#
# The accelerator provisions the DPDP Consent Portal application and both roles
# automatically, but never any user and never role membership - see
# docs/configuration-guide.md, which documents membership as a manual Console step.
# This script is that manual step, automated, so CI and a fresh local install can
# reach a runnable state the same way.
#
# Idempotent: safe to re-run against a long-lived server. Existing users are left
# alone and re-adding an existing role member is a no-op (verified: SCIM2
# `op: add` on `path: users` appends rather than replacing, so other members of
# dpdp-consent-admin are never evicted).
#
# Usage:
#   TEST_PASSWORD='Str0ng!Pass' bash scripts/provision-test-users.sh
#
# Environment:
#   TEST_PASSWORD        required - password set on all three accounts
#   IS_BASE_URL          default https://localhost:9443
#   IS_ADMIN_USERNAME    default admin
#   IS_ADMIN_PASSWORD    default admin
#   IGNORE_HTTPS_ERRORS  default true (the shipped IS certificate is self-signed)
#
# When run inside GitHub Actions it also writes the usernames to $GITHUB_OUTPUT
# as `user`, `user2` and `admin`, so the workflow never hardcodes them twice.

set -euo pipefail

IS_BASE_URL="${IS_BASE_URL:-https://localhost:9443}"
IS_BASE_URL="${IS_BASE_URL%/}"
IS_ADMIN_USERNAME="${IS_ADMIN_USERNAME:-admin}"
IS_ADMIN_PASSWORD="${IS_ADMIN_PASSWORD:-admin}"
IGNORE_HTTPS_ERRORS="${IGNORE_HTTPS_ERRORS:-true}"

# The plain user persona must NOT be an administrator: tests/04-authorization asserts
# it holds only internal_login. dpdp-consent-user carries zero permissions (it is
# created with an empty permission list), so assigning it grants no scopes and cannot
# perturb those assertions - it is assigned only to mirror the documented setup.
# Email-shaped because the accelerator enforces it: the username regex is
# ^[a-zA-Z0-9._-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,4}$, so a bare "dpdp-ci-user" is
# rejected with SCIM2 31301 and provisioning aborts.
USER_NAME="dpdp-ci-user@dpdp.test"
USER_2_NAME="dpdp-ci-user-2@dpdp.test"
ADMIN_NAME="dpdp-ci-admin@dpdp.test"
USER_ROLE="dpdp-consent-user"
ADMIN_ROLE="dpdp-consent-admin"

if [ -z "${TEST_PASSWORD:-}" ]; then
  echo "ERROR: TEST_PASSWORD is not set. Give the test accounts a password that satisfies" >&2
  echo "       the server's password policy, e.g. TEST_PASSWORD='Str0ng!Pass'." >&2
  exit 2
fi

CURL_OPTS=(-s --max-time 30 -u "${IS_ADMIN_USERNAME}:${IS_ADMIN_PASSWORD}")
if [ "${IGNORE_HTTPS_ERRORS}" = "true" ]; then
  CURL_OPTS+=(-k)
fi

# --- helpers ----------------------------------------------------------------

# Reads a value out of a JSON document on stdin. Keeps jq out of the dependency
# list, since python3 is present on GitHub runners and on developer machines.
json() {
  python3 -c "$1"
}

api() {
  curl "${CURL_OPTS[@]}" "$@"
}

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

# Prints the SCIM2 id of a user, or an empty string when the user does not exist.
find_user_id() {
  api "${IS_BASE_URL}/scim2/Users?filter=userName+eq+${1}" | json '
import json, sys
d = json.load(sys.stdin)
res = d.get("Resources") or []
print(res[0]["id"] if res else "")
'
}

# Prints the SCIM2 id of a role by display name, or an empty string.
find_role_id() {
  api "${IS_BASE_URL}/scim2/v2/Roles?filter=displayName+eq+${1}" | json '
import json, sys
d = json.load(sys.stdin)
res = d.get("Resources") or []
print(res[0]["id"] if res else "")
'
}

create_user() {
  local username="$1" body response status
  body=$(TP="${TEST_PASSWORD}" UN="${username}" python3 -c '
import json, os
print(json.dumps({
    "schemas": ["urn:ietf:params:scim:schemas:core:2.0:User"],
    "userName": os.environ["UN"],
    "password": os.environ["TP"],
    "name": {"givenName": "DPDP", "familyName": "CI"},
    "emails": [{"primary": True, "value": os.environ["UN"]}],
}))
')
  response=$(api -X POST "${IS_BASE_URL}/scim2/Users" \
    -H 'Content-Type: application/json' -d "${body}" -w '\n%{http_code}')
  status=$(printf '%s' "${response}" | tail -1)
  if [ "${status}" != "201" ]; then
    # The body can carry a password-policy message, which is the usual cause.
    printf '%s\n' "${response}" | sed '$d' >&2
    fail "creating ${username} returned HTTP ${status}"
  fi
  printf '%s' "${response}" | sed '$d' | json '
import json, sys
print(json.load(sys.stdin)["id"])
'
}

# Adds a user to a role. Appends - existing members are preserved - and is a no-op
# when the user is already a member.
assign_role() {
  local role_id="$1" user_id="$2" status
  status=$(api -X PATCH "${IS_BASE_URL}/scim2/v2/Roles/${role_id}" \
    -H 'Content-Type: application/json' \
    -d "{\"Operations\":[{\"op\":\"add\",\"path\":\"users\",\"value\":[{\"value\":\"${user_id}\"}]}]}" \
    -o /dev/null -w '%{http_code}')
  [ "${status}" = "200" ] || fail "assigning role ${role_id} returned HTTP ${status}"
}

# Creates the user if absent, then ensures the role membership.
provision() {
  local username="$1" role_name="$2" user_id role_id

  role_id=$(find_role_id "${role_name}")
  if [ -z "${role_id}" ]; then
    fail "role ${role_name} does not exist. The accelerator creates it at startup, so this
       usually means provisioning failed silently - check for 'Error provisioning the DPDP
       Consent Portal' in repository/logs/wso2carbon.log, and confirm
       [dpdp_accelerator.consent_portal] auto_provisioning_enabled = true."
  fi

  user_id=$(find_user_id "${username}")
  if [ -n "${user_id}" ]; then
    echo "  ${username}: already exists (${user_id})"
  else
    user_id=$(create_user "${username}")
    echo "  ${username}: created (${user_id})"
  fi

  assign_role "${role_id}" "${user_id}"
  echo "  ${username}: member of ${role_name}"
}

# --- main -------------------------------------------------------------------

echo "Provisioning integration-test accounts on ${IS_BASE_URL}"

api -o /dev/null -f "${IS_BASE_URL}/scim2/Users?count=1" \
  || fail "cannot reach ${IS_BASE_URL}/scim2 as ${IS_ADMIN_USERNAME}. Is the server running,
       and are IS_ADMIN_USERNAME / IS_ADMIN_PASSWORD correct?"

provision "${USER_NAME}"   "${USER_ROLE}"
provision "${USER_2_NAME}" "${USER_ROLE}"
provision "${ADMIN_NAME}"  "${ADMIN_ROLE}"

if [ -n "${GITHUB_OUTPUT:-}" ]; then
  {
    echo "user=${USER_NAME}"
    echo "user2=${USER_2_NAME}"
    echo "admin=${ADMIN_NAME}"
  } >> "${GITHUB_OUTPUT}"
fi

echo "Done. Point TEST_USER_USERNAME at ${USER_NAME} and TEST_CONSENT_ADMIN_USERNAME at ${ADMIN_NAME}."

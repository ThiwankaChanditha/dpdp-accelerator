# DPDP User Anonymization Script

A small command-line tool for permanently replacing one user's identifier with a random
placeholder ("pseudonym") across the WSO2 DPDP Accelerator's database — for example, to fulfil a
data subject's erasure/anonymization request without deleting their historical records outright.

It only requires database access — it does not need WSO2 Identity Server to be running, and it
doesn't modify anything outside the database.

## What it does

Given a tenant, a user's ID, and a replacement value you provide, it finds every place in the
database that identifies that user and **replaces** it with the replacement value — nothing is
ever set to blank/`NULL`, and no rows are deleted. By default it only **reports** what it would
change; nothing is written until you explicitly ask it to.

## Exactly what gets changed

| Table | Column(s) changed | How |
|---|---|---|
| `COMPLAINT` | `USER_ID`, `USER_NAME` | Both set to the replacement value, for rows where `USER_ID` matches |
| `COMPLAINT_EVENT` | `ACTOR_USER_ID`, `ACTOR_USER_NAME` | Both set to the replacement value, for rows where `ACTOR_USER_ID` matches |
| `DPDP_CONSENT_STATUS_AUDIT` | `ACTION_BY` | Set to the replacement value, for rows where `ACTION_BY` matches |
| `DPDP_CONSENT_HISTORY` | `ACTION_BY` | Set to the replacement value, for rows where `ACTION_BY` matches |
| `DPDP_CONSENT_HISTORY` | `SNAPSHOT` (JSON) | Every occurrence of the source value inside the JSON text is replaced — in practice this is the `piiPrincipalId` field and any `userId` field inside `authorizations[]` |
| `EVENT` | `PAYLOAD` (JSON) | Same substring replacement, but **only** for events whose topic (via a join to `TOPIC`) is `user.data.change` or `user.account.delete` — every other topic's payload is left completely untouched |

Every one of these is additionally scoped to the tenant you pass with `--tenant-domain` (`ORG_ID`
in each table) — rows for the same person in a different tenant are never touched.

Nothing outside this list is read or written: no other tables, and no other columns on these five
tables (e.g. `COMPLAINT.DESCRIPTION`, timestamps, status fields, etc. are left exactly as they
were).

## What it does not do

- It does not delete anything. Row counts, timestamps, and the shape of the audit trail all stay
  exactly as they were — only the identifying value changes.
- It does not touch attachments, free-text complaint descriptions/comments, or any data outside
  the tables listed above.
- It matches on the exact value stored in the database's identity columns — which throughout this
  accelerator's schema is the person's **username** (their email address), not a separate internal
  ID. If the same person is somehow recorded under two different values anywhere (e.g. an old
  username from before a rename), only the one you provide gets replaced.
- One field is a known exception worth double-checking after you run this: event records for
  `user.data.change`/`user.account.delete` are populated by code that prefers an internal identity
  identifier over the username, only falling back to it in some cases. If those event rows still
  show something other than your replacement value afterward, that's why — re-run with that other
  value once you know what it is.
- It only supports **MySQL** today (this is what production runs). There is no H2 (test/local
  database) support.

## Before you run this

This changes production data. Treat it like any other production database change:

1. **Stop WSO2 Identity Server** and anything else that writes to the DPDP database, for the
   duration of the run.
2. **Take a database backup** and confirm you can restore it.
3. Run the tool **without** `--execute` first and read the report.
4. Only re-run **with** `--execute` once you've confirmed the numbers look right.

## Requirements

- `bash` and the `mysql` command-line client available on the machine you run this from.
- Network access to the MySQL server hosting the DPDP database.
- A database user with `SELECT` and `UPDATE` privileges on the tables listed above.

## Configuration

Connection details are passed as environment variables — nothing is written to a config file, and
the password never appears in the command you type or in the process list.

| Variable | Required? | Default | Description |
|---|---|---|---|
| `DPDP_DB_USER` | Yes | — | Database username |
| `DPDP_DB_PASSWORD` | Yes | — | Database password |
| `DPDP_DB_HOST` | No | `localhost` | Database host |
| `DPDP_DB_PORT` | No | `3306` | Database port |
| `DPDP_DB_NAME` | No | `WSO2DPDP_DB` | Database name |

## How to run it

You need three things: the **tenant domain**, the **user's username** (their email address, exactly
as it's stored in the database), and a **replacement value**. The replacement doesn't need to look
like anything in particular — a fresh, random UUID (e.g. from `uuidgen`) is a good choice precisely
because it's guaranteed not to collide with a real person's username.

**Step 1 — dry run** (default; makes no changes):

```bash
export DPDP_DB_USER=<db-username>
export DPDP_DB_PASSWORD=<db-password>
export DPDP_DB_HOST=<db-host>        # optional, defaults to localhost

./anonymize-dpdp-user.sh \
  --tenant-domain example.com \
  --user-id alice@example.com \
  --pseudonym 216d6aac-7e84-4484-a71e-c52f89b3cb1d
```

This prints how many records reference that user and then stops. Review the number — if it's
`0`, there's nothing to do for that tenant.

**Step 2 — apply the change**, once you're satisfied with the dry run:

```bash
./anonymize-dpdp-user.sh \
  --tenant-domain example.com \
  --user-id alice@example.com \
  --pseudonym 216d6aac-7e84-4484-a71e-c52f89b3cb1d \
  --execute
```

All changes happen together as one database transaction: either every affected record is updated,
or — if anything goes wrong partway through — none of them are. The script re-checks its own work
afterward and will warn you if anything unexpected is left behind.

## Safety checks built in

- **Refuses to run** if the replacement ID is already used by someone else in that tenant, so you
  can't accidentally merge two people's histories together.
- **Validates** that both values only contain safe characters (letters, digits, and
  `. _ % + @ -`) and are different from each other, before either is used to build any database
  query.
- **Re-verifies after applying changes** that no trace of the original ID is left, and clearly
  reports if something looks wrong.
- Safe to run more than once — if the user's already been anonymized, it reports "nothing to do"
  rather than making any further changes.

## Troubleshooting

| Message | What it means |
|---|---|
| `Rows currently referencing the source identifier: 0` | Nothing found for this user/tenant — either already done, or the ID/tenant is wrong. |
| `The pseudonym is already present...` | The replacement ID you chose is already in use — generate a new one and try again. |
| `contains unexpected characters` | The ID or tenant has a character outside the safe set (letters, digits, `. _ % + @ -`). Double-check for typos or stray punctuation. |
| `row(s) still reference the source identifier after commit` | Most likely the event-payload exception noted above (an event row holding something other than the username). The change that *did* apply elsewhere is already committed; this needs manual follow-up — check with whoever manages the database. |

## Support

Tested end-to-end against MySQL 8. If you hit an issue, capture the exact command you ran
(without the password) and the full output, and reach out to the accelerator team.

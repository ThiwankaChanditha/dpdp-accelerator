# DPDP Accelerator quickstart

Use this guide for a local evaluation: install the accelerator with its default
embedded H2 databases, open the Consent Portal, and verify initial access.
Use the [Setup Guide](setup-guide.md) for external databases and the
[Configuration Guide](configuration-guide.md) for roles and runtime settings.

`configure.sh`, used below, is for evaluation and development: it replaces
`deployment.toml` and sets up the databases for you. For a production deployment,
run `merge.sh` as below, then follow the [Setup Guide](setup-guide.md) in place
of `configure.sh`.

## Prerequisites

- WSO2 Identity Server 7.3.0 at U2 update level 17 or later
- JDK 21 or later
- A released `wso2-dpdpiam-accelerator-<version>.zip`, or a ZIP built from the
  repository with `mvn clean install`

The extracted Identity Server directory is referred to as `<IS_HOME>` below.

## 1. Install the accelerator

Extract the accelerator ZIP, enter its directory, and run the two installation
scripts while Identity Server is stopped:

```sh
sh bin/merge.sh <IS_HOME>
sh bin/configure.sh <IS_HOME>
```

`configure.sh` backs up and then replaces
`<IS_HOME>/repository/conf/deployment.toml`; review that backup before using the
same process on an existing deployment.

Expected result: both scripts finish successfully and the accelerator
configuration is applied.

For source-build prerequisites, see the [repository README](https://github.com/wso2/dpdp-accelerator#build).
For automated MySQL and manual external database setup, see the
[Setup Guide](setup-guide.md).

### Set the local administrator password

For a fresh local installation, configure
`[super_admin]` in `<IS_HOME>/repository/conf/deployment.toml` before the first
server start, replacing the password placeholder with a unique password:

```toml
[super_admin]
username = "admin@wso2.com"
password = "<unique-local-administrator-password>"
create_admin_account = true
```

Replace the existing `[super_admin]` values instead of adding a duplicate table.
If the administrator already exists, use its current credentials
and change its password through Identity Server; editing the bootstrap setting
does not reset an existing account. Rotate evaluation credentials before any
production use.

## 2. Start Identity Server

```sh
sh <IS_HOME>/bin/wso2server.sh
```

After WSO2 Identity Server starts, open the Console:

```text
https://localhost:9443/console
```

## 3. Sign in to the Console

Sign in with the administrator account and password configured in step 1.

## 4. Create users and assign portal access

Open **User Management → Users** and create three users for portal access.
Assign one of the three provisioned roles to each user:

- `dpdp-consent-admin` for portal administrators, including the user who will
  verify the portal in this quickstart
- `dpdp-consent-user` for regular users who need personal consent history,
  complaint, or account-deletion features
- `dpdp-consent-dpo` for Data Protection Officers and complaint-handling users

The roles are created automatically, but users and role memberships are not.
After assigning a role, have each user sign out and sign in again so the new
access token contains the role's scopes.

See the [Role Management Guide](role-guide.md) before assigning roles. Basic
self-service consent management does not require a portal role.

## 5. Open the Portal

Open:

```text
https://localhost:9443/consent-portal/
```

Sign in as the user holding `dpdp-consent-admin` and confirm that the portal
loads.

## Next steps

- [Learn through real stories](learn.md) — understand how the major areas fit
  together from the perspectives of a Data Principal, administrator, processor,
  and grievance officer
- [Tryout Flows](tryout-flows.md) — catalog, consent lifecycle, complaint,
  automatic event, and account-deletion walkthroughs

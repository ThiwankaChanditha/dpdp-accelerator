# Setting up the DPDP Accelerator

Use this guide to set up the DPDP Accelerator on WSO2 Identity Server for a
production deployment:

- install the accelerator's artifacts;
- prepare the databases;
- configure `deployment.toml`;
- protect the credentials;
- start and verify the server.

Then continue with the [Configuration Guide](configuration-guide.md).

`<IS_HOME>` is the Identity Server directory, and `<ACCELERATOR_HOME>` is the
extracted accelerator ZIP.

## Prerequisites

- WSO2 Identity Server 7.3.0 at U2 update level 17 or later. Apply the U2 updates
  *before* installing the accelerator, because the consent v2 migration used in
  step 5 ships with them.
- JDK 21 or later.
- A MySQL 8.0 or PostgreSQL server. The supported PostgreSQL versions are 15, 16
  and 17, the versions Identity Server 7.3.0 is tested on. The embedded H2
  databases are for evaluation, development and testing only.
- A database administrator account, and the database's command-line client
  (`mysql` or `psql`).

Stop the Identity Server. Back up `<IS_HOME>/repository/conf/deployment.toml`,
and back up the databases too if this is an upgrade.

## Choose automated or manual setup

The accelerator ships two scripts:

- **`bin/merge.sh`** installs the accelerator's artifacts
  ([step 1](#1-install-the-accelerator-artifacts)). It changes no configuration,
  so use it in every environment, production included.
- **`bin/configure.sh`** automates steps 2 to 5 for evaluation and development.

The supported databases are H2, MySQL and PostgreSQL, and the installer has an
`h2`, `mysql` and `postgresql` profile for each in
`repository/conf/dbprofiles.properties`. For automated MySQL or PostgreSQL setup,
edit `repository/conf/configure.properties` before running `bin/configure.sh`:
set `DB_TYPE=mysql` or `DB_TYPE=postgresql`, `DB_HOST`, `DB_PORT` if needed,
`DB_USER`, and `DB_PASS`. Install the matching command-line client (`mysql` or
`psql`) and give the configured account permission to create the databases on the
first run.

The script downloads the configured JDBC driver into
`<IS_HOME>/repository/components/lib`, configures the datasource URLs, creates
missing databases, and applies the Identity Server schemas to databases it
creates. It applies the consent migration to a newly created identity database
when `APPLY_IS_CONSENT_MGT_V2_MIGRATION=true`, and the DPDP schemas when
`APPLY_DPDP_DB_MIGRATION=true`. Keep `RECREATE_DATABASES=false` to preserve
existing databases; setting it to `true` drops and recreates all four. See the
[Quickstart](quickstart.md) for the commands.

**Don't use `configure.sh` in production.** It replaces `deployment.toml`
wholesale, and creates and migrates the databases with whatever account it is
given. For production, run `merge.sh` and then follow steps 2 to 7 yourself, so
you decide what goes into `deployment.toml`, which account owns the databases, and
when each schema change is applied. Apply each schema change only once.

## 1. Install the accelerator artifacts

Run `merge.sh` from `<ACCELERATOR_HOME>`, with the Identity Server stopped:

```sh
sh bin/merge.sh <IS_HOME>
```

It first removes any previous accelerator version:
- the four webapps below, whether deployed as directories or `.war` files;
- every `org.wso2.dpdp.accelerator.*` bundle in `dropins`.

It then copies `<ACCELERATOR_HOME>/carbon-home/` over `<IS_HOME>`. The removal matters on an
upgrade, because copying only adds or overwrites files, so an old bundle or webapp left behind
would be loaded alongside the new one.

`carbon-home/` mirrors the Identity Server's layout:

| Path under `carbon-home/` | Contents |
| --- | --- |
| `repository/components/dropins/` | The accelerator's OSGi bundles (`org.wso2.dpdp.accelerator.*.jar`) |
| `repository/deployment/server/webapps/` | The Consent Portal and three API webapps: `consent-portal`, `api#dpdp#complaints#v1`, `api#dpdp#consent-mgt#v1`, `api#dpdp#event-notifications#v1` |
| `repository/resources/conf/templates/repository/conf/dpdp-accelerator.xml.j2` | The template the server renders the accelerator's own configuration from |
| `repository/conf/email/email-dpdp-config.xml` | Email templates for complaint notifications |
| `dbscripts/dpdp-accelerator/` | The accelerator's database scripts, used in step 5 |

## 2. Create the databases

Create four databases:

| Database | Used by |
|---|---|
| `WSO2IDENTITY_DB` | Identity Server identity and consent data |
| `WSO2SHARED_DB` | Identity Server shared data |
| `WSO2AGENTIDENTITY_DB` | the Identity Server `AgentIdentity` datasource |
| `WSO2DPDP_DB` | DPDP Accelerator data |

Create a separate account for the Identity Server to connect with, and use the
administrator account only to create the databases.

<details>
<summary>MySQL</summary>

Keep the three Identity Server databases on `latin1`. The shipped Identity Server
scripts mix tables that are explicitly `latin1` with tables that inherit the
database's character set, including in foreign keys, and MySQL rejects those keys
if the character sets differ. `WSO2DPDP_DB` holds the portal's multilingual data,
so it uses `utf8mb4`.

```sql
CREATE DATABASE WSO2IDENTITY_DB CHARACTER SET latin1 COLLATE latin1_swedish_ci;
CREATE DATABASE WSO2SHARED_DB CHARACTER SET latin1 COLLATE latin1_swedish_ci;
CREATE DATABASE WSO2AGENTIDENTITY_DB CHARACTER SET latin1 COLLATE latin1_swedish_ci;
CREATE DATABASE WSO2DPDP_DB CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE USER '<database-user>'@'<identity-server-host>' IDENTIFIED BY '<database-password>';
GRANT ALL PRIVILEGES ON WSO2IDENTITY_DB.* TO '<database-user>'@'<identity-server-host>';
GRANT ALL PRIVILEGES ON WSO2SHARED_DB.* TO '<database-user>'@'<identity-server-host>';
GRANT ALL PRIVILEGES ON WSO2AGENTIDENTITY_DB.* TO '<database-user>'@'<identity-server-host>';
GRANT ALL PRIVILEGES ON WSO2DPDP_DB.* TO '<database-user>'@'<identity-server-host>';
FLUSH PRIVILEGES;
```

</details>

<details>
<summary>PostgreSQL</summary>

Quote the database names. Without quotes, PostgreSQL folds them to lowercase, and
the JDBC URLs in step 4, which name `WSO2IDENTITY_DB` and so on, then fail to
connect. If you prefer lowercase names, use them consistently everywhere.

Making the Identity Server's account the owner of each database also gives it the
right to create tables. That matters from PostgreSQL 15, where the `public` schema
no longer lets every user create objects.

```sql
CREATE USER "<database-user>" WITH PASSWORD '<database-password>';

CREATE DATABASE "WSO2IDENTITY_DB" OWNER "<database-user>" ENCODING 'UTF8' TEMPLATE template0;
CREATE DATABASE "WSO2SHARED_DB" OWNER "<database-user>" ENCODING 'UTF8' TEMPLATE template0;
CREATE DATABASE "WSO2AGENTIDENTITY_DB" OWNER "<database-user>" ENCODING 'UTF8' TEMPLATE template0;
CREATE DATABASE "WSO2DPDP_DB" OWNER "<database-user>" ENCODING 'UTF8' TEMPLATE template0;
```

Run the step 5 scripts as `<database-user>`, so that it also owns the tables.

</details>

## 3. Install the JDBC driver

Copy the JDBC driver JAR for your database into
`<IS_HOME>/repository/components/lib` before starting the server. The `dropins`
directory is for OSGi bundles, not for drivers.

| DBMS | Driver the installer uses |
|---|---|
| MySQL 8.0 | [`mysql-connector-j-9.2.0.jar`](https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/9.2.0/mysql-connector-j-9.2.0.jar) |
| PostgreSQL 15, 16 or 17 | [`postgresql-42.7.13.jar`](https://repo1.maven.org/maven2/org/postgresql/postgresql/42.7.13/postgresql-42.7.13.jar) |

## 4. Configure `deployment.toml`

The accelerator ships a complete, commented `deployment.toml` for Identity Server 7.3.0:

```text
<ACCELERATOR_HOME>/repository/resources/wso2is-7.3.0-deployment.toml
```

- **Everything above its `# WSO2 DPDP Accelerator` banner** is the stock Identity Server file. The
  exceptions are the placeholders `configure.sh` fills in: the host name, the administrator
  account and the four datasource blocks.
- **Everything below the banner** is the accelerator's own configuration.

Use it as the reference while you edit your own `<IS_HOME>/repository/conf/deployment.toml`.

### 4.1 Server and administrator

Set these to the values for your environment:

```toml
[server]
hostname = "<public host name of the Identity Server>"

[super_admin]
username = "<administrator username>"
password = "$secret{admin_password}"
create_admin_account = true
```

With the accelerator's user-store settings (see `[user_store.properties]` below), usernames are email
addresses, so use one for the administrator too. Step 6 shows how to supply the password as an
encrypted secret.

### 4.2 Datasources

Configure these four sections:

| Database | `deployment.toml` section |
|---|---|
| `WSO2IDENTITY_DB` | `[database.identity_db]` |
| `WSO2SHARED_DB` | `[database.shared_db]` |
| `WSO2AGENTIDENTITY_DB` | `[datasource.AgentIdentity]` |
| `WSO2DPDP_DB` | `[datasource.WSO2DPDP_DB]` |

Keep the section names and the datasource IDs `AgentIdentity` and `WSO2DPDP_DB`.
`[dpdp_accelerator.jdbc_persistence_manager]` uses
`data_source_name = "jdbc/WSO2DPDP_DB"`: `jdbc/` is the JNDI prefix and
`WSO2DPDP_DB` the datasource ID.

**Write `&` in a JDBC URL as `&amp;`.** For example, write
`?sslMode=VERIFY_IDENTITY&amp;connectTimeout=10000`. The Identity Server renders
these URLs into an XML file, where a bare `&` breaks parsing and every datasource
then fails to bind.

The examples below use the same drivers and pool settings as `configure.sh`, with
TLS and server-certificate verification added. For TLS, add the database server's certificate,
or the CA that issued it, to the Identity Server's truststore. For credentials,
see [step 6](#6-protect-the-credentials).

<details>
<summary>MySQL</summary>

```toml
[database.identity_db]
type = "mysql"
url = "jdbc:mysql://<database-host>:3306/WSO2IDENTITY_DB?sslMode=VERIFY_IDENTITY"
username = "<database-user>"
password = "<database-password>"
driver = "com.mysql.cj.jdbc.Driver"

[database.identity_db.pool_options]
validationQuery = "SELECT 1"
validationInterval = "30000"
testOnBorrow = true

[database.shared_db]
type = "mysql"
url = "jdbc:mysql://<database-host>:3306/WSO2SHARED_DB?sslMode=VERIFY_IDENTITY"
username = "<database-user>"
password = "<database-password>"
driver = "com.mysql.cj.jdbc.Driver"

[database.shared_db.pool_options]
validationQuery = "SELECT 1"
validationInterval = "30000"
testOnBorrow = true

[datasource.AgentIdentity]
id = "AgentIdentity"
url = "jdbc:mysql://<database-host>:3306/WSO2AGENTIDENTITY_DB?sslMode=VERIFY_IDENTITY"
username = "<database-user>"
password = "<database-password>"
driver = "com.mysql.cj.jdbc.Driver"
pool_options.validationQuery = "SELECT 1"
pool_options.validationInterval = "30000"
pool_options.testOnBorrow = true

[datasource.WSO2DPDP_DB]
id = "WSO2DPDP_DB"
url = "jdbc:mysql://<database-host>:3306/WSO2DPDP_DB?sslMode=VERIFY_IDENTITY"
username = "<database-user>"
password = "<database-password>"
driver = "com.mysql.cj.jdbc.Driver"
pool_options.validationQuery = "SELECT 1"
pool_options.validationInterval = "30000"
pool_options.testOnBorrow = true
```

</details>

<details>
<summary>PostgreSQL</summary>

The Identity Server calls this database type `postgre`, not `postgresql`.
`validationQuery` ends with `COMMIT`, the Identity Server's own value for
PostgreSQL. These pools hand out connections with autocommit off, and a bare
`SELECT 1` would leave each validated connection idle inside an open transaction.

```toml
[database.identity_db]
type = "postgre"
url = "jdbc:postgresql://<database-host>:5432/WSO2IDENTITY_DB?sslmode=verify-full"
username = "<database-user>"
password = "<database-password>"
driver = "org.postgresql.Driver"

[database.identity_db.pool_options]
validationQuery = "SELECT 1; COMMIT"
validationInterval = "30000"
testOnBorrow = true

[database.shared_db]
type = "postgre"
url = "jdbc:postgresql://<database-host>:5432/WSO2SHARED_DB?sslmode=verify-full"
username = "<database-user>"
password = "<database-password>"
driver = "org.postgresql.Driver"

[database.shared_db.pool_options]
validationQuery = "SELECT 1; COMMIT"
validationInterval = "30000"
testOnBorrow = true

[datasource.AgentIdentity]
id = "AgentIdentity"
url = "jdbc:postgresql://<database-host>:5432/WSO2AGENTIDENTITY_DB?sslmode=verify-full"
username = "<database-user>"
password = "<database-password>"
driver = "org.postgresql.Driver"
pool_options.validationQuery = "SELECT 1; COMMIT"
pool_options.validationInterval = "30000"
pool_options.testOnBorrow = true

[datasource.WSO2DPDP_DB]
id = "WSO2DPDP_DB"
url = "jdbc:postgresql://<database-host>:5432/WSO2DPDP_DB?sslmode=verify-full"
username = "<database-user>"
password = "<database-password>"
driver = "org.postgresql.Driver"
pool_options.validationQuery = "SELECT 1; COMMIT"
pool_options.validationInterval = "30000"
pool_options.testOnBorrow = true
```

</details>

### 4.3 Accelerator settings

Copy every table below the `# WSO2 DPDP Accelerator` banner of the shipped file into your
`deployment.toml`, then adjust the values you need.

**Watch for tables your file already has.** TOML does not allow the same table twice, and the
server fails to start if it finds one. If your file already has, for example, a
`[consent_mgt]`, `[tenant_mgt]` or `[user_store.properties]` table, add the accelerator's keys to
that table instead. Array tables written with double brackets, `[[event_handler]]` and
`[[resource.access_control]]`, are repeatable; add them as they are.

The first group configures the Identity Server itself, and the accelerator does not work
correctly without it:

| Table | What it does |
| --- | --- |
| `[[event_handler]]` (`dpdpUserLifecycleEventHandler`) | Subscribes the accelerator to the Identity Server's user-deletion and claim-update events, which feed Event Notifications |
| `[datasource.WSO2DPDP_DB]` | The accelerator's own database. Configured in step 4.2 |
| `[consent_mgt]` | `enable_v2_api = true` registers the consent management v2 APIs and their scopes that the portal uses. `revoke_active_consents_on_create = false` keeps a user's earlier consents when a new one is created |
| `[[resource.access_control]]`, 30 entries | Protect the accelerator's APIs with OAuth scopes, open the portal's own paths, and restrict self-service account deletion (`DELETE /scim2/Me`) to `account:self:delete` |
| `[tenant_context.rewrite]` | Makes the portal and the accelerator APIs reachable at tenant-qualified URLs (`/t/<tenant>/…`) |
| `[console.flows.scopes]` | Lets the Console's flows view read consent purposes |
| `[tenant_mgt]` and `[user_store.properties]` | Make usernames email addresses, the accelerator's default. Leave them out only if you deliberately allow other usernames |

The `[dpdp_accelerator.*]` tables configure the accelerator. Every setting in them has a default,
which applies when the setting or the whole table is left out. Copying them anyway keeps the
values visible and easy to change:

| Table | What it does |
| --- | --- |
| `[dpdp_accelerator.jdbc_persistence_manager]` | The datasource the accelerator uses. The default is `jdbc/WSO2DPDP_DB`; it must match the datasource `id` |
| `[dpdp_accelerator.consent_portal]` | Provisions the portal's application and roles in every tenant. `client_id` (default `DPDP_CONSENT_PORTAL`) must match the portal's `deployment.config.json` |
| `[dpdp_accelerator.consent_api_invoker]` | Provisions a machine-to-machine application for systems that call the consent APIs directly |
| `[dpdp_accelerator.complaints]` | Complaint deadlines, attachment limits and notification emails |
| `[dpdp_accelerator.event_notifications]` and its `.payload_signing`, `.lifecycle_events`, `.polling` and `.webhook` sub-tables | Event Notifications behaviour, delivery and security |
| `[dpdp_accelerator.consent_history]` | Consent status-audit and history capture |
| `[dpdp_accelerator.consent_expiry]` | The consent-expiry sweep schedule |

The [Configuration Guide](configuration-guide.md) explains the settings in each of these tables.

## 5. Create the database tables

Apply these scripts, in this order:

| Script | Database |
|---|---|
| `<IS_HOME>/dbscripts/<db>.sql` | `WSO2SHARED_DB` |
| `<IS_HOME>/dbscripts/identity/<db>.sql` | `WSO2IDENTITY_DB` |
| `<IS_HOME>/dbscripts/consent/<db>.sql` | `WSO2IDENTITY_DB` |
| `<IS_HOME>/dbscripts/migrations/consent/<db>-migration.txt` | `WSO2IDENTITY_DB` |
| `<IS_HOME>/dbscripts/identity/agent/<db>.sql` | `WSO2AGENTIDENTITY_DB` |
| `dbscripts/dpdp-accelerator/{complaint,consent-history,event-notification}/<db>.sql` | `WSO2DPDP_DB` |

`<db>` is `mysql` or `postgresql`.

**The consent migration is required.** It is the only source of the consent v2
tables (`CM_CONSENT_AUTHORIZATION`, `CM_PURPOSE_VERSION` and others): neither the
base scripts nor the embedded H2 database contain them. It ships with the U2
updates. Strip its `#` comment lines before running it, as shown below. On MySQL
it needs U2 update level 17 or later.

**The accelerator's scripts** are in `<ACCELERATOR_HOME>/carbon-home/dbscripts/`.
After [step 1](#1-install-the-accelerator-artifacts),
they are also in `<IS_HOME>/dbscripts/`.

**Apply the Identity Server's scripts once, to new, empty databases.** They are
not safe to re-run: some `CREATE TABLE` statements are unguarded, and the
PostgreSQL agent script starts by dropping its tables. The accelerator's own
scripts use `CREATE ... IF NOT EXISTS` throughout, so they can be re-run.

<details>
<summary>MySQL</summary>

```sh
IS=<IS_HOME>
DPDP=<ACCELERATOR_HOME>/carbon-home/dbscripts/dpdp-accelerator
MYSQL="mysql -h <database-host> -u <database-user> -p"

$MYSQL WSO2SHARED_DB        < "$IS/dbscripts/mysql.sql"
$MYSQL WSO2IDENTITY_DB      < "$IS/dbscripts/identity/mysql.sql"
$MYSQL WSO2IDENTITY_DB      < "$IS/dbscripts/consent/mysql.sql"
grep -v '^#' "$IS/dbscripts/migrations/consent/mysql-migration.txt" | $MYSQL WSO2IDENTITY_DB
$MYSQL WSO2AGENTIDENTITY_DB < "$IS/dbscripts/identity/agent/mysql.sql"
for feature in complaint consent-history event-notification; do
  $MYSQL WSO2DPDP_DB < "$DPDP/$feature/mysql.sql"
done
```

</details>

<details>
<summary>PostgreSQL</summary>

`ON_ERROR_STOP` makes `psql` stop at the first failing statement. Without it,
`psql` carries on and still exits successfully.

```sh
IS=<IS_HOME>
DPDP=<ACCELERATOR_HOME>/carbon-home/dbscripts/dpdp-accelerator
PSQL="psql -h <database-host> -U <database-user> -v ON_ERROR_STOP=1"

$PSQL -d WSO2SHARED_DB        -f "$IS/dbscripts/postgresql.sql"
$PSQL -d WSO2IDENTITY_DB      -f "$IS/dbscripts/identity/postgresql.sql"
$PSQL -d WSO2IDENTITY_DB      -f "$IS/dbscripts/consent/postgresql.sql"
grep -v '^#' "$IS/dbscripts/migrations/consent/postgresql-migration.txt" | $PSQL -d WSO2IDENTITY_DB
$PSQL -d WSO2AGENTIDENTITY_DB -f "$IS/dbscripts/identity/agent/postgresql.sql"
for feature in complaint consent-history event-notification; do
  $PSQL -d WSO2DPDP_DB -f "$DPDP/$feature/postgresql.sql"
done
```

</details>

## 6. Protect the credentials

`deployment.toml` now holds the administrator password and the database passwords. Encrypt them
with the Identity Server's Cipher Tool instead of storing them in plain text:

1. **Add a `[secrets]` table** to `deployment.toml`, giving each password an alias and its value in
   square brackets:

   ```toml
   [secrets]
   admin_password = "[<administrator password>]"
   db_password = "[<database password>]"
   ```

2. **Refer to each secret by its alias** wherever the password is used, for example
   `password = "$secret{db_password}"` in each datasource.
3. **Run the Cipher Tool** from `<IS_HOME>/bin` (`./ciphertool.sh -Dconfigure`, with `-Dsymmetric`
   for symmetric encryption). It replaces the plain values with encrypted ones.

See the Identity Server's
[Encrypt passwords with Cipher Tool](https://is.docs.wso2.com/en/7.3.0/deploy/security/encrypt-passwords-with-cipher-tool/)
for the details, including how the server gets the keystore password at startup.

## 7. Start and verify

Start the Identity Server:

```sh
sh <IS_HOME>/bin/wso2server.sh
```

On Windows, run `bin\wso2server.bat` instead.

Then check that:

1. **The server starts with no datasource or `deployment.toml` errors** in
   `<IS_HOME>/repository/logs/wso2carbon.log`.
2. **The accelerator's bundles are active.** The log shows `Event Notification services are
   activated successfully.`
3. **The portal is provisioned.** The log shows `Provisioned the DPDP Consent Portal for tenant:
   carbon.super`, and the Console lists the `DPDP Consent Portal` application and the
   `dpdp-consent-admin`, `dpdp-consent-user` and `dpdp-consent-dpo` roles.
4. **The portal opens** at `https://<host>:9443/consent-portal`.

Then continue with the [Configuration Guide](configuration-guide.md) to assign roles and set up
the optional features.

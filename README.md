# LogiOpt: Smart Shipment Planning and Optimization (Review 3)

LogiOpt is written entirely in Java 17 (MySQL 8, Maven). It has a JavaFX desktop client as the main front end and a web application (Jakarta Servlet 6 + JSP/JSTL on Tomcat 10.1) as the second front end; both use the same engine, services, DAOs and migrations. Customers register, save shipments and compare the seeded road, rail and multimodal routes for each one on cost, duration with arrival date, emissions and reliability. One of four strategies (Cheapest, Fastest, Greenest, Balanced) recommends a route and explains the choice. Customers can book a route with a simulated payment, follow tracking milestones, get in-app notifications, see a dashboard and history, and download TXT reports. A single administrator records tracking and sees system-wide counts. `docs/REVIEW3.md` describes the Review 3 features, state machines, security controls and test evidence.

**Project layout (Maven multi-module).** The root `pom.xml` is the parent `logiopt-parent`:
- `logiopt-core` (jar): model, planning engine and strategies, services, DAOs, `MigrationRunner`/`MigrationTool`, the report service, the migrations in `db/migration` and `db.properties`, plus their tests. It also publishes the test helpers `TestData` and `TestDatabase` as a test-jar.
- `logiopt-web` (war): Servlets, filters, JSPs and their tests. It builds `logiopt-web\target\logiopt.war`, with `logiopt-core-1.0-SNAPSHOT.jar` in `WEB-INF/lib`.
- `logiopt-desktop` (jar): the JavaFX 17 desktop client and its tests. `package` copies JavaFX to `logiopt-desktop\target\lib\javafx` and the other libraries (including the core jar) to `logiopt-desktop\target\lib`. Nothing from JavaFX enters the WAR.
- Run Maven from the project folder, where the parent `pom.xml` is.

**Two front ends.** The shared `com.logiopt.app.CoreRuntime` (in `logiopt-core`) builds the services from the same environment variables for both front ends. It also runs the read-only schema check, seeds the administrator from `LOGIOPT_ADMIN_PASSWORD`, starts the optional tracking simulator and shuts everything down. Shipment-form input is parsed by the shared `ShipmentFormInput`, so both front ends validate it the same way. Neither front end migrates the database.

| | Desktop client (main) | Web application (second front end) |
|---|---|---|
| Start | `java ... com.logiopt.desktop.Launcher` (section 4.1) | deploy `logiopt-web\target\logiopt.war` on Tomcat (section 4) |
| Schema behind, failed or unreachable | modal dialog with the database and required versions, Retry or Exit; the MigrationTool command is only in the log | HTTP 503 page; the command is only in the server log |
| Screens | sign in, register, dashboard, new and edit shipment (with optimisation preference), my shipments, route comparison, review your order, booking detail with tracking timeline, simulated payment, my bookings, notifications (badge refreshed every 15 s), reports (generate, status, save a copy), history, account; administrator: dashboard, users, all bookings (read-only), tracking (record next milestone, Advance) | the same features; it ignores the optimisation preference |

Use **one front end at a time per database**, with the same `LOGIOPT_REPORTS_DIR` for both. At start-up each front end records report jobs it is not running itself as FAILED, and both write report files into that directory; the desktop "Save a copy…" copies a finished file elsewhere and leaves the original in place.

> Estimates based on configured sample rates and factors; not live carrier data. Payments are simulated; no money moves.

## Verified versions
These are the versions the build and tests were run with (2026-10-26):

| Component | Version |
|---|---|
| JDK | Temurin 17.0.20.1 (`D:\tools\jdk-17.0.20.1+1`) |
| Maven | 3.10.0, local repository `D:\tools\m2repo`, run offline (`-o`) |
| Tomcat | 10.1.61 (installed at `D:\tools\tomcat`; the same version is embedded, test scope only, to render the JSPs in tests) |
| MySQL server | reports `26.10.0` (`D:\tools\mysql`); the migrations need 8.0.19 or newer and check `SELECT VERSION()` |
| MySQL Connector/J | 8.4.0 |
| Jakarta Servlet / JSP / JSTL | 6.0 / 3.1 / 3.0 (GlassFish JSTL 3.0.1) |
| JUnit | 5.10.2 |
| JaCoCo | 0.8.12 (coverage report only) |

## Prerequisites
- JDK 17
- Maven 3.9+
- MySQL 8.0.19+ with the `mysql` and `mysqldump` command-line clients
- Apache Tomcat 10.1 (Jakarta namespace; Tomcat 9 will not work)
- A MySQL account that can create tables in the application database and in a disposable `_test` database

## 1. Create the databases and apply migrations
The schema is managed by versioned migrations in `logiopt-core/src/main/resources/db/migration`, applied by the Java command `com.logiopt.dao.MigrationTool` (JDBC, no external tool) and recorded in the `schema_migrations` table. The web application never migrates on its own.

| Version | Script | What it does |
|---|---|---|
| V1 | `V1__baseline.sql` | The nine Review 2 tables: users, shipments, routes, route_legs, bookings, tracking, payments, notifications, reports (later versions extend them) |
| V2 | `V2__route_legs_unique_key.sql` | Adds `uk_route_legs_seq (route_id, sequence_no)` if it is missing |
| V3 | `V3__seed_reference_data.sql` | Upserts demo user 1 (disabled, no login) and the 10 sample routes with their legs. Nothing is deleted, so it is safe once bookings exist |
| V4 | `V4__users_auth.sql` | Adds per-user password salt, iteration count and algorithm, and a generated `admin_marker` column with a UNIQUE key so at most one ADMIN can exist |
| V5 | `V5__bookings_payments.sql` | Adds the booking snapshot columns and a `booking_legs` table, a generated `active_shipment_id` with a UNIQUE key (one active booking per shipment), status CHECKs, and payment attempt numbers. Generated columns with UNIQUE keys allow at most one PENDING and one PAID attempt per booking. An existing booking row with a Review 2 status such as `BOOKED` makes V5 stop instead of reinterpreting it |
| V6 | `V6__tracking_notifications.sql` | Tracking events get shipment reference, description, source (SYSTEM/ADMIN/SIMULATED), recording admin, milestone CHECK and a UNIQUE key on (booking, milestone). Notifications get type, booking, event key, `read_at` and a UNIQUE dedupe key on (recipient, type, booking, event) |
| V7 | `V7__report_jobs.sql` | Report jobs are persisted: public `job_id` (UNIQUE), owner `user_id`, status CHECK (QUEUED/RUNNING/DONE/FAILED), `error_message`, `started_at`/`finished_at`, and the managed `file_name` (UNIQUE, set only when DONE). CHECKs keep file name, error and finish time consistent with the status |
| V8 | `V8__routes_natural_key.sql` | Adds `uk_routes_pair_name (source, destination, name)` if it is missing, so no corridor can hold two routes with the same name |
| V9 | `V9__seed_all_city_pairs.sql` | Illustrative sample data: every ordered pair of the six cities that has no route yet gets *Road direct*, *Rail direct* and *Road-Rail-Road* (27 corridors, 81 routes, 135 legs), generated from one table of road distances. Inserts only; routes 1-10 and their legs are never touched |
| V10 | `V10__shipment_optimisation_preference.sql` | Adds the nullable `shipments.optimisation_preference` (binary collation) with a CHECK on CHEAPEST, FASTEST, GREENEST, BALANCED. Existing rows keep NULL, read as BALANCED; the web form never changes it |

**At startup the application checks `schema_migrations` (read-only).** If the database is behind the version the build needs, or the history shows a failed migration, every page answers HTTP 503 with a generic "temporarily unavailable" message. The page shows no database name, version or command. The server log (Tomcat `catalina` log) contains the exact MigrationTool command to run. The check repeats at most every 10 seconds, so the application starts serving as soon as the migration is applied, without a restart.

**Create the databases** (once, in the `mysql` client as an account allowed to create databases):
```sql
CREATE DATABASE logiopt      CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE logiopt_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;  -- disposable, for the tests
```
**Apply the migrations to a new database.** Build once (section 3) so that `logiopt-web\target\logiopt` exists. Then, from the project folder, always look at the status first:
```powershell
$cp = "logiopt-web\target\logiopt\WEB-INF\classes;logiopt-web\target\logiopt\WEB-INF\lib\*"
java -cp $cp com.logiopt.dao.MigrationTool --target=main --confirm-database=logiopt --status   # 1. read-only
java -cp $cp com.logiopt.dao.MigrationTool --target=main --confirm-database=logiopt            # 2. apply
java -cp $cp com.logiopt.dao.MigrationTool --target=main --confirm-database=logiopt --status   # 3. check: V1 to V10
```
`--target=main` uses `LOGIOPT_DB_URL` and runs only if `--confirm-database` repeats the database name in that URL. `--target=test` uses `LOGIOPT_TEST_DB_URL` and refuses any database whose name does not end in `_test` (for example `java -cp $cp com.logiopt.dao.MigrationTool --target=test --status`). The tool prints only the database name, never the URL or credentials.

How the runner treats an existing database without history:
- None of the V1 tables exist: V1 runs (fresh install).
- All nine exist with exactly the V1 structure: V1 is recorded as `BASELINE` without running. It compares column types, nullability, auto_increment, primary, unique and foreign keys (including ON DELETE CASCADE) and CHECK constraint names. A missing `uk_route_legs_seq` is allowed because V2 adds it.
- Anything else (some tables missing, a different column type, a missing key): it stops, lists the differences and changes nothing.

MySQL DDL is not transactional. If a script fails part-way, its row stays in `schema_migrations` with `success = FALSE` and the error, and every later run refuses until you repair the schema by hand and delete that row. Editing an applied script (checksum change) or a recorded version unknown to the build also stops the run. A named lock keeps two runs apart.

The original Review 2 `schema.sql` and `seed.sql` are in git history (commit 912e426) and are superseded by migrations V1 to V10. Do not run them.

**Route data (illustrative sample data, not real carrier tariffs or distances).** Every ordered pair of the six cities (Ahmedabad, Bengaluru, Chennai, Delhi, Kolkata, Mumbai) has at least three routes, 91 routes and 151 legs in total:
- V3 (unchanged since Review 2): Mumbai → Delhi (routes 1-4), Chennai → Kolkata (5-7; rail route 6 does not accept HAZARDOUS cargo) and Ahmedabad → Bengaluru (8-10; rail route 9 has no emission factor).
- V9: the other 27 ordered pairs, including the reverse directions of the three V3 corridors. Each gets three routes with fixed ids `1000 + 100 × source code + 10 × destination code + kind`. The city codes are Ahmedabad 1, Bengaluru 2, Chennai 3, Delhi 4, Kolkata 5, Mumbai 6, and the kinds are 1 Road direct, 2 Rail direct, 3 Road-Rail-Road. For example, Delhi → Mumbai Rail direct is route 1462.
  - Road direct: one road leg of the road distance.
  - Rail direct: one rail leg of `ROUND(road km × 1.05)`, with halves rounded up; it refuses HAZARDOUS cargo.
  - Road-Rail-Road: 100 km road, then rail of (rail distance − 200 km), then 100 km road.
  - Rates, speeds, buffers, emission factors, reliability, capacity and charges are those of V3 routes 1-3 (Mumbai → Delhi). Road distances are the same in both directions. The table is in the V9 script header, and `RouteNetworkIntegrationTest` checks every generated value against it.
  - V3's own corridors keep their own distances: Mumbai → Delhi rail is 1450 km, Chennai → Kolkata rail 1660 km, and Ahmedabad → Bengaluru road 1500 km and rail 1550 km. Their reverse directions use the table (rail 1470, 1754 and 1565 km, road 1490 km), so the two directions of those three pairs differ.

### 1.1 Migrating the real `logiopt` database (manual, do this yourself)
> **This changes your real data.** None of the Review 3 phases ran a migration against `logiopt`; it has only been tested on `logiopt_test`, including an upgrade from a restored copy of the Review 2 backup. Stop Tomcat (or undeploy LogiOpt) before you start, so that nothing writes to the database during the upgrade.

1. **Back up first.** Choose a folder outside the project, then run (it asks for the password; do not put it on the command line):
   ```powershell
   D:\tools\mysql\bin\mysqldump.exe -u <user> -p --single-transaction --routines --triggers --set-gtid-purged=OFF --databases logiopt --result-file=D:\backups\logiopt_before_review3.sql
   ```
   Check that the file exists and ends with `-- Dump completed`.
2. **Status (read-only):** `java -cp $cp com.logiopt.dao.MigrationTool --target=main --confirm-database=logiopt --status`. A Review 2 database shows no history yet.
3. **Apply:** `java -cp $cp com.logiopt.dao.MigrationTool --target=main --confirm-database=logiopt`. On a Review 2 database V1 is recorded as `BASELINE` and V2 to V10 run. If the structure differs from the Review 2 baseline, the tool stops before changing anything and lists the differences.
4. **Verify:** run `--status` again; it must list V1 to V10.
5. **If a migration failed part-way:** do not start the application. Restore the backup into an empty `logiopt` database (in `cmd.exe`: `D:\tools\mysql\bin\mysql.exe -u <user> -p logiopt < D:\backups\logiopt_before_review3.sql`) and report the error printed by the tool.
6. Set `LOGIOPT_ADMIN_PASSWORD` (section 2) and start Tomcat (section 4). Shipments created before Review 3 belong to the disabled demo user 1 and are not visible to anyone who signs in.

## 2. Configuration
| Setting | Where | Default |
|---|---|---|
| JDBC URL | env `LOGIOPT_DB_URL`, else `db.url` in `logiopt-core/src/main/resources/db.properties` | `jdbc:mysql://localhost:3306/logiopt` |
| DB user | env `LOGIOPT_DB_USER`, else `db.user` | `logiopt` |
| DB password | env `LOGIOPT_DB_PASSWORD`, else `db.password` | empty; never commit a real password |
| Reports directory | system property `logiopt.reports.dir`, else env `LOGIOPT_REPORTS_DIR` | `reports` under the working directory |
| Snapshots directory | system property `logiopt.snapshots.dir`, else env `LOGIOPT_SNAPSHOTS_DIR` | `snapshots` under the working directory |
| Administrator password | env `LOGIOPT_ADMIN_PASSWORD` (8 to 128 characters). Never put it in a file | unset: no administrator is seeded and this is logged |
| Tracking simulator | env `LOGIOPT_SIM_INTERVAL_SECONDS`: positive whole number of seconds between automatic passes, each advancing every confirmed booking by one milestone | unset: off (logged). Invalid values: off (logged) |
| Test database URL | env `LOGIOPT_TEST_DB_URL` only (no fallback); name must end in `_test` | unset: database tests are skipped |
| Test DB user / password | env `LOGIOPT_TEST_DB_USER` / `LOGIOPT_TEST_DB_PASSWORD`, else `LOGIOPT_DB_USER` / `LOGIOPT_DB_PASSWORD` | |
| Upgrade-test dump | env `LOGIOPT_TEST_UPGRADE_DUMP`: a mysqldump of an existing Review 2 database, without `GTID_PURGED`/`SQL_LOG_BIN` lines | unset: upgrade-path tests are skipped |

Set the environment variables in the shell that starts Tomcat or Maven, for example `$env:LOGIOPT_DB_URL = "jdbc:mysql://localhost:3306/logiopt"`.

**Administrator provisioning.** The single administrator signs in with the email **`admin@logiopt.local`** and the password you put in the environment variable `LOGIOPT_ADMIN_PASSWORD` before starting Tomcat. Once the schema check passes, the application creates that account. The variable is the source of truth: if you change it, the stored hash is replaced at the next start. If the variable is unset, no administrator is created and the log says so. Nobody can register `admin@logiopt.local`. Newly registered users are always CUSTOMER, and the database (`uk_users_single_admin`) refuses a second ADMIN row.

## 3. Build and test
```powershell
$env:JAVA_HOME = "D:\tools\jdk-17.0.20.1+1"
$env:LOGIOPT_TEST_DB_URL = "jdbc:mysql://localhost:3306/logiopt_test"   # disposable; the name must end in _test
# LOGIOPT_DB_USER / LOGIOPT_DB_PASSWORD (or LOGIOPT_TEST_DB_USER / _PASSWORD) must be set for that database
mvn -o -B "-Dmaven.repo.local=D:\tools\m2repo" clean package          # all tests of the three modules + coverage reports + WAR + desktop client
mvn -o -B "-Dmaven.repo.local=D:\tools\m2repo" -DskipTests package    # WAR and desktop client, no tests
mvn -o -B "-Dmaven.repo.local=D:\tools\m2repo" -pl logiopt-web -am "-Dtest=JspRenderingIntegrationTest" "-Dsurefire.failIfNoSpecifiedTests=false" test   # one test class (use -pl logiopt-core for a core test)
```
- Each module writes its own coverage report: `logiopt-core\target\site\jacoco\index.html`, `logiopt-web\target\site\jacoco\index.html` and `logiopt-desktop\target\site\jacoco\index.html`. It has no thresholds and never fails the build.
- To run the upgrade-path tests too, set `LOGIOPT_TEST_UPGRADE_DUMP` to a filtered copy of a Review 2 dump (section 2).
- The database tests use only `LOGIOPT_TEST_DB_URL`. They drop and recreate every table in that database, so it must be disposable and its name must end in `_test`; any other name makes them fail instead of running, and without the variable they are skipped. They never read `LOGIOPT_DB_URL`. When running them in a shell that also has the real `LOGIOPT_DB_URL`, you can point that variable at a closed port for the run (for example `$env:LOGIOPT_DB_URL = "jdbc:mysql://127.0.0.1:1/no_db_for_tests"`), so nothing can reach the real database even by mistake.
  - Database test classes: `DaoIntegrationTest`, `MigrationIntegrationTest`, `DemoScriptIntegrationTest`, `AuthServiceIntegrationTest`, `BookingServiceIntegrationTest`, `TrackingNotificationIntegrationTest`, `ShipmentDraftEditIntegrationTest`, `DashboardHistoryIntegrationTest`, `ReportPersistenceIntegrationTest`, `RouteNetworkIntegrationTest`, `CoreRuntimeIntegrationTest`, `WebSecurityIntegrationTest`, `EndToEndJourneyIntegrationTest`, `JspRenderingIntegrationTest`, `DesktopPresentersIntegrationTest`.
- The desktop view smoke tests (`ViewSmokeTest`) need the JavaFX toolkit, which needs a display (a normal or remote Windows desktop session). Without one they are reported as **skipped**, not passed. The presenter tests need no display.
- The test-only failing migration is `logiopt-core/src/test/resources/db/test-migration/V999__intentionally_failing.sql`; its high version can never collide with a real migration, and it is not in the WAR.
- `JspRenderingIntegrationTest` renders every JSP with an embedded Tomcat 10.1.61 (test scope). It listens on a random port on 127.0.0.1 only while the test runs; it is not the installed Tomcat and deploys nothing. The embedded Tomcat, Jasper and JaCoCo are never packaged: `jar tf logiopt-web\target\logiopt.war` lists only `logiopt-core-1.0-SNAPSHOT.jar` and the JSTL, EL API, Connector/J and protobuf jars under `WEB-INF/lib`.

## 4. Deploy on Tomcat 10.1
1. Check that nothing else uses port 8080 (`Get-NetTCPConnection -LocalPort 8080 -State Listen`), or stop the running Tomcat first.
2. Migrate the database (section 1 or 1.1).
3. Stop Tomcat; delete `D:\tools\tomcat\webapps\logiopt.war`, `D:\tools\tomcat\webapps\logiopt\` and `D:\tools\tomcat\work\Catalina\localhost\logiopt\`; copy `logiopt-web\target\logiopt.war` to `D:\tools\tomcat\webapps\`; start Tomcat (step 4). The WAR entries carry a fixed 1980 timestamp, so without the clean delete Tomcat keeps serving the previously compiled JSPs.
4. In the same PowerShell window set `LOGIOPT_DB_URL`, `LOGIOPT_DB_USER`, `LOGIOPT_DB_PASSWORD` and `LOGIOPT_ADMIN_PASSWORD` (and optionally `LOGIOPT_REPORTS_DIR`, `LOGIOPT_SIM_INTERVAL_SECONDS`), then run `D:\tools\tomcat\bin\startup.bat`. Stop Tomcat with `shutdown.bat`.
5. Open `http://localhost:8080/logiopt/`. Without a session it redirects to `/login`; after sign-in you land on the dashboard.

### 4.1 Start the desktop client (JavaFX)
Build first (section 3). The client reads the same environment variables as the web application and never migrates. For manual runs use the demo database `logiopt_demo`, never `logiopt_test` (the tests empty it) and not `logiopt`. Never run the tests against `logiopt_demo`: they only use `LOGIOPT_TEST_DB_URL`, and they refuse a name that does not end in `_test`.

Create and migrate the demo database once (a MySQL account allowed to create databases; MigrationTool asks for nothing and prints no credentials):
```powershell
# in the mysql client: CREATE DATABASE IF NOT EXISTS logiopt_demo CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
$env:LOGIOPT_DB_URL = "jdbc:mysql://localhost:3306/logiopt_demo?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
java -cp "logiopt-desktop\target\lib\*" com.logiopt.dao.MigrationTool --target=main --confirm-database=logiopt_demo --status
java -cp "logiopt-desktop\target\lib\*" com.logiopt.dao.MigrationTool --target=main --confirm-database=logiopt_demo
```
Start the client against it, from the project folder:
```powershell
$env:JAVA_HOME = "D:\tools\jdk-17.0.20.1+1"
$env:LOGIOPT_DB_URL = "jdbc:mysql://localhost:3306/logiopt_demo?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
$env:LOGIOPT_DB_USER = "<your MySQL user>"
# The two passwords are typed in, not written down (Read-Host -AsSecureString shows nothing on screen):
$env:LOGIOPT_DB_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR([Runtime.InteropServices.Marshal]::SecureStringToBSTR((Read-Host "MySQL password" -AsSecureString)))
$env:LOGIOPT_ADMIN_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR([Runtime.InteropServices.Marshal]::SecureStringToBSTR((Read-Host "Administrator password (8+ characters)" -AsSecureString)))
$env:LOGIOPT_REPORTS_DIR = "D:\java project\Code\reports"      # optional; the same folder as the web application
# optional: $env:LOGIOPT_SIM_INTERVAL_SECONDS = "20"            # automatic tracking passes, as on the web
java --module-path logiopt-desktop\target\lib\javafx --add-modules javafx.controls -cp "logiopt-desktop\target\logiopt-desktop-1.0-SNAPSHOT.jar;logiopt-desktop\target\lib\*" com.logiopt.desktop.Launcher
```
- **Before any window opens**, the client checks `schema_migrations` read-only. If the database is behind, has a failed migration, or cannot be reached, a modal dialog shows the database version and the required version, with **Retry** and **Exit**. The exact MigrationTool command is written to the console log only.
- When the check passes, the administrator `admin@logiopt.local` is seeded from `LOGIOPT_ADMIN_PASSWORD`, exactly as the web application does. Customers register in the client.
- Window: about 90% of the screen's usable area (the screen minus the taskbar, after Windows display scaling), centred, never larger than that area, at least 960×600 (still fits 1920×1080 at 150%); resizable and maximizable. Page content sits in a centred column at most 1100 px wide and scrolls vertically inside the window. Enter submits the sign-in, registration and shipment forms; Esc cancels the shipment form. Close the window to exit. That shuts down the background threads and the shared runtime.
- Logs (including the MigrationTool command) go to the console window. On first start JavaFX unpacks its native libraries into `%USERPROFILE%\.openjfx\cache`.

**Screenshots.** `ScreenshotTool` renders every desktop screen built so far with sample data (no database) off-screen to PNG files:
```powershell
java --module-path logiopt-desktop\target\lib\javafx --add-modules javafx.controls -cp "logiopt-desktop\target\logiopt-desktop-1.0-SNAPSHOT.jar;logiopt-desktop\target\lib\*" com.logiopt.desktop.tools.ScreenshotTool D:\screenshots\logiopt [--scale=2] [--d4]
```
`--d4` renders only the phase D4 screens (timeline, notifications, reports, administrator) at this laptop's size (1280×672 usable) and the minimum window.
It shows no window but needs a display for the JavaFX toolkit. Without one it prints "ScreenshotTool needs a display …", writes nothing and exits with code 2.

## 5. Using the application
- **Dashboard** (`/dashboard`, the page you land on after signing in):
  - Customers see their own counts, read from the database: shipments by status, bookings by status, unread notifications, and the latest tracking milestone of each confirmed booking (the latest 20; a note says so when there are more).
  - The administrator is sent to `/admin/dashboard` (404 for everyone else): enabled users by role (the disabled demo user is not counted), bookings by status, and confirmed bookings awaiting tracking (not yet Delivered).
  - Empty counts show a short explanation; if the database is unavailable the page says so (HTTP 503).
- **History** (`/history`): your latest 25 shipments, bookings and reports, newest first, with a "Showing the latest 25" note on each list.
- **Edit a draft** (`/shipments/edit?shipmentId=`, *Edit draft* link in the shipment list and history):
  - Only your own shipments (404 otherwise) and only while they are DRAFT. The form and validation are the same as for creating one: known cities from the lists, weight bounds and decimals, dispatch date not in the past.
  - Booked, planned, cancelled and completed shipments are refused with an explanation (HTTP 409) and nothing changes. Booking snapshots are separate rows and an edit never touches them.
  - Cancelling an unpaid booking returns the shipment to its earlier status; if that was DRAFT, it can be edited again.
- **Register and sign in:**
  - `/register` asks for email, name and a password of at least 8 characters.
  - A valid form always ends on the sign-in page with the same notice, whether or not the email was already registered. Nothing tells a visitor which addresses have accounts; a duplicate creates nothing.
  - `/login` gives one message ("Email or password is incorrect.") for every failure.
  - After 5 failed attempts for the same email from the same IP address within 5 minutes, that pair is locked for 5 minutes (HTTP 429, generic message). The lock is in memory, per server.
  - Every page except `/login` and `/register` needs a signed-in user; otherwise you are sent to `/login`, with "session expired" if your session timed out.
  - *Sign out* (in the page header) ends the session.
- **Sessions and security:**
  - Every response carries `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: same-origin`, a `Content-Security-Policy` that allows no script, stylesheet or frame (`default-src 'none'; img-src 'self'; form-action 'self'; frame-ancestors 'none'; base-uri 'none'`) and `Cache-Control: no-store`, so after sign-out the back button shows no earlier page. The pages use no JavaScript and no CSS.
  - Errors show a friendly page without stack traces or internal details: 400, 401, 403, 404, 405, 429, 500 and 503 (and any uncaught exception).
  - Sessions are server-side, with a 30-minute idle timeout.
  - The session id changes at sign-in and the session is invalidated at sign-out.
  - The `JSESSIONID` cookie is `HttpOnly; SameSite=Lax`, gets `Secure` automatically over HTTPS, and is the only tracking mode (no `;jsessionid` in URLs).
  - Every POST form carries a per-session CSRF token in a hidden field; a missing or wrong token gives 403.
  - Your identity comes only from the session, never from request parameters.
  - Shipments, comparisons, reports and report jobs are visible only to their owner. Another user's resource gives the same 404 page as one that does not exist.
- **Administrator:** `/admin/users` lists the accounts (no password data). For everyone else it answers 404.
- **Create a shipment:** open `/shipments/new`, choose source and destination from the lists of cities served by a configured route, and fill in cargo type, weight (kg), optional dimensions and dispatch date. The server also rejects any city that no route serves. Weight must be 0.001 to 999,999,999.999 kg with at most 3 decimals (the `DECIMAL(12,3)` column); anything else is rejected with a message, never rounded. The dispatch date cannot be in the past. All validation errors are shown together and the values you entered are kept. A valid shipment is saved for the signed-in user and opens its comparison with the Balanced strategy. The form needs the database (for the city list); without it you get a friendly 503.
- **Saved shipments with a past dispatch date** can still be compared and reported. The past-date rule applies to new shipments and to booking.
- **Book a route:** the comparison page has a *Book* button for every eligible route, if the dispatch date is today or later and the shipment has no active booking.
  - The form sends only the shipment id, route id and strategy. The server runs the planning engine again and stores a snapshot of its own evaluation: route name, every leg, cost breakdown, duration, arrival, emissions, reliability, Balanced score, strategy, reason, notes and time. Posted prices or names are ignored, and later route changes never alter the snapshot.
  - A shipment has at most one active booking. The service checks this under a row lock, and the database enforces it with `uk_bookings_active_shipment`, so double clicks and concurrent submissions create exactly one booking.
  - The shipment status becomes BOOKED.
- **Booking states:** PENDING_PAYMENT → CONFIRMED (after a paid attempt) or → CANCELLED (unpaid only; the shipment returns to its previous status and can be booked again). A paid booking cannot be cancelled.
  - `/bookings` lists your bookings; `/bookings/view?id=` shows one.
- **Simulated payment** (`/payments?bookingId=`):
  - The page is clearly labelled as a simulation. It shows the amount from the snapshot, a method label (UPI, Card, Net banking) and two buttons, *Simulate success* and *Simulate failure*. No card or account fields exist, and no money moves.
  - Each click is a new attempt: PENDING → PAID or FAILED, with timestamps. After a failure you can retry or cancel.
  - Concurrent clicks charge at most once.
- **Ownership:** another user's booking or payment gives the same 404 as one that does not exist.
- **Administrator view:** the administrator can view any booking (read-only) and the list at `/admin/bookings`, but cannot pay or cancel; those answer 404.
- **Tracking** (confirmed bookings only):
  - Milestones go in one order: Booked → Picked up → In transit → At transfer (only for routes with more than one leg) → Out for delivery → Delivered. No skipping, no going back, and Delivered is final; recording Delivered sets the shipment to COMPLETED.
  - *Booked* is recorded automatically, in the same transaction as the successful payment (source SYSTEM).
  - Customers see the timeline on their own booking page; anyone else gets 404.
- **Admin tracking page** (`/admin/tracking`, 404 for everyone else):
  - Lists confirmed bookings. Each has a form that offers only the single legal next milestone with a description (source ADMIN), and an *Advance (simulation)* button (source SIMULATED).
  - Both forms carry the milestone the page was shown with, and the database allows each (booking, milestone) once, so double submissions or concurrent clicks record a milestone only once.
  - With `LOGIOPT_SIM_INTERVAL_SECONDS` set, a scheduler thread (`logiopt-tracking-sim`) calls the same idempotent method on a timer. It stops on undeploy.
- **Notifications** (`/notifications`, customers only):
  - Created for booking created, payment succeeded, payment failed, booking cancelled and every tracking milestone. The administrator gets none.
  - They are written on a managed background thread (`logiopt-notify-1`) after the business transaction commits. A notification failure is logged and never blocks or rolls back the booking, payment or tracking.
  - The dedupe key makes retries harmless.
  - The header shows the unread count. You can mark one or all as read (POST + CSRF); another user's notification gives 404.
- **Compare routes:** `/compare?shipmentId=<id>&strategy=<KEY>`. The page shows, for each eligible route, the cost breakdown, duration, estimated arrival, emissions, reliability and notes. It highlights the recommended route with its reason (plus the score breakdown for Balanced). It also lists the routes the chosen strategy did not consider and why, and the routes rejected by eligibility (capacity, cargo type) and why.
- **Run each strategy:** choose Cheapest, Fastest, Greenest or Balanced in the dropdown and click *Compare*. Keys are case-insensitive. An unknown key gives an error page; there is no silent fallback.
- **List shipments:** `/shipments` shows your own shipments only.
- **TXT report:** click *Generate TXT report* on the comparison page. The job is recorded in the `reports` table and runs on a background thread; you are taken to `/reports?jobId=…`, which refreshes until the job is DONE or FAILED. The database is the source of truth for the status; the in-memory map only holds jobs in flight. Every job ends DONE or FAILED, also on unexpected errors and at shutdown, and a job left unfinished by a stopped server is recorded and shown as FAILED after the restart. Your reports are listed in `/history`. The report states the eligibility result (for example "No routes are configured for this source and destination."). Money, duration and emissions have 2 decimals, weight and reliability 3 (their stored scales), Balanced scores 6. The file `shipment-<id>-<STRATEGY>-<yyyyMMddHHmmss>.txt` is written to the reports directory, which is resolved against Tomcat's working directory unless you configure it. If that name already exists, `-2`, `-3`, … is appended; a partly written file is deleted.
- **Download a report** (`/reports/download?jobId=`): only your own DONE reports. The file is found only through the managed file name stored for the job: the name must have exactly the generated form, and the resolved real path must be a regular file directly inside the reports directory. Nothing from the request except the job id is used. The response is `Content-Type: text/plain; charset=UTF-8`, `Content-Disposition: attachment; filename="logiopt-report-shipment-<id>-<strategy>.txt"` and `X-Content-Type-Options: nosniff`. Missing, foreign, unfinished and failed reports, and stored names that do not resolve safely, give the usual 404 without any path.
- **Demo: a saved past-dated shipment can still be compared.** `docs/demo/past-dated-shipment.sql` (outside the classpath and the WAR) inserts one DRAFT shipment dated 30 days ago for an existing customer. Edit the email on its `SET` line and run it manually against the database you chose for the demo (for example `source docs/demo/past-dated-shipment.sql` in a MySQL client connected to it). Running it again adds nothing.
- **Serialized snapshots:** `ShipmentSnapshotService` saves and loads `shipment-<id>.ser` in the snapshots directory. Loading uses an exact class allow-list with depth, reference, size (4 KB) and array limits, then revalidates the shipment. It is an auxiliary feature, MySQL is the primary store, and the web UI does not call it.

## 6. Implemented features
Registration, sign-in and sign-out with PBKDF2 password hashing, throttling, session rotation, CSRF tokens, owner-only access and a single seeded administrator; a read-only start-up schema check; booking from the comparison page with a server-computed immutable snapshot, one active booking per shipment, unpaid cancellation and a simulated payment with retries; ordered tracking milestones (admin, simulated and automatic) with a customer timeline; in-app notifications on a managed executor; customer and admin dashboards with database counts; shipment, booking and report history; draft editing; persisted report jobs with a safe owner-only download; shipment validation, JDBC persistence and listing; seeded routes loaded through `RouteFactory` (invalid rows are logged and skipped); eligibility filtering by endpoints, capacity and cargo; one immutable `RouteEvaluation` per route, shared by identity between the comparison page, all four strategies and the report; Cheapest, Fastest, Greenest and Balanced with reasons, per-strategy exclusions and the common tie-break; custom exceptions; a background TXT report service (fixed pool of named daemon threads, thread-safe job map, executor shut down by the context listener); Shipment serialization; security response headers on every page; friendly error pages with no stack traces.

## 7. Verification status (observed)
- **Latest run (2026-10-27, after the desktop phases):** `mvn test` against `logiopt_test`: **602 tests, 0 failures, 0 errors**. Per module: core 439, web 58, desktop 105. The 7 upgrade-path cases in `MigrationIntegrationTest` (core) are skipped unless `LOGIOPT_TEST_UPGRADE_DUMP` points at the Review 2 backup; with it set, 0 are skipped. The desktop view smoke tests need a display.
- Earlier per-phase results are in the git history of this file and in `docs/REVIEW3.md` (section 11, change log).

## 8. Known limitations and incomplete requirements
- The desktop client connects to MySQL directly with the account in `LOGIOPT_DB_USER` / `LOGIOPT_DB_PASSWORD`. The services enforce owner checks, the single administrator, and the booking, payment and validation rules for the desktop client exactly as for the web. However, anyone who runs the client has that database account and could bypass the client with the mysql tool. A real deployment would need a server API or restricted database accounts.
- In the desktop client so far: sign in, register, dashboard, new and edit shipment with an optimisation preference, my shipments, route comparison, review your order, booking detail with the simulated payment and cancellation, my bookings, history and account. On the account screen customers can change their name and password (same rules and sign-in throttling as registration and sign-in); the administrator's password stays set by `LOGIOPT_ADMIN_PASSWORD`. The web application has no account screen and ignores the preference. The unread badge refreshes when a page opens and after a booking, payment or cancellation; a timed poller comes with the notifications screen. Use the web application for tracking, notifications, reports and the admin pages until the desktop screens exist.
- The optimisation preference only marks the preferred strategy's winner as "Your choice" and pre-selects that strategy when booking; all four strategies are always compared. If the preferred strategy cannot rank any route, the comparison says why and every eligible route can still be booked.
- Only one front end per database at a time (see "Two front ends" at the top).
- The pages are rendered in tests by Jasper (Tomcat 10.1.61 embedded) and checked for errors and expected text, but they have not been looked at in a browser by these phases.
- On undeploy the context listener now deregisters the MySQL driver and stops its cleanup thread; this has not yet been observed on a real Tomcat undeploy.
- Notifications are in-app only (no email or SMS). If the server stops while notifications are still queued, those are dropped and logged. The business data they describe is already committed.
- Payments are simulated only: no gateway, no refunds. A payment attempt is created PENDING and resolved to PAID or FAILED in the same transaction, so PENDING is never visible between requests.
- Shipments created before Phase 2 belong to the disabled demo user 1, so no signed-in user can see them.
- There is no password reset or email verification (no email in scope). Changing the admin password means changing `LOGIOPT_ADMIN_PASSWORD` and restarting.
- Sign-in throttling and sessions are in memory on one server and reset when it restarts.
- All route data is illustrative sample data. The distances are round figures, and every V9 corridor uses the Mumbai → Delhi rates and factors, so costs and emissions scale with distance only. The two directions of the three V3 corridors have different distances (see section 1).
- Shipments saved before Phase 1 with an unknown city (for example "Bengalaru") stay viewable and show the same message.
- Report jobs and files are never pruned. Deleting a file by hand makes its download answer 404; the job stays DONE.
- A job is treated as abandoned when it is unfinished in the database and not in flight on this server, so the design assumes one application server per database.
- Estimated arrival assumes departure at 00:00 on the dispatch date and is shown in ISO format (for example `2026-11-03T06:00`).
- Balanced scores are relative to the routes currently compared.

## 9. Troubleshooting
| Symptom | Cause and fix |
|---|---|
| Every page answers 503 "temporarily unavailable" | The schema is behind this build, or a migration is recorded as failed. The Tomcat log (`D:\tools\tomcat\logs\catalina.*.log`) names the exact MigrationTool command. Run it with `--status` first (section 1). |
| Tomcat fails to start LogiOpt: "database configuration is invalid" | `LOGIOPT_DB_URL` (or `db.properties`) is missing or malformed in the shell that started Tomcat. Set it in the same window before `startup.bat`. |
| "Cannot connect to the database" in the log, 503 pages | MySQL is not running on the configured host and port, or the user and password are wrong. Check with `mysql -u <user> -p -h 127.0.0.1 logiopt`. |
| MigrationTool: "Refusing: ... does not end in _test" | `--target=test` only accepts a database whose name ends in `_test`. Use `--target=main --confirm-database=<name>` for the real database. |
| MigrationTool lists structure differences and stops | The existing database is not the Review 2 baseline. Nothing was changed. Compare it with `V1__baseline.sql`, or migrate a fresh database. |
| MigrationTool: "failed earlier" / "checksum mismatch" | A previous run failed part-way or a script was edited after it was applied. Restore the backup (section 1.1, step 5) rather than editing `schema_migrations`. |
| Database tests are skipped | `LOGIOPT_TEST_DB_URL` is not set in the shell running Maven. |
| No administrator can sign in | `LOGIOPT_ADMIN_PASSWORD` was unset or shorter than 8 characters when Tomcat started (the log says so). Set it and restart. The email is `admin@logiopt.local`. |
| "Too many failed sign-in attempts" (429) | 5 wrong passwords for that email from that address within 5 minutes. Wait 5 minutes, or restart Tomcat (the lock is in memory). |
| 403 "This form has expired" | The session timed out or the page was opened before signing in again. Reload the page and resubmit. |
| A report stays FAILED or its download gives 404 | The reports directory is not writable, or the file was deleted. Check `LOGIOPT_REPORTS_DIR` (relative paths are resolved against Tomcat's working directory) and the Tomcat log. |
| Offline Maven build cannot find a plugin or dependency | Run the same command once without `-o` to fill `D:\tools\m2repo`. |
| Git warns "LF will be replaced by CRLF" | The repository has `.gitattributes` with `* text=auto`; the warning comes from the global `core.autocrlf=true` setting and does not change file contents. |

See `docs/REVIEW3.md` for the Review 3 features, state machines, security controls and test evidence, `docs/FORMULAS.md` for the formulas and `docs/REVIEW2.md` for the Review 2 demo script and the concept map.

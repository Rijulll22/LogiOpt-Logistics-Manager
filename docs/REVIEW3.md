# LogiOpt: Review 3

This document describes what Review 3 added to LogiOpt, how it is secured, how the schema is migrated, and which tests prove each acceptance item. Everything stated as verified was run on 2026-10-26 (Phase 7 route data: 2026-10-27) against the disposable database `logiopt_test`. Setup, migration, deployment and troubleshooting are in the `README.md`.

## 1. Features
| Area | What it does | Main code |
|---|---|---|
| Accounts | Registration (email, name, password of at least 8 characters), sign-in, sign-out, one administrator seeded from `LOGIOPT_ADMIN_PASSWORD` with the fixed email `admin@logiopt.local`. Duplicate emails are refused without revealing which addresses exist. | `AuthService`, `PasswordHasher`, `LoginThrottle`, `UserDAO`, `LoginServlet`, `RegisterServlet`, `LogoutServlet` |
| Ownership | Every shipment, comparison, booking, payment, tracking timeline, notification, report and history entry belongs to the signed-in user; anything else gives the same 404 as a missing resource. `/admin` pages give 404 to everyone but the administrator. | `AuthFilter`, the `...ForOwner` / `...ForUser` DAO methods |
| Shipments | Create with known cities (select lists, also checked on the server), exact weight 0.001 to 999,999,999.999 kg with at most 3 decimals, dispatch date not in the past. Edit only while DRAFT. | `ShipmentValidator`, `ShipmentForm`, `ShipmentService`, `ShipmentCreateServlet`, `ShipmentEditServlet` |
| Planning (Review 2, unchanged) | Eligibility, one immutable `RouteEvaluation` per eligible route, four strategies, recommendation reasons. Saved shipments with a past date can still be compared. | `PlanningService`, `RouteEvaluator`, strategies |
| Route data (Phase 7) | Illustrative sample data, not real carrier tariffs. Every ordered pair of the six cities has at least three routes (91 routes, 151 legs). V3's 10 routes are unchanged; V9 adds Road direct, Rail direct (refuses HAZARDOUS) and Road-Rail-Road for the other 27 ordered pairs, generated from one distance table with the Mumbai → Delhi constants. | `V9__seed_all_city_pairs.sql`, `RouteDAO` |
| Booking | Book any eligible route from the comparison page. The server re-runs the planning engine and stores a snapshot (route, every leg, cost breakdown, duration, arrival, emissions, reliability, score, strategy, reason, notes, time) that later route changes never alter. One active booking per shipment. | `BookingService`, `BookingDAO`, `BookingServlet` |
| Simulated payment | Page labelled as a simulation; amount from the snapshot; method label UPI, Card or Net banking; *Simulate success* / *Simulate failure*. Each click is a new attempt. | `PaymentService`, `PaymentDAO`, `PaymentServlet` |
| Tracking | Ordered milestones for CONFIRMED bookings, recorded by the administrator (with a description), by the *Advance (simulation)* button, or by the optional scheduler (`LOGIOPT_SIM_INTERVAL_SECONDS`). The customer sees the timeline. | `TrackingService`, `TrackingDAO`, `TrackingSimulator`, `AdminTrackingServlet`, `BookingViewServlet` |
| Notifications | In-app rows for booking created, payment succeeded, payment failed, booking cancelled and every milestone, written after commit on a managed executor; unread count in the header; mark one or all read. | `NotificationService`, `NotificationDAO`, `NotificationServlet` |
| Dashboard and history | Customer counts (shipments and bookings by status, unread notifications, latest milestone per confirmed booking); admin counts (enabled users by role, bookings by status, confirmed bookings awaiting tracking); latest 25 shipments, bookings and reports. | `DashboardService`, `DashboardDAO`, `HistoryService`, `DashboardServlet`, `AdminDashboardServlet`, `HistoryServlet` |
| Reports | Background TXT report (format unchanged since Review 2) with persisted job status; owner-only download of DONE reports. | `ReportService`, `ReportDAO`, `ReportServlet`, `ReportDownloadServlet` |
| Operations | Versioned migrations with a CLI, a read-only start-up schema check (503 until migrated), security headers, friendly error pages, executor and driver shutdown on undeploy. | `MigrationRunner`, `MigrationTool`, `SchemaGate`, `SecurityHeadersFilter`, `AppContextListener` |

## 2. State machines
### Booking (`bookings.status`)
| From | Event | To | Rule |
|---|---|---|---|
| (none) | Customer books an eligible route of their own DRAFT or PLANNED shipment, dispatch date today or later, no active booking | PENDING_PAYMENT | Shipment becomes BOOKED; snapshot stored |
| PENDING_PAYMENT | A payment attempt succeeds | CONFIRMED | Same transaction records the attempt PAID and the first milestone BOOKED (source SYSTEM) |
| PENDING_PAYMENT | A payment attempt fails | PENDING_PAYMENT | Attempt recorded FAILED; the customer may retry or cancel |
| PENDING_PAYMENT | Owner cancels | CANCELLED | Shipment gets back the status it had before booking |
| CONFIRMED | Cancel or pay again | refused (409) | "Paid bookings cannot be cancelled." / "This booking is already paid." |
| CANCELLED | Pay or cancel again | refused (409) | Terminal |

The administrator can view any booking but cannot pay or cancel (404).

### Payment attempt (`payments.status`)
| From | Event | To |
|---|---|---|
| (none) | *Simulate success* or *Simulate failure* on a PENDING_PAYMENT booking | PENDING, resolved in the same transaction |
| PENDING | success | PAID (at most one PAID attempt per booking, enforced by a unique key) |
| PENDING | failure | FAILED; a retry is a new attempt with the next attempt number |

### Shipment (`shipments.status`)
| From | Event | To |
|---|---|---|
| (none) | Created | DRAFT (editable) |
| DRAFT or PLANNED | Booked | BOOKED (not editable) |
| BOOKED | Unpaid booking cancelled | its previous status (DRAFT is editable again) |
| BOOKED | Milestone DELIVERED recorded | COMPLETED |

### Tracking milestones (`tracking.status`, CONFIRMED bookings only)
`BOOKED → PICKED_UP → IN_TRANSIT → AT_TRANSFER (only routes with more than one leg) → OUT_FOR_DELIVERY → DELIVERED`

There is exactly one legal next milestone: no skipping, no going back, and DELIVERED is terminal. Every write names the milestone the caller saw as current; if tracking has moved on, nothing is recorded. The unique key (booking, milestone) backs this up, so double submissions and concurrent advances record a milestone once.

### Report job (`reports.status`)
| From | Event | To |
|---|---|---|
| (none) | Submitted | QUEUED (row written before work starts) |
| QUEUED | Worker starts | RUNNING |
| QUEUED or RUNNING | File written and status saved | DONE (managed file name stored) |
| QUEUED or RUNNING | Error, shutdown, or found unfinished with no worker (after a restart) | FAILED with a user-facing message |

The first terminal state wins. A partly written file, or a file whose DONE status could not be saved, is deleted.

### Notification
UNREAD → READ (owner only, one or all). Each (recipient, type, booking, event) exists once.

## 3. Security controls
| Control | How | Evidence |
|---|---|---|
| Password storage | PBKDF2WithHmacSHA256, 600,000 iterations (minimum 100,000), 16-byte random salt per user, 256-bit key, constant-time comparison | `PasswordHasherTest`, `AuthServiceIntegrationTest` |
| Sign-in throttling | 5 failures per (email, client address) within 5 minutes lock that pair for 5 minutes (HTTP 429, generic message) | `LoginThrottleTest`, `WebSecurityIntegrationTest` |
| No account enumeration | One message for every failed sign-in; duplicate registration looks exactly like success | `AuthServiceIntegrationTest`, `WebSecurityIntegrationTest` |
| Sessions | Server-side; id rotated at sign-in (`changeSessionId`), invalidated at sign-out, 30-minute idle timeout; cookie `HttpOnly; SameSite=Lax`, `Secure` over HTTPS, cookie-only tracking | `WebSecurityIntegrationTest`, `WebXmlTest`; cookie attributes checked offline with Tomcat 10.1.61's own classes in Phase 2 |
| CSRF | Per-session token in a hidden field of every POST form, checked with a constant-time comparison; rotated at sign-in; missing or wrong token gives 403 | `FilterTest`, `WebXmlTest` (every POST form), `WebSecurityIntegrationTest` |
| Authorization | AuthFilter requires sign-in except `/login` and `/register`; `/admin/*` answers 404 to non-admins; Servlets and services check owner or admin again; identity comes only from the session | `FilterTest`, `WebSecurityIntegrationTest`, `DashboardServletTest`, `TrackingNotificationIntegrationTest` |
| SQL injection | Every query with values is a `PreparedStatement`; the only plain `Statement` runs the checksummed migration scripts from the classpath | code review (Phase 6 sweep) |
| Output encoding | Every value is written with `<c:out>` or `<c:url>`/`<c:param>`; no scriptlets (`scripting-invalid` in web.xml), no JavaScript, no CSS | `JspRenderingIntegrationTest` (escaped `<b>`, `&` and quotes in a rendered page; static scan of every JSP) |
| Response headers | `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: same-origin`, `Content-Security-Policy: default-src 'none'; img-src 'self'; form-action 'self'; frame-ancestors 'none'; base-uri 'none'`, `Cache-Control: no-store` on every response (first filter in web.xml) | `SecurityHeadersFilterTest`, `EndToEndJourneyIntegrationTest` (every response of the journey) |
| Safe download | Only the owner's DONE reports; file located only from the stored managed name (exact pattern, normalized and real path directly inside the reports directory, regular file); attachment with a generated name; `text/plain; charset=UTF-8`; `nosniff`; any refusal is the normal 404 | `ReportServiceTest`, `ReportPersistenceIntegrationTest`, `WebSecurityIntegrationTest` |
| No secrets in logs or pages | Logs name `LOGIOPT_ADMIN_PASSWORD` but never its value; errors never include the JDBC URL or credentials; no page shows a hash, salt, password or server path; failed reports show a generic message | code review; `JspRenderingIntegrationTest` (hash, salt, password and reports path absent from every rendered page); `SchemaGateTest` (503 page reveals nothing) |
| Error pages | Friendly `error.jsp` for 400, 401, 403, 404, 405, 429, 500, 503 and any exception; no stack trace | `WebXmlTest`, `JspRenderingIntegrationTest` |
| Database guards | Unique keys for one active booking per shipment, one PAID attempt, one milestone per booking, notification dedupe, one admin, one row per report job and file; CHECKs on every status | `MigrationIntegrationTest`, `BookingServiceIntegrationTest`, `TrackingNotificationIntegrationTest` |
| Test isolation | Destructive tests run only on `LOGIOPT_TEST_DB_URL` and refuse any database whose name (in the URL and from `SELECT DATABASE()`) does not end in `_test` | `DBConnectionTest`, `MigrationToolTest` |

## 4. Schema and migrations
Migrations live in `logiopt-core/src/main/resources/db/migration` and are applied with `MigrationTool` (README section 1). The history table `schema_migrations` stores version, script, SHA-256 checksum (LF line endings), type (`SQL` or `BASELINE`), success and error. A named lock keeps runs apart.

| Version | Change |
|---|---|
| V1 | Review 2 baseline: users, shipments, routes, route_legs, bookings, tracking, payments, notifications, reports. On an existing Review 2 database it is recorded as BASELINE after a structural comparison (columns, types, nullability, keys, foreign keys, CHECK names) |
| V2 | `uk_route_legs_seq` if missing |
| V3 | Seed: disabled demo user 1 and 10 routes with legs (upsert, no deletes) |
| V4 | Password salt, iterations, algorithm; generated `admin_marker` with a unique key (one admin) |
| V5 | Booking snapshot columns, `booking_legs`, generated `active_shipment_id` (unique), payment attempt numbers with PENDING/PAID unique keys |
| V6 | Tracking (shipment, description, source, recorder, millisecond time, unique booking+milestone) and notifications (type, booking, event key, read time, dedupe key) |
| V7 | Report jobs: job id, owner, status CHECK, error, start and finish times, managed file name; CHECKs tie file name, error and finish time to the status |
| V8 | `uk_routes_pair_name (source, destination, name)` if missing: one route name per corridor |
| V9 | Illustrative route data for the 27 ordered pairs without routes: 81 routes with ids `1000 + 100 × source + 10 × destination + kind`, 135 legs. Two `INSERT … SELECT` statements over derived tables of cities, distances and route kinds. Corridors that already have a route are skipped; duplicate keys leave rows as they are; nothing is updated or deleted |
| V10 | Nullable `shipments.optimisation_preference` (binary collation) with a CHECK on CHEAPEST, FASTEST, GREENEST, BALANCED; existing rows keep NULL, read as BALANCED |

V999 (`logiopt-core/src/test/resources/db/test-migration`) fails on purpose and exists only in tests. Generated columns and CHECK constraints need MySQL 8.0.19+, which the runner checks.

## 5. Test results (as run)
Command (PowerShell, from the project folder, with `LOGIOPT_TEST_DB_URL` pointing at `logiopt_test`, `LOGIOPT_DB_URL` pointed at a closed port for the run, `LOGIOPT_TEST_UPGRADE_DUMP` set to the filtered Review 2 backup, `LOGIOPT_SIM_INTERVAL_SECONDS` unset):
```
mvn -o -B -f "D:\java project\Code\pom.xml" "-Dmaven.repo.local=D:\tools\m2repo" clean package
```
Result on 2026-10-26 (Phase 6): **377 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS**; WAR built (then `target\logiopt.war`; since the module split `logiopt-web\target\logiopt.war`); JaCoCo report in `target\site\jacoco` of each module (`logiopt-core`, `logiopt-web`, `logiopt-desktop`).

"0 skipped" in every run on this page needs `LOGIOPT_TEST_UPGRADE_DUMP` set; without it the 7 upgrade-path cases of `MigrationIntegrationTest` (core) are skipped.

Result on 2026-10-27 (Phase 7, `mvn -o -q "-Dmaven.repo.local=D:\tools\m2repo" clean verify` with the same variables): **467 tests, 0 failures, 0 errors, 0 skipped**; WAR built with V8 and V9. The table below shows this run.

| Package | Test class | Tests |
|---|---|---|
| dao | `DaoIntegrationTest` | 13 |
| dao | `DBConnectionTest` | 12 |
| dao | `DemoScriptIntegrationTest` | 2 |
| dao | `MigrationIntegrationTest` | 17 |
| dao | `MigrationRunnerTest` | 21 |
| dao | `MigrationToolTest` | 3 |
| dao | `RouteNetworkIntegrationTest` | 88 |
| model | `BookingStateTest` | 3 |
| model | `MilestoneTest` | 4 |
| model | `RouteValidationTest` | 53 |
| report | `ReportPersistenceIntegrationTest` | 6 |
| report | `ReportServiceTest` | 15 |
| report | `ShipmentSnapshotServiceTest` | 6 |
| service | `AuthServiceIntegrationTest` | 12 |
| service | `BookingServiceIntegrationTest` | 9 |
| service | `CalculationTest` | 9 |
| service | `DashboardHistoryIntegrationTest` | 7 |
| service | `EligibilityServiceTest` | 7 |
| service | `LoginThrottleTest` | 5 |
| service | `PasswordHasherTest` | 4 |
| service | `PlanningServiceTest` | 6 |
| service | `ShipmentDraftEditIntegrationTest` | 6 |
| service | `ShipmentValidatorTest` | 34 |
| service | `TrackingNotificationIntegrationTest` | 15 |
| service | `TrackingSimulatorTest` | 3 |
| strategy | `SeedCoreWinnersTest` | 6 |
| strategy | `StrategyTest` | 24 |
| util | `NumbersTest` | 19 |
| web | `DashboardServletTest` | 3 |
| web | `EndToEndJourneyIntegrationTest` | 1 |
| web | `FilterTest` | 18 |
| web | `JspRenderingIntegrationTest` | 4 |
| web | `SchemaGateTest` | 5 |
| web | `SecurityHeadersFilterTest` | 4 |
| web | `WebSecurityIntegrationTest` | 18 |
| web | `WebXmlTest` | 5 |
| | **Total** | **467** |

Coverage of the main code, before the module split; it now lives in `logiopt-core/src/main/java` and `logiopt-web/src/main/java`, with the desktop client in `logiopt-desktop/src/main/java` (JaCoCo 0.8.12, Phase 7 run): lines 3526/4070 (86.6 %), branches 1234/1626 (75.9 %), instructions 16281/18601 (87.5 %), methods 782/808 (96.8 %). By package (lines / branches): dao 85.5 % / 73.2 %, exception 100 % / 100 %, model 95.8 % / 77.9 %, report 89.7 % / 72.7 %, service 92.3 % / 77.4 %, strategy 95.2 % / 84.8 %, util 100 % / 100 %, web 73.8 % / 75.1 %. Coverage is reported only; there are no thresholds.

How the web tests work: servlet-level tests run the real filters in web.xml order and the real Servlets through JDK-proxy fakes of the Servlet API (`WebFakes`, `WebApp`). `JspRenderingIntegrationTest` replays the request attributes those Servlets produced and renders every JSP with Jasper in a test-scope embedded Tomcat 10.1.61 (127.0.0.1, random port). The JSP configuration comes from the real web.xml.

## 6. Acceptance checklist: requirement to evidence
"Tests" means the item is asserted by the named automated tests in the run above. "Code reading" means it was checked only by reading code or configuration.

| # | Acceptance item | Verified by tests | Only by code reading or not verified |
|---|---|---|---|
| 1 | Build succeeds; Review 2 behaviour intact (recommendation winners unchanged on the seeded dataset) | `mvn clean package` above; `SeedCoreWinnersTest`, `StrategyTest`, `CalculationTest`, `PlanningServiceTest`, `EligibilityServiceTest`, `RouteValidationTest`, `ReportServiceTest` (report text); `EndToEndJourneyIntegrationTest` (route 2 is the Balanced winner from the database); `RouteNetworkIntegrationTest` (routes 1-4 and 1-3 loaded from the database after V9 keep their Balanced scores and winners) | — |
| 2 | Registration, login, logout, session expiry work; demo-user identity gone from real flows | `AuthServiceIntegrationTest`, `PasswordHasherTest`, `LoginThrottleTest`, `FilterTest`, `WebSecurityIntegrationTest` (`registrationSignInSessionRotationAndSignOut`, `demoUserShipmentsAreNotVisibleToSignedInUsers`, `failedSignInIsGenericThrottledAndNeedsCsrf`), `EndToEndJourneyIntegrationTest`. Expiry is tested as "the session id is no longer valid → `/login?expired=1`" | The real 30-minute idle timeout in Tomcat (configuration checked by `WebXmlTest` only); `Secure` cookie over HTTPS (Tomcat classes run offline in Phase 2, no HTTPS server) |
| 3 | Cross-user access denied for every resource type; CSRF verified; only the seeded admin can add tracking events | `WebSecurityIntegrationTest` (shipments, comparisons, reports, report downloads, bookings, payments, timelines, notifications, history, draft edit, dashboards, admin pages), `DashboardHistoryIntegrationTest`, `ShipmentDraftEditIntegrationTest`, `ReportPersistenceIntegrationTest`, `TrackingNotificationIntegrationTest`, `DashboardServletTest`, `FilterTest`, `WebXmlTest` (CSRF field in all 12 POST forms), `EndToEndJourneyIntegrationTest` | — |
| 4 | Booking from the comparison page; snapshot persisted and immutable; duplicates and concurrency prevented; past-date booking rejected | `BookingServiceIntegrationTest` (snapshot unchanged after route edits, 8-thread double submission, database unique key, past and today's date), `WebSecurityIntegrationTest` (`bookPayFailRetryAndConfirmThroughTheWebLayer`, `pastDispatchDateCannotBeBookedButCanStillBeCompared`), `ShipmentDraftEditIntegrationTest` (edits never touch a snapshot), `EndToEndJourneyIntegrationTest` | The *Book* buttons in the rendered comparison page are rendered by `JspRenderingIntegrationTest` but not clicked in a browser |
| 5 | Simulated payment transitions correct; invalid transitions rejected; unpaid cancel works; paid cancel rejected | `BookingStateTest`, `BookingServiceIntegrationTest` (fail → retry → paid, 8-thread payment race, mixed pay/cancel), `WebSecurityIntegrationTest`, `EndToEndJourneyIntegrationTest` | — |
| 6 | Tracking timeline for the customer; admin and simulated tracking; notifications created and owner-only | `TrackingNotificationIntegrationTest`, `MilestoneTest`, `TrackingSimulatorTest`, `WebSecurityIntegrationTest` (`adminTrackingIsAdminOnlyCsrfProtectedAndIdempotent`, `customersSeeTheTimelineOfTheirOwnBookingsOnly`, `notificationsAreOwnerOnlyAndCanBeMarkedRead`), `EndToEndJourneyIntegrationTest` (admin to DELIVERED, timeline, one notification per milestone) | The scheduler running on the installed Tomcat with `LOGIOPT_SIM_INTERVAL_SECONDS` set |
| 7 | Saved past-date shipments viewable and re-comparable; weight bounds and report formatting correct; report jobs always end DONE or FAILED | `WebSecurityIntegrationTest` (`pastDispatchDateCannotBeBookedButCanStillBeCompared`), `DemoScriptIntegrationTest`, `JspRenderingIntegrationTest` (`comparison-past-date`), `ShipmentValidatorTest`, `NumbersTest`, `ReportServiceTest`, `ReportPersistenceIntegrationTest` (restart, shutdown, abandoned jobs) | — |
| 8 | Migrations work on fresh and existing-schema test databases | `MigrationIntegrationTest` (fresh install, re-run, upgrade from the restored Review 2 backup, missing unique key, structure mismatch, missing foreign key, partial schema, failing V999, checksum change, unknown version, seed re-run with bookings, V4 and V7 constraints, V9 reached on fresh install and upgrade with the 5 backup shipments kept and routes 1-10 unchanged, V8/V9 re-run adds nothing), `MigrationRunnerTest`, `MigrationToolTest` | The real `logiopt` database has not been migrated (README section 1.1 is the procedure) |
| 8a | Routes for every ordered pair of distinct cities (Phase 7) | `RouteNetworkIntegrationTest` (all 30 pairs plannable with at least 3 eligible routes and a Balanced recommendation for 5000 kg GENERAL; generated values match the distance table and constants; forward and reverse match; quality checks; HAZARDOUS refused by generated Rail direct), `WebSecurityIntegrationTest` (`sameSourceAndDestinationIsRejectedOnCreateAndEdit`), `ShipmentValidatorTest` | The shipment form note was rendered by `JspRenderingIntegrationTest` but not viewed in a browser |
| 9 | End-to-end journey executed; test results, limitations and unverified areas documented honestly | `EndToEndJourneyIntegrationTest` (servlet level, real filters and services on `logiopt_test`); this document and README section 7 | A browser walkthrough on the installed Tomcat (section 8 gives the procedure; the user runs it) |

Also verified only by reading code or configuration: driver deregistration and executor shutdown on a real undeploy, container dispatch to `error.jsp` for errors Tomcat raises itself (the declarations and the page are tested, the dispatch is not), and the browser's back-button behaviour after sign-out (the `no-store` header is tested).

## 7. Feature status
| Feature | Status |
|---|---|
| Accounts, sessions, CSRF, throttling, single admin | Implemented; tested |
| Owner-only access, admin-only pages (404) | Implemented; tested |
| Shipment create, validate, list, draft edit | Implemented; tested |
| Route comparison, four strategies (Review 2) | Unchanged; tested |
| Booking with immutable snapshot | Implemented; tested (including concurrency) |
| Simulated payment with retries | Implemented; tested (including concurrency) |
| Tracking (admin, simulated, scheduler) and customer timeline | Implemented; tested (scheduler on real Tomcat not run) |
| In-app notifications | Implemented; tested |
| Dashboards and history | Implemented; tested |
| Persisted report jobs and safe download | Implemented; tested |
| Migrations and start-up schema check | Implemented; tested on `logiopt_test`; not applied to `logiopt` |
| Security headers and friendly error pages | Implemented; tested (container error dispatch by configuration) |
| JSP rendering with realistic data | Tested with embedded Tomcat 10.1.61; not viewed in a browser |
| Email, password reset, real payments, carrier integration | Not in scope |

## 8. Manual browser walkthrough (procedure; not executed by these phases)
1. Migrate the chosen database (README section 1 or 1.1), set `LOGIOPT_ADMIN_PASSWORD`, deploy and start Tomcat (README section 4).
2. Open `http://localhost:8080/logiopt/` → sign-in page. Register a customer, sign in → dashboard with zero counts and explanations.
3. *New shipment*: Mumbai → Delhi, GENERAL, 5000 kg, a date next week → comparison page; switch strategies.
4. *Book* route 2 → booking page (pending) → *Pay* → choose UPI → *Simulate failure* (notice, still pending) → *Pay* → *Simulate success* → confirmed, timeline shows Booked.
5. In a second browser profile, sign in as `admin@logiopt.local` → *Admin: tracking* → record the next milestone with a description, then use *Advance (simulation)* until Delivered. *Admin: dashboard* shows the counts.
6. Back as the customer: the booking page shows every milestone; *Notifications* shows the unread count; *Mark all as read*.
7. On the comparison page *Generate TXT report* → status page → *Download the report (TXT)*; the browser saves `logiopt-report-shipment-<id>-balanced.txt`.
8. *History* lists the shipment, booking and report. A second customer opening the first customer's booking URL gets "The page you requested does not exist."
9. *Sign out*, then press the browser's back button: no earlier page is shown, and every protected URL leads to the sign-in page.
10. Optional demo: run `docs/demo/past-dated-shipment.sql` for the customer's email; the past-dated shipment can be compared but not booked.

## 9. Limitations (honest)
- No browser walkthrough has been done by these phases; rendering is verified by Jasper in tests, not by looking at pages.
- The real `logiopt` database has not been migrated; only `logiopt_test` has.
- Payments are simulated; notifications are in-app only; no email, password reset or email verification.
- Throttling and sessions are in memory on one server. Report jobs assume one application server per database.
- Notifications still queued when the server stops are dropped and logged; the business data is already committed.
- Report jobs and files are never pruned. Deleting a file by hand makes its download answer 404.
- Shipments created before Review 3 belong to the disabled demo user 1 and are not visible to signed-in users.
- All route data is illustrative sample data. V9 corridors use the Mumbai → Delhi rates and factors throughout, so their results differ only by distance. Mumbai → Delhi, Chennai → Kolkata and Ahmedabad → Bengaluru keep their V3 distances, while their reverse directions use the Phase 7 table, so the two directions of those three pairs are not symmetric.
- Coverage of the web package is the lowest (73.8 % of lines); most of the rest are DataAccessException branches that need a failing database.

## 10. Desktop client (phases D1-D4)
The JavaFX client (`logiopt-desktop`) uses the same `logiopt-core` services as the web application; presenters are plain Java and every database call runs off the JavaFX thread.

| Feature | Web application | Desktop client |
|---|---|---|
| Accounts, sign-in, registration, account page | yes | yes |
| Shipments, draft edit, comparison, optimisation preference | yes (no preference) | yes |
| Booking, simulated payment, cancellation | yes | yes |
| Tracking timeline on the booking | yes | yes (D4) |
| Notifications, unread badge | yes (on page load) | yes (D4, polled every 15 s) |
| Reports: generate, status, download | yes (browser download) | yes (D4, "Save a copy…") |
| Admin dashboard, users, all bookings, tracking | yes | yes (D4) |
| Dashboard, history | yes | yes |

**Security notes and limitations (desktop).** Identity comes only from the in-memory session after sign-in; every service checks ownership or the administrator role again, and a refusal looks like "not found". The administrator menu is built from the role, and the navigator refuses the other role's pages. The client connects to MySQL directly with the account in `LOGIOPT_DB_USER`, so anyone who can run it with those credentials can reach the database outside the application's checks; the desktop client is meant for a trusted machine. Background failures (badge, report status) are logged, never shown as dialogs. Sign-in throttling is per running client. Use one front end at a time per database with the same `LOGIOPT_REPORTS_DIR`.

**Test results per module (D4 run, 2026-10-27):** core 439, web 58, desktop 105; 602 in total, 0 failures, 0 errors, 0 skipped with `LOGIOPT_TEST_UPGRADE_DUMP` set (otherwise 7 core migration tests skip; view smoke tests ran with a display). Details in README section 7.

## 11. Change log (Review 3)
- **Phase 0:** read-only inspection and baseline build.
- **Phase 1:** versioned migrations V1-V3 with `MigrationTool` and structural baseline detection; city select lists with server-side checks; exact weight bounds; report and snapshot fixes; legacy SQL moved to `docs/legacy-sql-superseded`.
- **Phase 2:** V4; registration, sign-in, throttling, sessions, CSRF, single admin, owner-only access, start-up schema check.
- **Phase 3:** V5; booking with server-side snapshot, one active booking per shipment, simulated payment with retries, cancellation.
- **Phase 4:** V6; tracking milestones (admin, simulated, scheduler), customer timeline, in-app notifications on a managed executor.
- **Phase 5:** V7; dashboards, history, draft editing, persisted report jobs, safe report download, past-dated demo script.
- **Phase 6:** security headers filter; friendly error pages for 400/401/403/404/405/429/500/503; admin user counts exclude the disabled demo user; failed reports show a generic message instead of an exception name; remaining constant `Statement` queries converted to `PreparedStatement`; JSP rendering tests (embedded Tomcat, test scope), end-to-end journey test, JaCoCo report; `.gitattributes`; README and this document.
- **Phase 7:** V8 (one route name per corridor) and V9 (illustrative routes for every ordered city pair); shipment form note updated; `RouteNetworkIntegrationTest`, plus migration and same-city web tests.

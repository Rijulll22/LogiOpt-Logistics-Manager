# LogiOpt: Review 2 guide

> **Historical, before the module split.** This page describes the Review 2 single-module project. Paths such as `src/main/...` now live under `logiopt-core/` (Java, resources) and `logiopt-web/` (Servlets, JSPs); `web.AppContextListener` is in `logiopt-web`. The schema is now created by migrations V1 to V10 (README section 1).

## Demo script
Prerequisite: the database has been created and seeded and `logiopt.war` is deployed (see `README.md`).
1. **Invalid shipment:** open `http://localhost:8080/logiopt/shipments/new` and submit source `Mumbai`, destination ` mumbai `, no cargo type, weight `-3` and a past date. All errors appear together: same endpoints, missing cargo type, weight not positive, date in the past. The entered values are kept.
2. **Valid shipment:** Mumbai → Delhi, GENERAL, 5000 kg, a dispatch date of today or later. Saving it redirects to the comparison page with the Balanced strategy.
3. **Comparison:** routes 1-4 appear with their cost breakdowns, duration, arrival, emissions and reliability. The recommended route is highlighted with its reason and the Balanced score breakdown. The sample-data disclaimer is shown at the top and bottom of the page.
4. **Switch strategies:** Cheapest → R2 Rail direct (INR 22800.00); Fastest → R1 Road direct (30.00 h); Greenest → R4 Rail electric (145.00 kg CO2e); Balanced → R2 (score 0.167971). These are the values for 5000 kg asserted by `SeedCoreWinnersTest`.
5. **Exclusions and rejections:** create Ahmedabad → Bengaluru, 5000 kg, then choose Greenest. Route 9 stays in the comparison but is listed as "Emissions not available". Create a 16000 kg Mumbai → Delhi shipment: route 3 is rejected for capacity.
6. **Generate report:** click *Generate TXT report*. The status page moves from QUEUED/RUNNING to DONE and names the file in the reports directory.

## Java concepts → classes
| Concept | Where |
|---|---|
| OOP: abstraction, inheritance, polymorphism | `model.Route` (abstract) → `RoadRoute`, `RailRoute`, `MultimodalRoute`; `model.RouteFactory`; `strategy.OptimizationStrategy` → `AbstractRankingStrategy` → `CheapestStrategy`, `FastestStrategy`, `GreenestStrategy`; `BalancedStrategy` |
| Encapsulation and immutability | `model.Shipment`, `RouteLeg`, `CostBreakdown`, `RouteEvaluation`, `Recommendation`, `PlanningResult` (identity invariant) |
| Exceptions | `exception.LogiOptException` (checked base), `InvalidShipmentException` (all errors), `InvalidStrategyException`, `DataAccessException`, `RouteConfigurationException` (unchecked) |
| Collections | `List`/`Map` in `RouteEvaluator`, `PlanningResult`, `Recommendation`; `LinkedHashMap` strategy registry in `service.RecommendationService`; `EnumSet` in `Route`; `IdentityHashMap` set in `PlanningResult`; `Comparator` chains in `AbstractRankingStrategy`; `ConcurrentHashMap` in `report.ReportService` |
| File handling | `report.ReportService` (UTF-8 TXT, try-with-resources, `CREATE_NEW`) |
| Multithreading | `report.ReportService` (fixed `ExecutorService`, named daemon threads), `report.ReportJob` (volatile state, `CountDownLatch`), shutdown in `web.AppContextListener` |
| JDBC / MySQL | `dao.DBConnection`, `dao.ShipmentDAO`, `dao.RouteDAO` (PreparedStatement only); the original Review 2 `schema.sql` and `seed.sql` are in git history (commit 912e426) and are superseded by migrations V1 to V10 |
| JSP / Servlets | `web.ShipmentFormServlet`, `ShipmentCreateServlet`, `ShipmentListServlet`, `ComparisonServlet`, `ReportServlet`, `AppContextListener`; JSPs in `src/main/webapp/WEB-INF/jsp/` (JSTL + EL, no scriptlets or JavaScript) |
| Serialization | `report.ShipmentSnapshotService` (`ObjectOutputStream`/`ObjectInputStream` with an `ObjectInputFilter`), `Shipment implements Serializable` |
| Unit testing | `src/test/java/com/logiopt/**` (JUnit 5, parameterized tests, fixed `Clock`) |

## Known gaps
- `DaoIntegrationTest` was skipped in the build environment because no database credentials were set there. It skips itself when no database is reachable. The DB-backed flow (create → compare → report) was observed only through Tomcat access-log status codes and one generated report file.
- Out of scope: login, sessions, booking, payment, tracking, notifications, dashboard, shipment update/cancel, report download.
- Report jobs live in memory only. Snapshots are not exposed in the UI.

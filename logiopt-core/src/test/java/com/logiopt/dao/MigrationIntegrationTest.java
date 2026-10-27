package com.logiopt.dao;

import com.logiopt.exception.DataAccessException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fresh-install and upgrade migration paths against the disposable database named by
 * LOGIOPT_TEST_DB_URL (must end in _test). Every test starts from an empty database; the upgrade
 * tests restore the filtered backup named by LOGIOPT_TEST_UPGRADE_DUMP. The database is left
 * freshly migrated afterwards.
 */
class MigrationIntegrationTest {

    private static final long BACKUP_SHIPMENTS = 5;
    private static final long SEEDED_ROUTES = 10;
    private static final long SEEDED_LEGS = 16;
    /** V3's 10 routes plus V9's 27 corridors x 3 routes. */
    private static final long ALL_ROUTES = 91;
    /** V3's 16 legs plus V9's 27 corridors x (1 + 1 + 3) legs. */
    private static final long ALL_LEGS = 151;
    private static final List<String> FRESH_HISTORY = List.of("V1 SQL ok", "V2 SQL ok", "V3 SQL ok", "V4 SQL ok",
            "V5 SQL ok", "V6 SQL ok", "V7 SQL ok", "V8 SQL ok", "V9 SQL ok", "V10 SQL ok");
    private static final List<String> UPGRADE_HISTORY = List.of("V1 BASELINE ok", "V2 SQL ok", "V3 SQL ok",
            "V4 SQL ok", "V5 SQL ok", "V6 SQL ok", "V7 SQL ok", "V8 SQL ok", "V9 SQL ok", "V10 SQL ok");
    /** Routes 1-10 and their legs, one line per row, used to prove later scripts never change them. */
    private static final String V3_ROWS_SQL = "SELECT row_text FROM ("
            + "SELECT CONCAT_WS('|', route_id, name, source, destination, mode, reliability, capacity_kg, "
            + "handling_charge, transfer_charge_per_transfer, transfer_hours_per_transfer, waiting_hours, "
            + "waiting_rate_per_hour, unsupported_cargo) AS row_text FROM routes WHERE route_id <= 10 "
            + "UNION ALL SELECT CONCAT_WS('|', 'leg', leg_id, route_id, sequence_no, mode, distance_km, rate_per_tonne_km, "
            + "IFNULL(speed_kmh, '-'), buffer_hours, IFNULL(emission_factor, '-')) FROM route_legs WHERE route_id <= 10) t ORDER BY row_text";

    private static DBConnection db;

    @BeforeAll
    static void connect() throws Exception {
        db = TestDatabase.connect();
    }

    @AfterAll
    static void leaveFreshlyMigrated() throws Exception {
        if (db != null) {
            TestDatabase.dropAllTables(db);
            new MigrationRunner(db).migrate();
        }
    }

    @BeforeEach
    void emptyDatabase() throws Exception {
        TestDatabase.dropAllTables(db);
    }

    private static void restoreBackup() throws Exception {
        Path dump = TestDatabase.upgradeDump();
        TestDatabase.restoreDump(db, dump);
        assertEquals(BACKUP_SHIPMENTS, TestDatabase.queryLong(db, "SELECT COUNT(*) FROM shipments"));
        assertFalse(TestDatabase.tableExists(db, MigrationRunner.HISTORY_TABLE));
    }

    private static List<String> history() throws Exception {
        List<String> rows = new ArrayList<>();
        long count = TestDatabase.queryLong(db, "SELECT COUNT(*) FROM schema_migrations");
        for (int v = 1; v <= count; v++) {
            rows.add(TestDatabase.queryString(db, "SELECT CONCAT('V', version, ' ', type, ' ', "
                    + "IF(success, 'ok', 'failed')) FROM schema_migrations ORDER BY version LIMIT 1 OFFSET " + (v - 1)));
        }
        return rows;
    }

    /** @return the first column of every row of a query, in order */
    private static List<String> rows(String sql) throws Exception {
        List<String> rows = new ArrayList<>();
        try (Connection con = db.getConnection(); PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            DBConnection.requireTestDatabase(con);
            while (rs.next()) {
                rows.add(rs.getString(1));
            }
        }
        return rows;
    }

    private static List<String> withFailed999() {
        List<String> rows = new ArrayList<>(FRESH_HISTORY);
        rows.add("V999 SQL failed");
        return rows;
    }

    /** Checks V3's reference data (routes 1-10) and, when the full chain ran, V9's generated routes. */
    private static void assertSeeded() throws Exception {
        boolean allPairs = TestDatabase.tableExists(db, MigrationRunner.HISTORY_TABLE)
                && TestDatabase.queryLong(db, "SELECT COUNT(*) FROM schema_migrations WHERE version = 9 AND success") == 1;
        assertEquals(SEEDED_ROUTES, TestDatabase.queryLong(db, "SELECT COUNT(*) FROM routes WHERE route_id <= 10"));
        assertEquals(SEEDED_LEGS, TestDatabase.queryLong(db, "SELECT COUNT(*) FROM route_legs WHERE route_id <= 10"));
        assertEquals(allPairs ? ALL_ROUTES : SEEDED_ROUTES, TestDatabase.queryLong(db, "SELECT COUNT(*) FROM routes"));
        assertEquals(allPairs ? ALL_LEGS : SEEDED_LEGS, TestDatabase.queryLong(db, "SELECT COUNT(*) FROM route_legs"));
        assertEquals(1, TestDatabase.queryLong(db, "SELECT COUNT(*) FROM users WHERE user_id = 1 AND enabled = FALSE"));
        assertEquals(1, TestDatabase.queryLong(db, "SELECT COUNT(*) FROM information_schema.STATISTICS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'route_legs' AND INDEX_NAME = 'uk_route_legs_seq' "
                + "AND SEQ_IN_INDEX = 1"));
    }

    @Test
    void serverVersionOfTestDatabaseIsSupported() throws Exception {
        String version = TestDatabase.queryString(db, "SELECT VERSION()");
        assertTrue(MigrationRunner.isSupportedServerVersion(version), version);
    }

    @Test
    void freshInstallCreatesBaselineStructureAndSeed() throws Exception {
        List<String> log = new MigrationRunner(db, MigrationRunner.SCRIPTS.subList(0, 3)).migrate();
        assertEquals(3, log.size(), log.toString());
        assertEquals(List.of(), BaselineSchema.compare(TestDatabase.readSchema(db)), "V1 creates exactly the baseline");
        assertSeeded();
        assertEquals(1, new MigrationRunner(db, MigrationRunner.SCRIPTS.subList(0, 4)).migrate().size(),
                "V4 is applied on top");
        assertEquals(List.of("users.admin_marker: unexpected column (tinyint NULL)",
                "users.password_algorithm: unexpected column (varchar(40) NULL)",
                "users.password_iterations: unexpected column (int NULL)",
                "users.password_salt: unexpected column (varchar(64) NULL)"),
                BaselineSchema.compare(TestDatabase.readSchema(db)), "V4 adds exactly the auth columns");
        List<String> v3Rows = rows(V3_ROWS_SQL);
        assertEquals(6, new MigrationRunner(db).migrate().size(), "V5 to V10 are applied on top");
        assertEquals(FRESH_HISTORY, history());
        assertTrue(TestDatabase.tableExists(db, "booking_legs"));
        assertSeeded();
        assertEquals(v3Rows, rows(V3_ROWS_SQL), "V4 to V9 never change routes 1-10 or their legs");
    }

    @Test
    void appliedVersionIsReadOnlyAndReportsWhenTheDatabaseIsBehind() throws Exception {
        MigrationRunner full = new MigrationRunner(db);
        assertEquals(0, full.appliedVersion());
        assertFalse(TestDatabase.tableExists(db, MigrationRunner.HISTORY_TABLE));
        new MigrationRunner(db, MigrationRunner.SCRIPTS.subList(0, 3)).migrate();
        assertEquals(3, full.appliedVersion());
        assertEquals(10, full.requiredVersion());
        assertFalse(TestDatabase.tableExists(db, "users") && TestDatabase.queryLong(db,
                "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() "
                        + "AND TABLE_NAME = 'users' AND COLUMN_NAME = 'password_salt'") > 0);
        full.migrate();
        assertEquals(10, full.appliedVersion());
        TestDatabase.execute(db, "UPDATE schema_migrations SET success = FALSE, error_message = 'x' WHERE version = 4");
        assertThrows(DataAccessException.class, full::appliedVersion);
    }

    @Test
    void v7KeepsReportRowsConsistent() throws Exception {
        new MigrationRunner(db).migrate();
        TestDatabase.execute(db, "INSERT INTO shipments (shipment_id, user_id, source, destination, cargo_type, weight_kg, "
                + "dispatch_date, status) VALUES (700, 1, 'Mumbai', 'Delhi', 'GENERAL', 5, '2026-11-02', 'DRAFT')");
        String insert = "INSERT INTO reports (job_id, user_id, shipment_id, strategy_key, status, file_name, error_message, "
                + "finished_at) VALUES ('%s', %d, 700, 'BALANCED', '%s', %s, %s, %s)";
        TestDatabase.execute(db, String.format(insert, "job-ok", 1, "QUEUED", "NULL", "NULL", "NULL"),
                String.format(insert, "job-done", 1, "DONE", "'a.txt'", "NULL", "CURRENT_TIMESTAMP(3)"),
                String.format(insert, "job-failed", 1, "FAILED", "NULL", "'x'", "CURRENT_TIMESTAMP(3)"));
        assertThrows(SQLIntegrityConstraintViolationException.class, () -> TestDatabase.execute(db,
                String.format(insert, "job-ok", 1, "QUEUED", "NULL", "NULL", "NULL")), "job ids are unique");
        assertThrows(SQLIntegrityConstraintViolationException.class, () -> TestDatabase.execute(db,
                String.format(insert, "job-other", 1, "DONE", "'a.txt'", "NULL", "CURRENT_TIMESTAMP(3)")),
                "one row per managed file");
        assertThrows(SQLIntegrityConstraintViolationException.class, () -> TestDatabase.execute(db,
                String.format(insert, "job-x", 999999, "QUEUED", "NULL", "NULL", "NULL")), "owner must exist");
        for (String[] bad : new String[][] {{"DONE", "NULL", "NULL", "CURRENT_TIMESTAMP(3)"},
                {"QUEUED", "'b.txt'", "NULL", "NULL"}, {"FAILED", "NULL", "NULL", "CURRENT_TIMESTAMP(3)"},
                {"RUNNING", "NULL", "'x'", "NULL"}, {"FAILED", "NULL", "'x'", "NULL"}, {"QUEUED", "NULL", "NULL",
                "CURRENT_TIMESTAMP(3)"}, {"LOST", "NULL", "NULL", "NULL"}}) {
            assertThrows(SQLException.class, () -> TestDatabase.execute(db,
                    String.format(insert, "job-bad", 1, bad[0], bad[1], bad[2], bad[3])), String.join(",", bad));
        }
        assertEquals(3, TestDatabase.queryLong(db, "SELECT COUNT(*) FROM reports"));
    }

    @Test
    void v4AllowsAtMostOneAdministrator() throws Exception {
        new MigrationRunner(db).migrate();
        String insert = "INSERT INTO users (name, email, password_hash, role, enabled) VALUES ('A', '%s', 'x', 'ADMIN', FALSE)";
        TestDatabase.execute(db, String.format(insert, "first-admin@example.test"));
        assertThrows(SQLIntegrityConstraintViolationException.class,
                () -> TestDatabase.execute(db, String.format(insert, "second-admin@example.test")));
        assertThrows(SQLException.class, () -> TestDatabase.execute(db, "UPDATE users SET "
                + "password_algorithm = 'PBKDF2WithHmacSHA256', password_salt = 's', password_iterations = 10 "
                + "WHERE email = 'first-admin@example.test'"));
    }

    @Test
    void secondRunIsANoOp() throws Exception {
        new MigrationRunner(db).migrate();
        assertEquals(List.of(), new MigrationRunner(db).migrate());
        assertEquals(10, TestDatabase.queryLong(db, "SELECT COUNT(*) FROM schema_migrations"));
        assertSeeded();
    }

    @Test
    void upgradeFromBackupRecordsBaselineAndKeepsData() throws Exception {
        restoreBackup();
        List<String> log = new MigrationRunner(db, MigrationRunner.SCRIPTS.subList(0, 8)).migrate();
        assertTrue(log.get(0).startsWith("V1 recorded as BASELINE"), log.toString());
        List<String> v3Rows = rows(V3_ROWS_SQL);
        assertEquals(2, new MigrationRunner(db).migrate().size(), "only V9 and V10 remain");
        assertEquals(UPGRADE_HISTORY, history());
        assertEquals(BACKUP_SHIPMENTS, TestDatabase.queryLong(db, "SELECT COUNT(*) FROM shipments"));
        assertSeeded();
        assertEquals(v3Rows, rows(V3_ROWS_SQL), "V9 never changes routes 1-10 or their legs");
        assertEquals(List.of(), new MigrationRunner(db).migrate());
    }

    @Test
    void upgradeAddsMissingRouteLegsUniqueKey() throws Exception {
        restoreBackup();
        TestDatabase.execute(db, "ALTER TABLE route_legs ADD KEY idx_route_legs_route (route_id)",
                "ALTER TABLE route_legs DROP INDEX uk_route_legs_seq");
        new MigrationRunner(db).migrate();
        assertEquals(UPGRADE_HISTORY, history());
        assertSeeded();
    }

    @Test
    void duplicateLegsMakeV2FailAndLaterRunsRefuse() throws Exception {
        restoreBackup();
        TestDatabase.execute(db, "ALTER TABLE route_legs ADD KEY idx_route_legs_route (route_id)",
                "ALTER TABLE route_legs DROP INDEX uk_route_legs_seq",
                "INSERT INTO route_legs (route_id, sequence_no, mode, distance_km, rate_per_tonne_km, speed_kmh, "
                        + "buffer_hours, emission_factor) VALUES (1, 1, 'ROAD', 10, 1, 10, 0, 0.1)");
        DataAccessException first = assertThrows(DataAccessException.class, () -> new MigrationRunner(db).migrate());
        assertTrue(first.getMessage().contains("V2"), first.getMessage());
        assertEquals(List.of("V1 BASELINE ok", "V2 SQL failed"), history());
        DataAccessException again = assertThrows(DataAccessException.class, () -> new MigrationRunner(db).migrate());
        assertTrue(again.getMessage().contains("failed earlier"), again.getMessage());
    }

    @Test
    void mismatchedExistingSchemaIsRefusedWithoutChanges() throws Exception {
        restoreBackup();
        TestDatabase.execute(db, "ALTER TABLE shipments MODIFY weight_kg DECIMAL(10,2) NOT NULL");
        DataAccessException ex = assertThrows(DataAccessException.class, () -> new MigrationRunner(db).migrate());
        assertTrue(ex.getMessage().contains(
                "shipments.weight_kg: expected decimal(12,3) NOT NULL, found decimal(10,2) NOT NULL"), ex.getMessage());
        assertFalse(TestDatabase.tableExists(db, MigrationRunner.HISTORY_TABLE));
    }

    @Test
    void missingForeignKeyIsRefused() throws Exception {
        restoreBackup();
        TestDatabase.execute(db, "ALTER TABLE reports DROP FOREIGN KEY fk_reports_shipment");
        DataAccessException ex = assertThrows(DataAccessException.class, () -> new MigrationRunner(db).migrate());
        assertTrue(ex.getMessage().contains("missing foreign key reports.shipment_id -> shipments.shipment_id"),
                ex.getMessage());
        assertFalse(TestDatabase.tableExists(db, MigrationRunner.HISTORY_TABLE));
    }

    @Test
    void partialExistingSchemaIsRefused() throws Exception {
        restoreBackup();
        TestDatabase.execute(db, "DROP TABLE reports");
        DataAccessException ex = assertThrows(DataAccessException.class, () -> new MigrationRunner(db).migrate());
        assertTrue(ex.getMessage().contains("has 8 of 9 baseline tables (missing: reports)"), ex.getMessage());
        assertFalse(TestDatabase.tableExists(db, MigrationRunner.HISTORY_TABLE));
    }

    @Test
    void failingScriptIsRecordedAndBlocksLaterRuns() throws Exception {
        List<String> scripts = new ArrayList<>(MigrationRunner.SCRIPTS);
        scripts.add("/db/test-migration/V999__intentionally_failing.sql");
        DataAccessException ex = assertThrows(DataAccessException.class, () -> new MigrationRunner(db, scripts).migrate());
        assertTrue(ex.getMessage().contains("V999") && ex.getMessage().contains("statement 2 of 2"), ex.getMessage());
        assertEquals(withFailed999(), history());
        assertTrue(TestDatabase.tableExists(db, "migration_partial_marker"), "DDL before the failure is not rolled back");
        String error = TestDatabase.queryString(db, "SELECT error_message FROM schema_migrations WHERE version = 999");
        assertTrue(error.contains("statement 2 of 2"), error);
        DataAccessException again = assertThrows(DataAccessException.class,
                () -> new MigrationRunner(db, scripts).migrate());
        assertTrue(again.getMessage().contains("V999") && again.getMessage().contains("failed earlier"),
                again.getMessage());
        assertThrows(DataAccessException.class, () -> new MigrationRunner(db).appliedVersion(),
                "the application's schema check also refuses a recorded failure");
    }

    @Test
    void editedAppliedScriptIsRefused() throws Exception {
        new MigrationRunner(db).migrate();
        TestDatabase.execute(db, "UPDATE schema_migrations SET checksum = REPEAT('0', 64) WHERE version = 3");
        DataAccessException ex = assertThrows(DataAccessException.class, () -> new MigrationRunner(db).migrate());
        assertTrue(ex.getMessage().contains("checksum mismatch"), ex.getMessage());
    }

    @Test
    void unknownRecordedVersionIsRefused() throws Exception {
        new MigrationRunner(db).migrate();
        TestDatabase.execute(db, "INSERT INTO schema_migrations (version, description, script, checksum, type, success) "
                + "VALUES (99, 'future', 'V99__future.sql', REPEAT('a', 64), 'SQL', TRUE)");
        DataAccessException ex = assertThrows(DataAccessException.class, () -> new MigrationRunner(db).migrate());
        assertTrue(ex.getMessage().contains("does not know"), ex.getMessage());
    }

    @Test
    void seedRerunWithBookingsKeepsForeignKeysIntact() throws Exception {
        new MigrationRunner(db).migrate();
        TestDatabase.execute(db, "INSERT INTO shipments (shipment_id, user_id, source, destination, cargo_type, "
                        + "weight_kg, dispatch_date, status) VALUES (900, 1, 'Mumbai', 'Delhi', 'GENERAL', 5000, "
                        + "'2026-11-02', 'BOOKED')",
                "INSERT INTO bookings (booking_id, shipment_id, route_id, route_name, route_mode, shipment_source, "
                        + "shipment_destination, cargo_type, weight_kg, dispatch_date, transport_cost, handling_cost, "
                        + "transfer_cost, waiting_cost, strategy_key, strategy_name, total_cost, recommendation_reason) "
                        + "VALUES (900, 900, 2, 'Rail direct', 'RAIL', 'Mumbai', 'Delhi', 'GENERAL', 5000, '2026-11-02', "
                        + "20300.00, 2500.00, 0, 0, 'BALANCED', 'Balanced', 22800.00, 'test')");
        String seed = MigrationRunner.load(MigrationRunner.SCRIPTS.get(2)).sql;
        TestDatabase.execute(db, MigrationRunner.splitStatements(seed).toArray(new String[0]));
        TestDatabase.execute(db, MigrationRunner.splitStatements(seed).toArray(new String[0]));
        assertEquals(1, TestDatabase.queryLong(db, "SELECT COUNT(*) FROM bookings WHERE booking_id = 900 AND route_id = 2"));
        assertSeeded();
    }

    @Test
    void v10AddsANullablePreferenceThatOnlyAcceptsTheFourStrategies() throws Exception {
        restoreBackup();
        new MigrationRunner(db).migrate();
        assertEquals(UPGRADE_HISTORY, history());
        assertEquals(BACKUP_SHIPMENTS, TestDatabase.queryLong(db, "SELECT COUNT(*) FROM shipments"));
        assertEquals(BACKUP_SHIPMENTS, TestDatabase.queryLong(db,
                "SELECT COUNT(*) FROM shipments WHERE optimisation_preference IS NULL"), "existing rows keep no preference");
        assertEquals("YES", TestDatabase.queryString(db, "SELECT IS_NULLABLE FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shipments' AND COLUMN_NAME = 'optimisation_preference'"));
        String insert = "INSERT INTO shipments (user_id, source, destination, cargo_type, weight_kg, dispatch_date, status, "
                + "optimisation_preference) VALUES (1, 'Mumbai', 'Delhi', 'GENERAL', 5, '2026-11-02', 'DRAFT', %s)";
        for (String ok : new String[] {"NULL", "'CHEAPEST'", "'FASTEST'", "'GREENEST'", "'BALANCED'"}) {
            TestDatabase.execute(db, String.format(insert, ok));
        }
        assertThrows(SQLException.class, () -> TestDatabase.execute(db, String.format(insert, "'FANCIEST'")));
        assertThrows(SQLException.class, () -> TestDatabase.execute(db, String.format(insert, "'balanced'")),
                "the CHECK is exact");
        assertEquals(List.of(), new MigrationRunner(db).migrate(), "a second run does nothing");
    }

    @Test
    void rerunningV8AndV9AddsAndChangesNothing() throws Exception {
        new MigrationRunner(db).migrate();
        String everyRow = "SELECT CONCAT_WS('|', r.route_id, r.name, r.source, r.destination, r.mode, r.reliability, "
                + "r.capacity_kg, r.handling_charge, r.unsupported_cargo, l.leg_id, l.sequence_no, l.mode, l.distance_km, "
                + "l.rate_per_tonne_km, l.speed_kmh, l.buffer_hours, IFNULL(l.emission_factor, '-')) "
                + "FROM routes r JOIN route_legs l ON l.route_id = r.route_id ORDER BY l.leg_id";
        List<String> before = rows(everyRow);
        assertEquals(ALL_LEGS, before.size());
        for (int i = 7; i <= 8; i++) {
            String script = MigrationRunner.load(MigrationRunner.SCRIPTS.get(i)).sql;
            TestDatabase.execute(db, MigrationRunner.splitStatements(script).toArray(new String[0]));
            TestDatabase.execute(db, MigrationRunner.splitStatements(script).toArray(new String[0]));
        }
        assertEquals(before, rows(everyRow));
        assertSeeded();
        assertThrows(SQLIntegrityConstraintViolationException.class, () -> TestDatabase.execute(db,
                "INSERT INTO routes (route_id, name, source, destination, mode, capacity_kg) "
                        + "VALUES (5000, 'Road direct', 'Delhi', 'Mumbai', 'ROAD', 1000)"),
                "V8: one name per corridor");
    }
}

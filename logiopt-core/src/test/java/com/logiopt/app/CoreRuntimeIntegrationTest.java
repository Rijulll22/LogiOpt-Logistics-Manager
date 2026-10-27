package com.logiopt.app;

import com.logiopt.dao.DBConnection;
import com.logiopt.dao.MigrationRunner;
import com.logiopt.dao.TestDatabase;
import com.logiopt.service.AuthService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CoreRuntime} against the disposable test database (LOGIOPT_TEST_DB_URL, name ending in
 * _test): the read-only schema check in every state, administrator seeding from the environment,
 * the start-up action of the web gate, and shutdown. Never uses LOGIOPT_DB_URL.
 */
class CoreRuntimeIntegrationTest {

    private static final String CLASSPATH = "some\\classes;some\\lib\\*";
    private static final String ADMIN_PASSWORD = "runtime-admin-pass";

    private static DBConnection db;

    @TempDir
    Path reportsDir;

    private final Map<String, String> env = new HashMap<>();
    private final List<String> logged = new ArrayList<>();
    private final Handler capture = new Handler() {
        @Override
        public void publish(LogRecord record) {
            logged.add(record.getMessage());
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    };
    private CoreRuntime runtime;

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
        Logger.getLogger(CoreRuntime.class.getName()).addHandler(capture);
        runtime = new CoreRuntime(db, env::get, reportsDir, CLASSPATH);
    }

    @AfterEach
    void stop() {
        Logger.getLogger(CoreRuntime.class.getName()).removeHandler(capture);
        runtime.shutdown();
    }

    @Test
    void currentAfterEveryMigrationIsApplied() throws Exception {
        new MigrationRunner(db).migrate();
        SchemaStatus status = runtime.checkSchema();
        assertEquals(SchemaStatus.State.CURRENT, status.getState());
        assertEquals(10, status.getAppliedVersion());
        assertEquals(10, status.getRequiredVersion());
        assertEquals(10, runtime.requiredVersion());
        assertTrue(status.isUsable());
        assertFalse(logged.stream().anyMatch(m -> m.contains("MigrationTool")), logged.toString());
    }

    @Test
    void behindIsReportedWithVersionsAndTheCommandGoesOnlyToTheLog() throws Exception {
        new MigrationRunner(db).migrate();
        // The history now says only V1 to V3 are applied, as on a database that missed later releases.
        TestDatabase.execute(db, "DELETE FROM schema_migrations WHERE version > 3");
        SchemaStatus status = runtime.checkSchema();
        assertEquals(SchemaStatus.State.BEHIND, status.getState());
        assertEquals(3, status.getAppliedVersion());
        assertEquals(10, status.getRequiredVersion());
        assertFalse(status.isUsable());
        assertFalse(status.toString().contains("MigrationTool"), "the status carries no command");
        assertTrue(logged.stream().anyMatch(m -> m.contains("java -cp \"" + CLASSPATH + "\" com.logiopt.dao.MigrationTool "
                + "--target=main --confirm-database=" + db.getDatabaseName())), logged.toString());
        assertEquals(3, TestDatabase.queryLong(db, "SELECT COUNT(*) FROM schema_migrations"), "the check never migrates");
    }

    @Test
    void emptyDatabaseIsBehindAtVersionZero() {
        SchemaStatus status = runtime.checkSchema();
        assertEquals(SchemaStatus.State.BEHIND, status.getState());
        assertEquals(0, status.getAppliedVersion());
    }

    @Test
    void recordedFailureIsReportedWithoutAnAppliedVersion() throws Exception {
        new MigrationRunner(db).migrate();
        TestDatabase.execute(db, "UPDATE schema_migrations SET success = FALSE, error_message = 'x' WHERE version = 4");
        SchemaStatus status = runtime.checkSchema();
        assertEquals(SchemaStatus.State.FAILED, status.getState());
        assertNull(status.getAppliedVersion());
        assertFalse(status.isUsable());
        assertTrue(logged.stream().anyMatch(m -> m.contains("--status")), logged.toString());
    }

    @Test
    void unreachableDatabaseIsReported() throws Exception {
        CoreRuntime offline = new CoreRuntime(new DBConnection(
                "jdbc:mysql://127.0.0.1:1/unreachable_test?connectTimeout=1000", "nobody", "not-a-secret"),
                env::get, reportsDir, CLASSPATH);
        try {
            SchemaStatus status = offline.checkSchema();
            assertEquals(SchemaStatus.State.UNREACHABLE, status.getState());
            assertNull(status.getAppliedVersion());
            assertEquals(10, status.getRequiredVersion());
        } finally {
            offline.shutdown();
        }
    }

    @Test
    void adminIsSeededFromTheEnvironmentOnlyWhenConfigured() throws Exception {
        new MigrationRunner(db).migrate();
        assertEquals(AuthService.AdminSeedResult.SKIPPED_NOT_CONFIGURED, runtime.seedAdmin());
        env.put(AuthService.ENV_ADMIN_PASSWORD, ADMIN_PASSWORD);
        assertEquals(AuthService.AdminSeedResult.CREATED, runtime.seedAdmin());
        assertEquals(AuthService.AdminSeedResult.UNCHANGED, runtime.seedAdmin());
        assertEquals(1, TestDatabase.queryLong(db, "SELECT COUNT(*) FROM users WHERE role = 'ADMIN' AND email = '"
                + AuthService.ADMIN_EMAIL + "'"));
        assertFalse(logged.stream().anyMatch(m -> m.contains(ADMIN_PASSWORD)), "the password is never logged");
    }

    @Test
    void webGateRunsTheStartUpActionOnce() throws Exception {
        new MigrationRunner(db).migrate();
        env.put(AuthService.ENV_ADMIN_PASSWORD, ADMIN_PASSWORD);
        TestDatabase.execute(db, "INSERT INTO shipments (shipment_id, user_id, source, destination, cargo_type, weight_kg, "
                        + "dispatch_date, status) VALUES (800, 1, 'Mumbai', 'Delhi', 'GENERAL', 5, '2026-11-02', 'DRAFT')",
                "INSERT INTO reports (job_id, user_id, shipment_id, strategy_key, status) "
                        + "VALUES ('left-running', 1, 800, 'BALANCED', 'RUNNING')");
        assertTrue(runtime.services().getSchemaGate().isReady());
        assertEquals(1, TestDatabase.queryLong(db, "SELECT COUNT(*) FROM users WHERE role = 'ADMIN'"));
        assertEquals("FAILED", TestDatabase.queryString(db, "SELECT status FROM reports WHERE job_id = 'left-running'"));
    }

    @Test
    void desktopStartUpActionSeedsAndFailsInterruptedJobsWithoutThrowing() throws Exception {
        new MigrationRunner(db).migrate();
        env.put(AuthService.ENV_ADMIN_PASSWORD, ADMIN_PASSWORD);
        runtime.onSchemaReady();
        assertEquals(1, TestDatabase.queryLong(db, "SELECT COUNT(*) FROM users WHERE role = 'ADMIN'"));
        TestDatabase.dropAllTables(db);
        runtime.onSchemaReady();
        assertTrue(logged.stream().anyMatch(m -> m.contains("could not be seeded")), logged.toString());
    }

    @Test
    void simulatorStartsOnlyWithAValidIntervalAndShutdownIsIdempotent() {
        assertFalse(runtime.startSimulatorFromEnvironment(), "unset");
        env.put("LOGIOPT_SIM_INTERVAL_SECONDS", "not-a-number");
        assertFalse(runtime.startSimulatorFromEnvironment(), "invalid");
        env.put("LOGIOPT_SIM_INTERVAL_SECONDS", "3600");
        assertTrue(runtime.startSimulatorFromEnvironment());
        assertFalse(runtime.startSimulatorFromEnvironment(), "already running");
        runtime.shutdown();
        runtime.shutdown();
        assertTrue(runtime.isShutdown());
        assertTrue(runtime.services().getNotificationService().isShutdown());
        assertFalse(runtime.startSimulatorFromEnvironment(), "not after shutdown");
    }
}

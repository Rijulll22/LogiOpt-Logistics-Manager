package com.logiopt.report;

import com.logiopt.TestData;
import com.logiopt.dao.DBConnection;
import com.logiopt.dao.MigrationRunner;
import com.logiopt.dao.ReportDAO;
import com.logiopt.dao.RouteDAO;
import com.logiopt.dao.ShipmentDAO;
import com.logiopt.dao.TestDatabase;
import com.logiopt.dao.UserDAO;
import com.logiopt.exception.DataAccessException;
import com.logiopt.model.CargoType;
import com.logiopt.model.PasswordHash;
import com.logiopt.model.PlanningResult;
import com.logiopt.model.Role;
import com.logiopt.model.Shipment;
import com.logiopt.model.ShipmentStatus;
import com.logiopt.service.PasswordHasher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Report jobs persisted in the reports table of the disposable test database (LOGIOPT_TEST_DB_URL,
 * name ending in _test): status survives a restart, unfinished jobs become FAILED, and downloads
 * are owner-only, DONE-only and resolved only from the stored managed name.
 */
class ReportPersistenceIntegrationTest {

    private static final Duration WAIT = Duration.ofSeconds(10);

    private static DBConnection db;

    @TempDir
    Path reportsDir;

    private final List<ReportService> started = new ArrayList<>();
    private long alice;
    private long bob;
    private long aliceShipment;

    @BeforeAll
    static void connect() throws Exception {
        db = TestDatabase.connect();
        TestDatabase.dropAllTables(db);
        new MigrationRunner(db).migrate();
    }

    @BeforeEach
    void reset() throws Exception {
        TestDatabase.execute(db, "DELETE FROM reports", "DELETE FROM notifications", "DELETE FROM tracking",
                "DELETE FROM payments", "DELETE FROM booking_legs", "DELETE FROM bookings", "DELETE FROM shipments",
                "DELETE FROM users WHERE user_id <> 1");
        UserDAO users = new UserDAO(db);
        PasswordHash hash = new PasswordHasher(PasswordHasher.MIN_ITERATIONS, new SecureRandom()).hash("x".toCharArray());
        alice = users.insert("alice@example.test", "Alice", hash, Role.CUSTOMER).orElseThrow();
        bob = users.insert("bob@example.test", "Bob", hash, Role.CUSTOMER).orElseThrow();
        aliceShipment = new ShipmentDAO(db).insert(new Shipment(0, alice, "Mumbai", "Delhi", CargoType.GENERAL, 5000,
                "", LocalDate.of(2026, 11, 2), ShipmentStatus.DRAFT));
    }

    @AfterEach
    void stop() {
        for (ReportService s : started) {
            s.shutdown();
        }
    }

    /** A new service over the same database and directory, as after a server restart. */
    private ReportService start() {
        return start(new ReportService(reportsDir, TestData.CLOCK, 1, new ReportDAO(db)));
    }

    private ReportService start(ReportService service) {
        started.add(service);
        return service;
    }

    private PlanningResult plan(long shipmentId, long owner) throws Exception {
        Shipment s = new ShipmentDAO(db).findByIdForUser(shipmentId, owner).orElseThrow();
        return TestData.planningService().plan(s, new RouteDAO(db).findBySourceAndDestination("Mumbai", "Delhi"),
                "BALANCED");
    }

    private String doneJob(ReportService service) throws Exception {
        ReportJob job = service.submit(plan(aliceShipment, alice));
        assertTrue(service.awaitFinished(job.getJobId(), WAIT));
        assertEquals(ReportStatus.DONE, service.getJob(job.getJobId(), alice).orElseThrow().getStatus());
        return job.getJobId();
    }

    private static String status(String jobId) throws Exception {
        return TestDatabase.queryString(db, "SELECT status FROM reports WHERE job_id = '" + jobId + "'");
    }

    @Test
    void jobMetadataIsStoredAndSurvivesARestart() throws Exception {
        ReportService first = start();
        String jobId = doneJob(first);
        assertEquals("DONE", status(jobId));
        assertEquals(alice, TestDatabase.queryLong(db, "SELECT user_id FROM reports WHERE job_id = '" + jobId + "'"));
        assertEquals(1, TestDatabase.queryLong(db, "SELECT COUNT(*) FROM reports WHERE job_id = '" + jobId
                + "' AND shipment_id = " + aliceShipment + " AND strategy_key = 'BALANCED' AND file_name LIKE 'shipment-%' "
                + "AND started_at IS NOT NULL AND finished_at IS NOT NULL AND error_message IS NULL"));
        first.shutdown();

        ReportService restarted = start();
        ReportRecord after = restarted.getJob(jobId, alice).orElseThrow();
        assertEquals(ReportStatus.DONE, after.getStatus());
        ReportDownload download = restarted.download(jobId, alice).orElseThrow();
        assertTrue(Files.readString(download.getFile(), StandardCharsets.UTF_8).startsWith("LogiOpt shipment report"),
                "TXT format unchanged");
        assertEquals("logiopt-report-shipment-" + aliceShipment + "-balanced.txt", download.getDownloadName());
        assertEquals(List.of(jobId), restarted.history(alice, 10).stream().map(ReportRecord::getJobId).toList());
        assertTrue(restarted.history(bob, 10).isEmpty());
    }

    @Test
    void jobsLeftUnfinishedByAStoppedServerBecomeFailed() throws Exception {
        TestDatabase.execute(db, "INSERT INTO reports (job_id, user_id, shipment_id, strategy_key, status, started_at) "
                + "VALUES ('crashed-running', " + alice + ", " + aliceShipment + ", 'BALANCED', 'RUNNING', CURRENT_TIMESTAMP(3))",
                "INSERT INTO reports (job_id, user_id, shipment_id, strategy_key, status) VALUES ('crashed-queued', "
                        + alice + ", " + aliceShipment + ", 'CHEAPEST', 'QUEUED')");
        ReportService restarted = start();
        assertEquals(2, restarted.failInterruptedJobs(), "start-up recovery");
        assertEquals("FAILED", status("crashed-running"));
        ReportRecord shown = restarted.getJob("crashed-queued", alice).orElseThrow();
        assertEquals(ReportStatus.FAILED, shown.getStatus());
        assertEquals(ReportService.INTERRUPTED_MESSAGE, shown.getErrorMessage());
        assertTrue(restarted.download("crashed-running", alice).isEmpty());
        assertEquals(0, restarted.failInterruptedJobs());
    }

    @Test
    void shutdownRecordsUnfinishedJobsAsFailedInTheDatabase() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch never = new CountDownLatch(1);
        ReportService service = start(new ReportService(reportsDir, TestData.CLOCK, 1, new ReportDAO(db)) {
            @Override
            void writeContent(PrintWriter out, PlanningResult result) {
                started.countDown();
                try {
                    never.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("interrupted by shutdown", e);
                }
            }
        });
        ReportJob running = service.submit(plan(aliceShipment, alice));
        ReportJob queued = service.submit(plan(aliceShipment, alice));
        assertTrue(started.await(WAIT.toMillis(), TimeUnit.MILLISECONDS));
        assertEquals("RUNNING", status(running.getJobId()));
        assertEquals("QUEUED", status(queued.getJobId()));
        service.shutdown();
        assertEquals("FAILED", status(running.getJobId()));
        assertEquals("FAILED", status(queued.getJobId()));
        assertEquals(ReportService.SHUTDOWN_MESSAGE, TestDatabase.queryString(db,
                "SELECT error_message FROM reports WHERE job_id = '" + queued.getJobId() + "'"));
        try (Stream<Path> files = Files.list(reportsDir)) {
            assertEquals(0, files.count(), "the interrupted job left no partly written file");
        }
    }

    @Test
    void downloadIsOwnerOnlyAndOnlyForDoneJobs() throws Exception {
        ReportService service = start();
        String jobId = doneJob(service);
        assertTrue(service.download(jobId, alice).isPresent());
        assertTrue(service.download(jobId, bob).isEmpty(), "another user's report");
        assertTrue(service.getJob(jobId, bob).isEmpty());
        assertTrue(service.download("no-such-job", alice).isEmpty());
        assertTrue(service.download(null, alice).isEmpty());
        TestDatabase.execute(db, "INSERT INTO reports (job_id, user_id, shipment_id, strategy_key, status, error_message, "
                + "finished_at) VALUES ('failed-job', " + alice + ", " + aliceShipment + ", 'BALANCED', 'FAILED', 'x', "
                + "CURRENT_TIMESTAMP(3))");
        assertTrue(service.download("failed-job", alice).isEmpty());
        assertThrows(SQLException.class, () -> TestDatabase.execute(db, "INSERT INTO reports (job_id, user_id, "
                + "shipment_id, strategy_key, status, file_name) VALUES ('unfinished-with-file', " + alice + ", "
                + aliceShipment + ", 'BALANCED', 'RUNNING', 'shipment-1-BALANCED-20261021000000.txt')"),
                "the database itself refuses a file name on an unfinished job");
        assertThrows(DataAccessException.class, () -> new ReportDAO(db).insertQueued("foreign", bob, aliceShipment,
                "BALANCED"), "a job is recorded only for the owner's own shipment");
    }

    @Test
    void craftedStoredFileNamesAreNeverServed() throws Exception {
        ReportService service = start();
        String jobId = doneJob(service);
        String managed = TestDatabase.queryString(db, "SELECT file_name FROM reports WHERE job_id = '" + jobId + "'");
        Path outside = Files.writeString(reportsDir.getParent().resolve("outside-" + System.nanoTime() + ".txt"), "secret");
        try {
            for (String crafted : new String[] {"../" + outside.getFileName(), "..\\\\" + outside.getFileName(),
                    outside.toString().replace("\\", "\\\\"), managed + "/../../" + outside.getFileName(),
                    "subdir/" + managed, "shipment-999999999-BALANCED-20261021000000.txt", managed.toLowerCase(),
                    managed + ":hidden"}) {
                TestDatabase.execute(db, "UPDATE reports SET file_name = '" + crafted + "' WHERE job_id = '" + jobId + "'");
                assertTrue(service.download(jobId, alice).isEmpty(), "refused stored name: " + crafted);
            }
            TestDatabase.execute(db, "UPDATE reports SET file_name = '" + managed + "' WHERE job_id = '" + jobId + "'");
            assertEquals(Files.readString(reportsDir.resolve(managed)),
                    Files.readString(service.download(jobId, alice).orElseThrow().getFile()));
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    @Test
    void twoJobsCannotShareOneManagedFile() throws Exception {
        ReportService service = start();
        String first = doneJob(service);
        String second = doneJob(service);
        String name = TestDatabase.queryString(db, "SELECT file_name FROM reports WHERE job_id = '" + first + "'");
        assertThrows(SQLIntegrityConstraintViolationException.class, () -> TestDatabase.execute(db,
                "UPDATE reports SET file_name = '" + name + "' WHERE job_id = '" + second + "'"));
    }
}

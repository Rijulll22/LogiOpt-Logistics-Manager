package com.logiopt.report;

import com.logiopt.TestData;
import com.logiopt.exception.DataAccessException;
import com.logiopt.model.CargoType;
import com.logiopt.model.PlanningResult;
import com.logiopt.model.RouteEvaluation;
import com.logiopt.model.Shipment;
import com.logiopt.model.ShipmentStatus;
import com.logiopt.service.RouteEvaluator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportServiceTest {

    private static final Duration WAIT = Duration.ofSeconds(10);
    private static final int CONCURRENT_JOBS = 20;

    @TempDir
    Path tempDir;

    private final MemoryReportRepository repo = new MemoryReportRepository();
    private ReportService service;

    @AfterEach
    void stop() {
        if (service != null) {
            service.shutdown();
        }
    }

    private static PlanningResult plan(String strategy) throws Exception {
        return TestData.planningService().plan(TestData.shipment().withShipmentId(42),
                List.of(TestData.route1(), TestData.route2(), TestData.route3(), TestData.rail(5, "2.00", null, 0.9)),
                strategy);
    }

    @Test
    void reportContentMatchesEvaluationValues() throws Exception {
        service = new ReportService(tempDir, TestData.CLOCK, 2, repo);
        PlanningResult result = plan("GREENEST");
        ReportJob job = service.submit(result);
        assertTrue(job.awaitFinished(WAIT));
        assertEquals(ReportStatus.DONE, job.getStatus(), job.getErrorMessage());
        assertEquals("shipment-42-GREENEST-20261021000000.txt", job.getFileName());

        String text = Files.readString(tempDir.resolve(job.getFileName()), StandardCharsets.UTF_8);
        RouteEvaluation chosen = result.getRecommendation().getChosen();
        assertEquals(2L, chosen.getRouteId());
        assertTrue(text.contains("Recommended route: Rail direct (route 2, Rail)"));
        assertTrue(text.contains("Transport cost (INR): " + chosen.getCostBreakdown().getTransport().toPlainString()));
        assertTrue(text.contains("Total cost (INR):     " + chosen.getTotalCost().toPlainString()));
        assertTrue(text.contains("Duration (h):         " + chosen.getDurationHours()));
        assertTrue(text.contains("Estimated arrival:    " + chosen.getEstimatedArrival()));
        assertTrue(text.contains("Emissions (kg CO2e):  " + chosen.getEmissionsKg()));
        assertTrue(text.contains("22800.00") && text.contains("38.22") && text.contains("217.5"));
        assertTrue(text.contains("route 5 Rail 5: Emissions not available"));
        assertTrue(text.contains(RouteEvaluator.ESTIMATE_LABEL));
    }

    @Test
    void reportNumbersUseFixedScalesAndIncludeEligibilityMessage() throws Exception {
        service = new ReportService(tempDir, TestData.CLOCK, 1, repo);
        ReportJob job = service.submit(plan("BALANCED"));
        assertTrue(job.awaitFinished(WAIT));
        String text = Files.readString(tempDir.resolve(job.getFileName()), StandardCharsets.UTF_8);
        assertTrue(text.contains("Shipment 42: Mumbai -> Delhi, GENERAL, 5000.000 kg, dispatch 2026-11-02"), text);
        assertTrue(text.contains("Eligibility: 4 of 4 routes configured for this source and destination are eligible."),
                text);
        assertTrue(text.contains("  Duration (h):         38.22\n") || text.contains("  Duration (h):         38.22\r\n"));
        assertTrue(text.contains("  Emissions (kg CO2e):  217.50"), text);
        assertTrue(text.contains("  Reliability:          0.900"), text);
        assertTrue(text.contains("route 1 Road direct: total INR 33000.00, 30.00 h, 630.00 kg CO2e"), text);
        assertTrue(text.contains("route 5 Rail 5: total INR 17000.00, 38.22 h, not available kg CO2e"), text);
    }

    @Test
    void unknownCityPairReportStatesThatNoRoutesAreConfigured() throws Exception {
        service = new ReportService(tempDir, TestData.CLOCK, 1, repo);
        Shipment typo = new Shipment(53, 1, "Ahmedabad", "Bengalaru", CargoType.GENERAL, 3000, "",
                TestData.DISPATCH.minusYears(1), ShipmentStatus.DRAFT);
        ReportJob job = service.submit(TestData.planningService().plan(typo, TestData.dataset3(), "BALANCED"));
        assertTrue(job.awaitFinished(WAIT));
        assertEquals(ReportStatus.DONE, job.getStatus(), job.getErrorMessage());
        String text = Files.readString(tempDir.resolve(job.getFileName()), StandardCharsets.UTF_8);
        assertTrue(text.contains("Eligibility: No routes are configured for this source and destination."), text);
        assertTrue(text.contains("Recommended route: none"), text);
    }

    @Test
    void errorWhileWritingStillEndsFailed() throws Exception {
        service = new ReportService(tempDir, TestData.CLOCK, 1, repo) {
            @Override
            void writeContent(PrintWriter out, PlanningResult result) {
                throw new AssertionError("simulated non-IO failure");
            }
        };
        ReportJob job = service.submit(plan("CHEAPEST"));
        assertTrue(job.awaitFinished(WAIT));
        assertEquals(ReportStatus.FAILED, job.getStatus());
        assertEquals(ReportService.WRITE_FAILED_MESSAGE, job.getErrorMessage(), "no exception detail for users");
    }

    @Test
    void virtualMachineErrorEndsFailedAndPoolKeepsWorking() throws Exception {
        AtomicBoolean failNext = new AtomicBoolean(true);
        service = new ReportService(tempDir, TestData.CLOCK, 1, repo) {
            @Override
            void writeContent(PrintWriter out, PlanningResult result) {
                if (failNext.getAndSet(false)) {
                    throw new StackOverflowError("simulated");
                }
                super.writeContent(out, result);
            }
        };
        ReportJob failed = service.submit(plan("CHEAPEST"));
        assertTrue(failed.awaitFinished(WAIT));
        assertEquals(ReportStatus.FAILED, failed.getStatus());
        ReportJob next = service.submit(plan("CHEAPEST"));
        assertTrue(next.awaitFinished(WAIT));
        assertEquals(ReportStatus.DONE, next.getStatus(), next.getErrorMessage());
    }

    @Test
    void shutdownMarksRunningAndQueuedJobsFailed() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch never = new CountDownLatch(1);
        service = new ReportService(tempDir, TestData.CLOCK, 1, repo) {
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
        };
        ReportJob running = service.submit(plan("CHEAPEST"));
        ReportJob queued = service.submit(plan("FASTEST"));
        assertTrue(started.await(WAIT.toMillis(), TimeUnit.MILLISECONDS));
        service.shutdown();
        assertTrue(running.isFinished());
        assertTrue(queued.isFinished());
        assertEquals(ReportStatus.FAILED, running.getStatus());
        assertEquals(ReportStatus.FAILED, queued.getStatus());
        assertEquals("Report service was shut down before the report finished.", queued.getErrorMessage());
        ReportJob afterShutdown = service.submit(plan("CHEAPEST"));
        assertEquals(ReportStatus.FAILED, afterShutdown.getStatus());
        service = null;
    }

    @Test
    void unwritableTargetGivesFailedWithoutException() throws Exception {
        Path blocker = Files.writeString(tempDir.resolve("not-a-directory"), "x");
        service = new ReportService(blocker.resolve("reports"), TestData.CLOCK, 1, repo);
        PlanningResult result = plan("CHEAPEST");
        ReportJob job = assertDoesNotThrow(() -> service.submit(result));
        assertTrue(job.awaitFinished(WAIT));
        assertEquals(ReportStatus.FAILED, job.getStatus());
        assertNotNull(job.getErrorMessage());
    }

    @Test
    void concurrentSubmissionsWriteUniqueFiles() throws Exception {
        service = new ReportService(tempDir, TestData.CLOCK, 4, repo);
        PlanningResult result = plan("BALANCED");
        List<ReportJob> jobs = new ArrayList<>();
        List<Thread> submitters = new ArrayList<>();
        for (int i = 0; i < CONCURRENT_JOBS; i++) {
            Thread t = new Thread(() -> {
                try {
                    ReportJob job = service.submit(result);
                    synchronized (jobs) {
                        jobs.add(job);
                    }
                } catch (DataAccessException e) {
                    throw new IllegalStateException(e);
                }
            });
            submitters.add(t);
            t.start();
        }
        for (Thread t : submitters) {
            t.join();
        }
        Set<String> names = new HashSet<>();
        for (ReportJob job : jobs) {
            assertTrue(job.awaitFinished(WAIT));
            assertEquals(ReportStatus.DONE, job.getStatus(), job.getErrorMessage());
            names.add(job.getFileName());
        }
        assertEquals(CONCURRENT_JOBS, names.size());
        try (Stream<Path> files = Files.list(tempDir)) {
            assertEquals(CONCURRENT_JOBS, files.count());
        }
    }

    @Test
    void persistedStatusIsTheSourceOfTruthAndOwnerOnly() throws Exception {
        service = new ReportService(tempDir, TestData.CLOCK, 1, repo);
        PlanningResult result = plan("BALANCED");
        long owner = result.getShipment().getUserId();
        ReportJob job = service.submit(result);
        assertTrue(service.awaitFinished(job.getJobId(), WAIT));
        ReportRecord stored = service.getJob(job.getJobId(), owner).orElseThrow();
        assertEquals(ReportStatus.DONE, stored.getStatus());
        assertEquals(job.getFileName(), repo.get(job.getJobId()).getFileName());
        assertTrue(service.getJob(job.getJobId(), owner + 1).isEmpty(), "another user's job is not found");
        assertTrue(service.getJob("no-such-job", owner).isEmpty());
        assertEquals(List.of(job.getJobId()), service.history(owner, 10).stream().map(ReportRecord::getJobId).toList());
        assertTrue(service.history(owner + 1, 10).isEmpty());
    }

    @Test
    void unfinishedJobWithoutAWorkerIsRecordedAndShownAsFailed() throws Exception {
        service = new ReportService(tempDir, TestData.CLOCK, 1, repo);
        repo.put(new ReportRecord("left-running", 7, 42, "BALANCED", ReportStatus.RUNNING, LocalDateTime.now(),
                LocalDateTime.now(), null, null, null));
        repo.put(new ReportRecord("left-queued", 7, 42, "BALANCED", ReportStatus.QUEUED, LocalDateTime.now(),
                null, null, null, null));
        ReportRecord shown = service.getJob("left-running", 7).orElseThrow();
        assertEquals(ReportStatus.FAILED, shown.getStatus());
        assertEquals(ReportService.INTERRUPTED_MESSAGE, shown.getErrorMessage());
        assertEquals(ReportStatus.FAILED, repo.get("left-running").getStatus(), "the FAILED status is saved");
        assertEquals(1, service.failInterruptedJobs(), "start-up recovery records the other one");
        assertEquals(ReportStatus.FAILED, repo.get("left-queued").getStatus());
        assertEquals(0, service.failInterruptedJobs());
        assertTrue(service.download("left-running", 7).isEmpty());
    }

    @Test
    void inFlightJobIsNotMistakenForAnAbandonedOne() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        service = new ReportService(tempDir, TestData.CLOCK, 1, repo) {
            @Override
            void writeContent(PrintWriter out, PlanningResult result) {
                started.countDown();
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                super.writeContent(out, result);
            }
        };
        PlanningResult result = plan("CHEAPEST");
        ReportJob job = service.submit(result);
        assertTrue(started.await(WAIT.toMillis(), TimeUnit.MILLISECONDS));
        long owner = result.getShipment().getUserId();
        assertEquals(ReportStatus.RUNNING, service.getJob(job.getJobId(), owner).orElseThrow().getStatus());
        assertEquals(0, service.failInterruptedJobs());
        release.countDown();
        assertTrue(service.awaitFinished(job.getJobId(), WAIT));
        assertEquals(ReportStatus.DONE, service.getJob(job.getJobId(), owner).orElseThrow().getStatus());
    }

    @Test
    void downloadResolvesOnlyTheStoredManagedNameInsideTheReportsDirectory() throws Exception {
        Path reports = Files.createDirectories(tempDir.resolve("reports"));
        Files.writeString(tempDir.resolve("secret.txt"), "outside the reports directory");
        Files.createDirectories(reports.resolve("shipment-9-BALANCED-20261021000000-7.txt"));
        String good = "shipment-9-BALANCED-20261021000000.txt";
        Files.writeString(reports.resolve(good), "report text");
        service = new ReportService(reports, TestData.CLOCK, 1, repo);
        String[] crafted = {"../secret.txt", "..\\secret.txt", tempDir.resolve("secret.txt").toString(),
            good + "/../../secret.txt", "secret.txt", "shipment-9-BALANCED-20261021000000.TXT", good + ":stream",
            good + "\u0000", "shipment-9-BALANCED-20261021000001.txt", "shipment-9-BALANCED-20261021000000-7.txt",
            "", "CON.txt", "shipment-9-balanced-20261021000000.txt"};
        for (int i = 0; i < crafted.length; i++) {
            repo.put(done("crafted-" + i, crafted[i]));
            assertTrue(service.download("crafted-" + i, 7).isEmpty(), "refused: " + crafted[i]);
        }
        repo.put(done("good", good));
        ReportDownload download = service.download("good", 7).orElseThrow();
        assertEquals(reports.resolve(good).toRealPath(), download.getFile());
        assertEquals("logiopt-report-shipment-9-balanced.txt", download.getDownloadName());
        assertTrue(service.download("good", 8).isEmpty(), "another user's report");
        repo.put(new ReportRecord("failed", 7, 9, "BALANCED", ReportStatus.FAILED, LocalDateTime.now(), null,
                LocalDateTime.now(), good, "failed"));
        assertTrue(service.download("failed", 7).isEmpty(), "a FAILED job is never served, even with a file name");
        Files.delete(reports.resolve(good));
        assertTrue(service.download("good", 7).isEmpty(), "missing file");
    }

    @Test
    void downloadNameUsesOnlyDigitsAndLetters() {
        assertEquals("logiopt-report-shipment-3-greenest.txt", ReportService.downloadName(done("x", null)));
        ReportRecord odd = new ReportRecord("y", 7, 3, "a\"b/../c;d", ReportStatus.DONE, LocalDateTime.now(), null,
                LocalDateTime.now(), null, null);
        assertEquals("logiopt-report-shipment-3-abcd.txt", ReportService.downloadName(odd));
    }

    @Test
    void doneThatCannotBeSavedDeletesTheFileAndEndsFailed() throws Exception {
        service = new ReportService(tempDir, TestData.CLOCK, 1, repo) {
            @Override
            void writeContent(PrintWriter out, PlanningResult result) {
                super.writeContent(out, result);
                repo.failWrites = true;
            }
        };
        PlanningResult result = plan("FASTEST");
        ReportJob job = service.submit(result);
        assertTrue(service.awaitFinished(job.getJobId(), WAIT));
        assertEquals(ReportStatus.FAILED, job.getStatus());
        assertEquals(ReportService.NOT_SAVED_MESSAGE, job.getErrorMessage());
        try (Stream<Path> files = Files.list(tempDir)) {
            assertEquals(0, files.count(), "no file is left without a DONE row");
        }
        repo.failWrites = false;
        assertEquals(ReportStatus.FAILED, service.getJob(job.getJobId(), result.getShipment().getUserId())
                .orElseThrow().getStatus(), "the row left RUNNING is shown and recorded as FAILED");
    }

    @Test
    void submitFailsWhenTheJobCannotBeRecorded() throws Exception {
        service = new ReportService(tempDir, TestData.CLOCK, 1, repo);
        repo.failWrites = true;
        PlanningResult result = plan("CHEAPEST");
        assertThrows(DataAccessException.class, () -> service.submit(result));
        try (Stream<Path> files = Files.list(tempDir)) {
            assertEquals(0, files.count());
        }
    }

    private static ReportRecord done(String jobId, String fileName) {
        boolean x = "x".equals(jobId);
        return new ReportRecord(jobId, 7, x ? 3 : 9, x ? "GREENEST" : "BALANCED", ReportStatus.DONE,
                LocalDateTime.now(), null, LocalDateTime.now(), fileName, null);
    }
}

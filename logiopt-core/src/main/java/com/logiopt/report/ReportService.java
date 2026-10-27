package com.logiopt.report;

import com.logiopt.exception.DataAccessException;
import com.logiopt.model.CostBreakdown;
import com.logiopt.model.ExcludedRoute;
import com.logiopt.model.PlanningResult;
import com.logiopt.model.Recommendation;
import com.logiopt.model.RejectedRoute;
import com.logiopt.model.RouteEvaluation;
import com.logiopt.model.ScoreBreakdown;
import com.logiopt.model.Shipment;
import com.logiopt.service.RouteEvaluator;
import com.logiopt.util.Numbers;
import com.logiopt.util.Strings;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Writes TXT reports of planning results on a small pool of named daemon threads. The report
 * uses the same RouteEvaluation values the comparison page shows; nothing is recalculated.
 *
 * <p>Every job is a row of the reports table ({@link ReportRepository}); the database is the source
 * of truth for its status. The in-memory map holds only the jobs this server is working on. A job
 * that is QUEUED or RUNNING in the database but not in flight here (the server stopped before it
 * finished) is recorded and shown as FAILED. Files are written under managed names inside the
 * reports directory, and downloads resolve only those names (see {@link #download}).
 */
public class ReportService {

    /** System property naming the reports directory. */
    public static final String DIR_PROPERTY = "logiopt.reports.dir";
    /** Environment variable naming the reports directory. */
    public static final String DIR_ENV = "LOGIOPT_REPORTS_DIR";
    /** Default reports directory, relative to the working directory. */
    public static final String DEFAULT_DIR = "reports";
    /** Default number of worker threads. */
    public static final int DEFAULT_THREADS = 2;
    /** Message of a job that was unfinished when the server stopped. */
    public static final String INTERRUPTED_MESSAGE =
            "The server stopped before this report finished. Please create it again.";
    /** Message of a job that was unfinished when the service shut down. */
    static final String SHUTDOWN_MESSAGE = "Report service was shut down before the report finished.";
    /** Message of a job whose file could not be written; the cause is only in the server log. */
    public static final String WRITE_FAILED_MESSAGE = "The report could not be written. Please try again later.";
    /** Message when the job's status could not be saved. */
    static final String NOT_SAVED_MESSAGE = "The report status could not be saved.";
    /** Names this service writes: shipment-{id}-{STRATEGY}-{yyyyMMddHHmmss}[-n].txt. Nothing else is served. */
    static final Pattern MANAGED_NAME =
            Pattern.compile("shipment-[0-9]{1,19}-[A-Z]{1,20}-[0-9]{14}(-[0-9]{1,4})?\\.txt");

    private static final Logger LOG = Logger.getLogger(ReportService.class.getName());
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final int MAX_NAME_ATTEMPTS = 1000;
    private static final long SHUTDOWN_WAIT_SECONDS = 5;

    private final Path reportsDir;
    private final Clock clock;
    private final ReportRepository repository;
    private final ExecutorService executor;
    private final Map<String, ReportJob> jobs = new ConcurrentHashMap<>();

    /**
     * Creates the service.
     *
     * @param reportsDir target directory (created on demand)
     * @param clock      clock for file timestamps
     * @param threads    worker thread count (&gt;= 1)
     * @param repository persistent job store
     */
    public ReportService(Path reportsDir, Clock clock, int threads, ReportRepository repository) {
        if (reportsDir == null || clock == null || threads < 1 || repository == null) {
            throw new IllegalArgumentException(
                    "Reports directory, clock, a positive thread count and a repository are required");
        }
        this.reportsDir = reportsDir;
        this.clock = clock;
        this.repository = repository;
        AtomicInteger counter = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread t = new Thread(runnable, "logiopt-report-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
        this.executor = Executors.newFixedThreadPool(threads, factory);
    }

    /**
     * Resolves the reports directory: system property, then environment variable, then "reports".
     *
     * @return reports directory
     */
    public static Path configuredDirectory() {
        String value = System.getProperty(DIR_PROPERTY);
        if (Strings.isBlank(value)) {
            value = System.getenv(DIR_ENV);
        }
        return Paths.get(Strings.isBlank(value) ? DEFAULT_DIR : value.trim());
    }

    /**
     * Records a QUEUED job and returns immediately; the report is written in the background. The
     * job belongs to the shipment's owner.
     *
     * @param result planning result to report
     * @return the in-flight job
     * @throws DataAccessException if the job could not be recorded (nothing is written then)
     */
    public ReportJob submit(PlanningResult result) throws DataAccessException {
        if (result == null) {
            throw new IllegalArgumentException("Planning result must not be null");
        }
        ReportJob job = new ReportJob(UUID.randomUUID().toString(), result.getShipment().getShipmentId(),
                result.getShipment().getUserId(), result.getRecommendation().getStrategyKey());
        // In flight before the row exists, so a reader never takes the new row for an abandoned one.
        jobs.put(job.getJobId(), job);
        try {
            repository.insertQueued(job.getJobId(), job.getOwnerUserId(), job.getShipmentId(), job.getStrategyKey());
        } catch (DataAccessException | RuntimeException e) {
            jobs.remove(job.getJobId());
            throw e;
        }
        try {
            executor.execute(() -> run(job, result));
        } catch (RejectedExecutionException e) {
            LOG.log(Level.WARNING, "Report job rejected: {0}", job.getJobId());
            finishFailed(job, "Report service is shut down.");
        }
        return job;
    }

    /**
     * Looks up a job of the given user in the database. Another user's job and an unknown id both
     * give empty. A job that is unfinished in the database but not in flight on this server is
     * recorded as FAILED first, so it is never shown as running forever.
     *
     * @param jobId  job id
     * @param userId signed-in user id
     * @return the user's job, or empty
     * @throws DataAccessException on database failure
     */
    public Optional<ReportRecord> getJob(String jobId, long userId) throws DataAccessException {
        if (jobId == null) {
            return Optional.empty();
        }
        Optional<ReportRecord> found = repository.findForOwner(jobId, userId);
        return found.isEmpty() ? found : Optional.of(resolve(found.get(), userId));
    }

    /**
     * Lists the user's jobs, newest first, with the same FAILED rule as {@link #getJob}.
     *
     * @param userId signed-in user id
     * @param limit  maximum number of jobs
     * @return jobs
     * @throws DataAccessException on database failure
     */
    public List<ReportRecord> history(long userId, int limit) throws DataAccessException {
        List<ReportRecord> result = new ArrayList<>();
        for (ReportRecord r : repository.findByOwner(userId, limit)) {
            result.add(resolve(r, userId));
        }
        return result;
    }

    /**
     * Unfinished and not in flight here means no server is working on the job any more: a job is
     * put in flight before its row exists and leaves only after its final status is saved.
     */
    private ReportRecord resolve(ReportRecord record, long userId) throws DataAccessException {
        if (record.isFinished() || jobs.containsKey(record.getJobId())) {
            return record;
        }
        if (repository.markFailed(record.getJobId(), INTERRUPTED_MESSAGE)) {
            LOG.log(Level.WARNING, "Report job {0} was unfinished with no worker; recorded as FAILED",
                    record.getJobId());
        }
        return repository.findForOwner(record.getJobId(), userId).orElse(record.asFailed(INTERRUPTED_MESSAGE));
    }

    /**
     * Records every unfinished job that is not in flight on this server as FAILED. Run once at
     * start-up, after the schema check passes.
     *
     * @return number of jobs recorded as FAILED
     * @throws DataAccessException on database failure
     */
    public int failInterruptedJobs() throws DataAccessException {
        int failed = 0;
        for (String jobId : repository.findUnfinishedJobIds()) {
            if (!jobs.containsKey(jobId) && repository.markFailed(jobId, INTERRUPTED_MESSAGE)) {
                failed++;
            }
        }
        if (failed > 0) {
            LOG.log(Level.WARNING, "{0} report job(s) left unfinished by an earlier run were recorded as FAILED",
                    failed);
        }
        return failed;
    }

    /**
     * Waits until an in-flight job of this server has finished and its final status is saved.
     *
     * @param jobId   job id
     * @param timeout maximum wait
     * @return true if the job is not (or no longer) in flight
     * @throws InterruptedException if interrupted while waiting
     */
    public boolean awaitFinished(String jobId, Duration timeout) throws InterruptedException {
        ReportJob job = jobId == null ? null : jobs.get(jobId);
        return job == null || job.awaitFinished(timeout);
    }

    /**
     * Finds the file of a DONE report of the user. The file is located only through the managed
     * name stored in the database: the name must match {@link #MANAGED_NAME}, and both the
     * normalized and the real path must be a regular file directly inside the reports directory.
     * Anything else (missing, someone else's, unfinished, failed, a crafted name, a link or a
     * missing file) gives empty, so the caller answers with its usual 404.
     *
     * @param jobId  job id from the request
     * @param userId signed-in user id
     * @return the file and a safe download name, or empty
     * @throws DataAccessException on database failure
     */
    public Optional<ReportDownload> download(String jobId, long userId) throws DataAccessException {
        Optional<ReportRecord> record = getJob(jobId, userId);
        if (record.isEmpty() || !record.get().isDone()) {
            return Optional.empty();
        }
        Path file = resolveManagedFile(record.get().getFileName());
        if (file == null) {
            LOG.log(Level.WARNING, "Report job {0} has no usable stored file; download refused",
                    record.get().getJobId());
            return Optional.empty();
        }
        return Optional.of(new ReportDownload(file, downloadName(record.get())));
    }

    /**
     * @param name stored file name
     * @return the real path of the managed file directly inside the reports directory, or null
     */
    Path resolveManagedFile(String name) {
        if (name == null || !MANAGED_NAME.matcher(name).matches()) {
            return null;
        }
        try {
            Path dir = reportsDir.toAbsolutePath().normalize();
            Path candidate = dir.resolve(name).normalize();
            if (!dir.equals(candidate.getParent()) || !Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)) {
                return null;
            }
            Path real = candidate.toRealPath();
            return dir.toRealPath().equals(real.getParent()) ? real : null;
        } catch (IOException | InvalidPathException | SecurityException e) {
            LOG.log(Level.FINE, "Report file could not be resolved", e);
            return null;
        }
    }

    /**
     * @param record job
     * @return a download name built only from the job's numeric shipment id and strategy letters
     */
    static String downloadName(ReportRecord record) {
        String strategy = String.valueOf(record.getStrategyKey()).replaceAll("[^A-Za-z]", "")
                .toLowerCase(Locale.ROOT);
        return "logiopt-report-shipment-" + record.getShipmentId() + (strategy.isEmpty() ? "" : "-" + strategy)
                + ".txt";
    }

    /** @return reports directory */
    public Path getReportsDir() {
        return reportsDir;
    }

    /**
     * Stops accepting jobs and waits briefly for running ones. Every job that has not finished by
     * then is recorded as FAILED, so no job is left QUEUED or RUNNING.
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        } finally {
            for (ReportJob job : jobs.values()) {
                if (!job.isFinished()) {
                    LOG.log(Level.WARNING, "Report job {0} recorded as FAILED at shutdown", job.getJobId());
                    finishFailed(job, SHUTDOWN_MESSAGE);
                }
            }
        }
    }

    private void run(ReportJob job, PlanningResult result) {
        try {
            repository.markRunning(job.getJobId());
        } catch (DataAccessException e) {
            LOG.log(Level.WARNING, "Report job " + job.getJobId() + " could not be marked RUNNING", e);
            finishFailed(job, NOT_SAVED_MESSAGE);
            return;
        }
        job.markRunning();
        try {
            Files.createDirectories(reportsDir);
            String base = "shipment-" + job.getShipmentId() + "-" + job.getStrategyKey() + "-"
                    + LocalDateTime.now(clock).format(STAMP);
            finishDone(job, write(base, result));
        } catch (Throwable t) {
            LOG.log(Level.WARNING, "Report job " + job.getJobId() + " failed", t);
            finishFailed(job, WRITE_FAILED_MESSAGE);
            if (t instanceof VirtualMachineError) {
                throw (VirtualMachineError) t;
            }
        }
    }

    /** Saves DONE first, then publishes it; a file whose DONE status was not saved is deleted. */
    private void finishDone(ReportJob job, String name) {
        boolean saved;
        try {
            saved = repository.markDone(job.getJobId(), name);
        } catch (DataAccessException e) {
            LOG.log(Level.WARNING, "Report job " + job.getJobId() + " could not be marked DONE", e);
            deleteQuietly(name);
            finishFailed(job, NOT_SAVED_MESSAGE);
            return;
        }
        if (saved) {
            job.markDone(name);
        } else {
            LOG.log(Level.WARNING, "Report job {0} had already ended; deleting {1}", new Object[] {job.getJobId(), name});
            deleteQuietly(name);
            job.markFailed(SHUTDOWN_MESSAGE);
        }
        jobs.remove(job.getJobId());
    }

    /** Saves FAILED (best effort), then publishes it and takes the job out of flight. */
    private void finishFailed(ReportJob job, String message) {
        try {
            repository.markFailed(job.getJobId(), message);
        } catch (DataAccessException e) {
            LOG.log(Level.WARNING, "Report job " + job.getJobId() + " could not be marked FAILED", e);
        }
        job.markFailed(message);
        jobs.remove(job.getJobId());
    }

    private void deleteQuietly(String name) {
        try {
            Files.deleteIfExists(reportsDir.resolve(name));
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Could not delete unrecorded report file " + name, e);
        }
    }

    private String write(String base, PlanningResult result) throws IOException {
        for (int attempt = 1; attempt <= MAX_NAME_ATTEMPTS; attempt++) {
            String name = attempt == 1 ? base + ".txt" : base + "-" + attempt + ".txt";
            Path file = reportsDir.resolve(name);
            BufferedWriter writer;
            try {
                writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE);
            } catch (FileAlreadyExistsException e) {
                LOG.log(Level.FINE, "Report name taken, trying next: {0}", name);
                continue;
            }
            try (PrintWriter out = new PrintWriter(writer)) {
                writeContent(out, result);
                if (out.checkError()) {
                    throw new IOException("Error while writing " + name);
                }
            } catch (IOException | RuntimeException | Error e) {
                // A partly written file never stays behind under a managed name.
                deleteQuietly(name);
                throw e;
            }
            return name;
        }
        throw new IOException("No free report file name for " + base);
    }

    /**
     * Writes the report text. Money, duration and emissions use 2 decimals, weight 3 (the stored
     * scale), reliability 3 (the stored scale) and Balanced scores 6. Package-private so tests can
     * simulate a failing writer.
     *
     * @param out    target
     * @param result planning result
     */
    void writeContent(PrintWriter out, PlanningResult result) {
        Shipment s = result.getShipment();
        Recommendation rec = result.getRecommendation();
        out.println("LogiOpt shipment report");
        out.println(RouteEvaluator.ESTIMATE_LABEL);
        out.println();
        out.println("Shipment " + s.getShipmentId() + ": " + s.getSource() + " -> " + s.getDestination()
                + ", " + s.getCargoType() + ", " + Numbers.format(s.getWeightKg(), Numbers.WEIGHT_SCALE)
                + " kg, dispatch " + s.getDispatchDate());
        out.println("Strategy: " + rec.getStrategyName() + " (" + rec.getStrategyKey() + ")");
        out.println("Eligibility: " + result.getEligibility().getMessage());
        out.println();
        RouteEvaluation chosen = rec.getChosen();
        if (chosen == null) {
            out.println("Recommended route: none");
        } else {
            CostBreakdown cost = chosen.getCostBreakdown();
            out.println("Recommended route: " + chosen.getRouteName() + " (route " + chosen.getRouteId() + ", "
                    + chosen.getMode().getLabel() + ")");
            out.println("  Transport cost (INR): " + cost.getTransport().toPlainString());
            out.println("  Handling cost (INR):  " + cost.getHandling().toPlainString());
            out.println("  Transfer cost (INR):  " + cost.getTransfer().toPlainString());
            out.println("  Waiting cost (INR):   " + cost.getWaiting().toPlainString());
            out.println("  Total cost (INR):     " + cost.getTotal().toPlainString());
            out.println("  Duration (h):         " + fixed(chosen.getDurationHours(), Numbers.METRIC_SCALE));
            out.println("  Estimated arrival:    " + orNotAvailable(chosen.getEstimatedArrival()));
            out.println("  Emissions (kg CO2e):  " + fixed(chosen.getEmissionsKg(), Numbers.METRIC_SCALE));
            out.println("  Reliability:          " + fixed(chosen.getReliability(), Numbers.RELIABILITY_SCALE));
            ScoreBreakdown score = rec.getChosenScore();
            if (score != null) {
                out.println(String.format(Locale.ROOT, "  Balanced score:       %.6f (cost %.6f + time %.6f"
                                + " + emissions %.6f + reliability %.6f)", score.getTotalScore(),
                        score.getWeightedCost(), score.getWeightedTime(), score.getWeightedEmissions(),
                        score.getWeightedReliability()));
            }
        }
        out.println("Reason: " + rec.getReason());
        out.println();
        out.println("All eligible routes (" + result.getEvaluations().size() + "):");
        for (RouteEvaluation e : result.getEvaluations()) {
            out.println("  route " + e.getRouteId() + " " + e.getRouteName() + ": total INR "
                    + e.getTotalCost().toPlainString() + ", " + fixed(e.getDurationHours(), Numbers.METRIC_SCALE)
                    + " h, " + fixed(e.getEmissionsKg(), Numbers.METRIC_SCALE) + " kg CO2e");
        }
        out.println("Not considered by this strategy:");
        if (rec.getExcluded().isEmpty()) {
            out.println("  none");
        }
        for (ExcludedRoute x : rec.getExcluded()) {
            out.println("  route " + x.getEvaluation().getRouteId() + " " + x.getEvaluation().getRouteName() + ": "
                    + x.getReason());
        }
        out.println("Rejected by eligibility:");
        if (result.getRejections().isEmpty()) {
            out.println("  none");
        }
        for (RejectedRoute r : result.getRejections()) {
            out.println("  route " + r.getRoute().getRouteId() + " " + r.getRoute().getName() + ": " + r.getReason());
        }
        out.println();
        out.println(RouteEvaluator.ESTIMATE_LABEL);
    }

    private static String orNotAvailable(Object value) {
        return value == null ? "not available" : String.valueOf(value);
    }

    private static String fixed(Double value, int scale) {
        return value == null ? "not available" : Numbers.format(value, scale);
    }
}

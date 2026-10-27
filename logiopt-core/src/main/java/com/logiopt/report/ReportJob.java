package com.logiopt.report;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Thread-safe in-memory status of one report job while this server works on it. The persisted
 * status (the source of truth for pages) is a {@link ReportRecord}.
 */
public final class ReportJob {

    private final String jobId;
    private final long shipmentId;
    private final long ownerUserId;
    private final String strategyKey;
    private final CountDownLatch finished = new CountDownLatch(1);
    private volatile ReportStatus status = ReportStatus.QUEUED;
    private volatile String fileName;
    private volatile String errorMessage;

    /**
     * Creates a queued job.
     *
     * @param jobId       unique job id
     * @param shipmentId  shipment id
     * @param ownerUserId user who owns the shipment; only they may see the job
     * @param strategyKey strategy key
     */
    public ReportJob(String jobId, long shipmentId, long ownerUserId, String strategyKey) {
        this.jobId = jobId;
        this.shipmentId = shipmentId;
        this.ownerUserId = ownerUserId;
        this.strategyKey = strategyKey;
    }

    /** @return id of the user who owns the job */
    public long getOwnerUserId() {
        return ownerUserId;
    }

    synchronized void markRunning() {
        if (!isFinished()) {
            status = ReportStatus.RUNNING;
        }
    }

    /** First terminal state wins; later calls are ignored. @return true if this call finished the job */
    synchronized boolean markDone(String writtenFileName) {
        if (isFinished()) {
            return false;
        }
        fileName = writtenFileName;
        status = ReportStatus.DONE;
        finished.countDown();
        return true;
    }

    /** First terminal state wins; later calls are ignored. @return true if this call finished the job */
    synchronized boolean markFailed(String message) {
        if (isFinished()) {
            return false;
        }
        errorMessage = message;
        status = ReportStatus.FAILED;
        finished.countDown();
        return true;
    }

    /**
     * Waits until the job is DONE or FAILED.
     *
     * @param timeout maximum wait
     * @return true if the job finished within the timeout
     * @throws InterruptedException if interrupted while waiting
     */
    public boolean awaitFinished(Duration timeout) throws InterruptedException {
        return finished.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    /** @return job id */
    public String getJobId() {
        return jobId;
    }

    /** @return shipment id */
    public long getShipmentId() {
        return shipmentId;
    }

    /** @return strategy key */
    public String getStrategyKey() {
        return strategyKey;
    }

    /** @return current status */
    public ReportStatus getStatus() {
        return status;
    }

    /** @return true once DONE or FAILED */
    public boolean isFinished() {
        ReportStatus s = status;
        return s == ReportStatus.DONE || s == ReportStatus.FAILED;
    }

    /** @return written file name (no directory), or null */
    public String getFileName() {
        return fileName;
    }

    /** @return failure message, or null */
    public String getErrorMessage() {
        return errorMessage;
    }
}

package com.logiopt.report;

import java.time.LocalDateTime;

/**
 * Immutable persisted state of one report job (a row of the reports table). The database is the
 * source of truth for the status; see {@link ReportService#getJob(String, long)}.
 */
public final class ReportRecord {

    private final String jobId;
    private final long ownerUserId;
    private final long shipmentId;
    private final String strategyKey;
    private final ReportStatus status;
    private final LocalDateTime createdAt;
    private final LocalDateTime startedAt;
    private final LocalDateTime finishedAt;
    private final String fileName;
    private final String errorMessage;

    /**
     * Creates the record.
     *
     * @param jobId        public job id
     * @param ownerUserId  owner
     * @param shipmentId   shipment
     * @param strategyKey  strategy key
     * @param status       status
     * @param createdAt    submission time
     * @param startedAt    start time, or null
     * @param finishedAt   end time (DONE or FAILED), or null
     * @param fileName     managed file name (DONE only), or null
     * @param errorMessage failure message (FAILED only), or null
     */
    public ReportRecord(String jobId, long ownerUserId, long shipmentId, String strategyKey, ReportStatus status,
                        LocalDateTime createdAt, LocalDateTime startedAt, LocalDateTime finishedAt, String fileName,
                        String errorMessage) {
        this.jobId = jobId;
        this.ownerUserId = ownerUserId;
        this.shipmentId = shipmentId;
        this.strategyKey = strategyKey;
        this.status = status;
        this.createdAt = createdAt;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.fileName = fileName;
        this.errorMessage = errorMessage;
    }

    /**
     * @param message failure message
     * @return a FAILED copy of this record (used for a job no running server is working on)
     */
    ReportRecord asFailed(String message) {
        return new ReportRecord(jobId, ownerUserId, shipmentId, strategyKey, ReportStatus.FAILED, createdAt, startedAt,
                finishedAt, null, message);
    }

    /** @return public job id */
    public String getJobId() {
        return jobId;
    }

    /** @return owner user id */
    public long getOwnerUserId() {
        return ownerUserId;
    }

    /** @return shipment id */
    public long getShipmentId() {
        return shipmentId;
    }

    /** @return strategy key */
    public String getStrategyKey() {
        return strategyKey;
    }

    /** @return status */
    public ReportStatus getStatus() {
        return status;
    }

    /** @return true once DONE or FAILED */
    public boolean isFinished() {
        return status == ReportStatus.DONE || status == ReportStatus.FAILED;
    }

    /** @return true if the report can be downloaded */
    public boolean isDone() {
        return status == ReportStatus.DONE;
    }

    /** @return submission time */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /** @return start time, or null */
    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    /** @return end time, or null */
    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    /** @return managed file name inside the reports directory (never shown to users), or null */
    String getFileName() {
        return fileName;
    }

    /** @return failure message, or null */
    public String getErrorMessage() {
        return errorMessage;
    }
}

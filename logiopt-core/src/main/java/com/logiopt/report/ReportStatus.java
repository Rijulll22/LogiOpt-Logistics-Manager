package com.logiopt.report;

/**
 * Lifecycle of a background report job.
 */
public enum ReportStatus {
    /** Submitted, waiting for a worker thread. */
    QUEUED,
    /** Being written. */
    RUNNING,
    /** File written successfully. */
    DONE,
    /** Writing failed; see the error message. */
    FAILED
}

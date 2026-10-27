package com.logiopt.report;

import com.logiopt.exception.DataAccessException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory {@link ReportRepository} for the non-database report tests, with the same "first
 * terminal state wins" rule as ReportDAO. {@link #failWrites} simulates an unavailable database.
 */
final class MemoryReportRepository implements ReportRepository {

    private final Map<String, ReportRecord> rows = new LinkedHashMap<>();
    volatile boolean failWrites;

    @Override
    public synchronized void insertQueued(String jobId, long ownerUserId, long shipmentId, String strategyKey)
            throws DataAccessException {
        check();
        rows.put(jobId, new ReportRecord(jobId, ownerUserId, shipmentId, strategyKey, ReportStatus.QUEUED,
                LocalDateTime.now(), null, null, null, null));
    }

    @Override
    public synchronized boolean markRunning(String jobId) throws DataAccessException {
        check();
        ReportRecord r = rows.get(jobId);
        if (r == null || r.getStatus() != ReportStatus.QUEUED) {
            return false;
        }
        rows.put(jobId, copy(r, ReportStatus.RUNNING, null, null));
        return true;
    }

    @Override
    public synchronized boolean markDone(String jobId, String fileName) throws DataAccessException {
        check();
        ReportRecord r = rows.get(jobId);
        if (r == null || r.isFinished()) {
            return false;
        }
        rows.put(jobId, copy(r, ReportStatus.DONE, fileName, null));
        return true;
    }

    @Override
    public synchronized boolean markFailed(String jobId, String message) throws DataAccessException {
        check();
        ReportRecord r = rows.get(jobId);
        if (r == null || r.isFinished()) {
            return false;
        }
        rows.put(jobId, copy(r, ReportStatus.FAILED, null, message));
        return true;
    }

    @Override
    public synchronized Optional<ReportRecord> findForOwner(String jobId, long ownerUserId) {
        ReportRecord r = rows.get(jobId);
        return r != null && r.getOwnerUserId() == ownerUserId ? Optional.of(r) : Optional.empty();
    }

    @Override
    public synchronized List<ReportRecord> findByOwner(long ownerUserId, int limit) {
        List<ReportRecord> result = new ArrayList<>();
        for (ReportRecord r : rows.values()) {
            if (r.getOwnerUserId() == ownerUserId) {
                result.add(0, r);
            }
        }
        result.sort(Comparator.comparing(ReportRecord::getCreatedAt).reversed());
        return result.subList(0, Math.min(limit, result.size()));
    }

    @Override
    public synchronized List<String> findUnfinishedJobIds() {
        List<String> ids = new ArrayList<>();
        for (ReportRecord r : rows.values()) {
            if (!r.isFinished()) {
                ids.add(r.getJobId());
            }
        }
        return ids;
    }

    /** Stores a row as it might be found in the database (for crafted or abandoned rows). */
    synchronized void put(ReportRecord record) {
        rows.put(record.getJobId(), record);
    }

    synchronized ReportRecord get(String jobId) {
        return rows.get(jobId);
    }

    private void check() throws DataAccessException {
        if (failWrites) {
            throw new DataAccessException("simulated database failure");
        }
    }

    private static ReportRecord copy(ReportRecord r, ReportStatus status, String fileName, String error) {
        boolean finished = status == ReportStatus.DONE || status == ReportStatus.FAILED;
        return new ReportRecord(r.getJobId(), r.getOwnerUserId(), r.getShipmentId(), r.getStrategyKey(), status,
                r.getCreatedAt(), status == ReportStatus.RUNNING ? LocalDateTime.now() : r.getStartedAt(),
                finished ? LocalDateTime.now() : null, fileName, error);
    }
}

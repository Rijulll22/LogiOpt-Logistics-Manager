package com.logiopt.report;

import com.logiopt.exception.DataAccessException;

import java.util.List;
import java.util.Optional;

/**
 * Persistent store of report jobs (implemented by {@code com.logiopt.dao.ReportDAO}). Status
 * changes follow "first terminal state wins": once a job is DONE or FAILED, later changes are
 * ignored and reported as false.
 */
public interface ReportRepository {

    /**
     * Inserts a QUEUED job for a shipment of the given owner.
     *
     * @param jobId       new public job id
     * @param ownerUserId owner
     * @param shipmentId  owner's shipment
     * @param strategyKey strategy key
     * @throws DataAccessException on database failure, or if the shipment is not the owner's
     */
    void insertQueued(String jobId, long ownerUserId, long shipmentId, String strategyKey) throws DataAccessException;

    /**
     * @param jobId job id
     * @return true if a QUEUED job became RUNNING
     * @throws DataAccessException on database failure
     */
    boolean markRunning(String jobId) throws DataAccessException;

    /**
     * @param jobId    job id
     * @param fileName managed file name of the written report
     * @return true if an unfinished job became DONE
     * @throws DataAccessException on database failure
     */
    boolean markDone(String jobId, String fileName) throws DataAccessException;

    /**
     * @param jobId   job id
     * @param message failure message
     * @return true if an unfinished job became FAILED
     * @throws DataAccessException on database failure
     */
    boolean markFailed(String jobId, String message) throws DataAccessException;

    /**
     * @param jobId       job id
     * @param ownerUserId signed-in user
     * @return the user's job, or empty if missing or someone else's
     * @throws DataAccessException on database failure
     */
    Optional<ReportRecord> findForOwner(String jobId, long ownerUserId) throws DataAccessException;

    /**
     * @param ownerUserId signed-in user
     * @param limit       maximum number of rows
     * @return the user's jobs, newest first
     * @throws DataAccessException on database failure
     */
    List<ReportRecord> findByOwner(long ownerUserId, int limit) throws DataAccessException;

    /**
     * @return ids of all QUEUED or RUNNING jobs
     * @throws DataAccessException on database failure
     */
    List<String> findUnfinishedJobIds() throws DataAccessException;
}

package com.logiopt.dao;

import com.logiopt.exception.DataAccessException;
import com.logiopt.report.ReportRecord;
import com.logiopt.report.ReportRepository;
import com.logiopt.report.ReportStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC access to the reports table (V7). Every status change is a single UPDATE guarded by the
 * current status, so the first terminal state wins even with concurrent writers. Owner checks use
 * reports.user_id; another user's job and a missing one give the same empty result.
 */
public class ReportDAO implements ReportRepository {

    private static final String COLUMNS = "job_id, user_id, shipment_id, strategy_key, status, created_at, "
            + "started_at, finished_at, file_name, error_message";
    private static final String UNFINISHED = "status IN ('QUEUED', 'RUNNING')";
    private static final int MAX_ERROR = 500;

    private final DBConnection db;

    /**
     * Creates the DAO.
     *
     * @param db connection factory
     */
    public ReportDAO(DBConnection db) {
        this.db = db;
    }

    @Override
    public void insertQueued(String jobId, long ownerUserId, long shipmentId, String strategyKey)
            throws DataAccessException {
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement("INSERT INTO reports (job_id, user_id, shipment_id, "
                     + "strategy_key, status) SELECT ?, user_id, shipment_id, ?, 'QUEUED' FROM shipments "
                     + "WHERE shipment_id = ? AND user_id = ?")) {
            ps.setString(1, jobId);
            ps.setString(2, strategyKey);
            ps.setLong(3, shipmentId);
            ps.setLong(4, ownerUserId);
            if (ps.executeUpdate() != 1) {
                throw new DataAccessException("Shipment " + shipmentId + " of user " + ownerUserId + " not found");
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to record report job " + jobId, e);
        }
    }

    @Override
    public boolean markRunning(String jobId) throws DataAccessException {
        return update("UPDATE reports SET status = 'RUNNING', started_at = CURRENT_TIMESTAMP(3) "
                + "WHERE job_id = ? AND status = 'QUEUED'", jobId);
    }

    @Override
    public boolean markDone(String jobId, String fileName) throws DataAccessException {
        return update("UPDATE reports SET status = 'DONE', file_name = ?, finished_at = CURRENT_TIMESTAMP(3) "
                + "WHERE job_id = ? AND " + UNFINISHED, fileName, jobId);
    }

    @Override
    public boolean markFailed(String jobId, String message) throws DataAccessException {
        String text = message == null || message.isBlank() ? "Report failed." : message;
        if (text.length() > MAX_ERROR) {
            text = text.substring(0, MAX_ERROR - 1) + "…";
        }
        return update("UPDATE reports SET status = 'FAILED', error_message = ?, finished_at = CURRENT_TIMESTAMP(3) "
                + "WHERE job_id = ? AND " + UNFINISHED, text, jobId);
    }

    @Override
    public Optional<ReportRecord> findForOwner(String jobId, long ownerUserId) throws DataAccessException {
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT " + COLUMNS
                     + " FROM reports WHERE job_id = ? AND user_id = ?")) {
            ps.setString(1, jobId);
            ps.setLong(2, ownerUserId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load report job", e);
        }
    }

    @Override
    public List<ReportRecord> findByOwner(long ownerUserId, int limit) throws DataAccessException {
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT " + COLUMNS + " FROM reports WHERE user_id = ? "
                     + "ORDER BY created_at DESC, report_id DESC LIMIT ?")) {
            ps.setLong(1, ownerUserId);
            ps.setInt(2, Math.max(0, limit));
            List<ReportRecord> result = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(map(rs));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to list report jobs for user " + ownerUserId, e);
        }
    }

    @Override
    public List<String> findUnfinishedJobIds() throws DataAccessException {
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT job_id FROM reports WHERE " + UNFINISHED);
             ResultSet rs = ps.executeQuery()) {
            List<String> ids = new ArrayList<>();
            while (rs.next()) {
                ids.add(rs.getString(1));
            }
            return ids;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to list unfinished report jobs", e);
        }
    }

    private boolean update(String sql, String... params) throws DataAccessException {
        try (Connection con = db.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setString(i + 1, params[i]);
            }
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to update report job", e);
        }
    }

    private static ReportRecord map(ResultSet rs) throws SQLException {
        return new ReportRecord(rs.getString("job_id"), rs.getLong("user_id"), rs.getLong("shipment_id"),
                rs.getString("strategy_key"), ReportStatus.valueOf(rs.getString("status")),
                toLocal(rs.getTimestamp("created_at")), toLocal(rs.getTimestamp("started_at")),
                toLocal(rs.getTimestamp("finished_at")), rs.getString("file_name"), rs.getString("error_message"));
    }

    private static LocalDateTime toLocal(Timestamp t) {
        return t == null ? null : t.toLocalDateTime();
    }
}

package com.logiopt.dao;

import com.logiopt.exception.DataAccessException;
import com.logiopt.model.BookingStatus;
import com.logiopt.model.Milestone;
import com.logiopt.model.TrackingEvent;
import com.logiopt.model.TrackingSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * JDBC access to tracking events. {@link #recordNext} runs in one transaction with the usual lock
 * order (shipment row, then booking row), re-checks under lock that the booking is CONFIRMED and that
 * the latest milestone is still the one the caller saw, and inserts the next one. Together with
 * uk_tracking_booking_milestone this makes a repeated or concurrent advance a harmless no-op.
 */
public class TrackingDAO {

    /** Result of {@link #recordNext}. */
    public enum RecordOutcome {
        /** Event inserted. */
        RECORDED,
        /** No such booking. */
        NOT_FOUND,
        /** The booking is not CONFIRMED. */
        NOT_CONFIRMED,
        /** The latest milestone is no longer the expected one (already advanced). */
        STALE
    }

    private static final String COLUMNS = "tracking_id, booking_id, shipment_id, status, description, source, "
            + "recorded_by_user_id, event_time";

    private final DBConnection db;

    /**
     * Creates the DAO.
     *
     * @param db connection factory
     */
    public TrackingDAO(DBConnection db) {
        this.db = db;
    }

    /**
     * Records the next milestone atomically if the booking is CONFIRMED and its latest milestone is
     * still {@code expectedCurrent}. The order rule itself is the caller's; this only applies it.
     *
     * @param bookingId         booking id
     * @param expectedCurrent   latest milestone the caller saw (null if none)
     * @param next              milestone to record
     * @param description       description
     * @param source            source
     * @param recordedByUserId  administrator id for ADMIN events, otherwise null
     * @param completesShipment true to set the shipment to COMPLETED (DELIVERED)
     * @return outcome
     * @throws DataAccessException on database failure (after rollback)
     */
    public RecordOutcome recordNext(long bookingId, Milestone expectedCurrent, Milestone next, String description,
                                    TrackingSource source, Long recordedByUserId, boolean completesShipment)
            throws DataAccessException {
        try (Connection con = db.getConnection()) {
            con.setAutoCommit(false);
            try {
                Long shipmentId = shipmentOf(con, bookingId);
                if (shipmentId == null) {
                    con.rollback();
                    return RecordOutcome.NOT_FOUND;
                }
                lockShipment(con, shipmentId);
                String[] booking = BookingDAO.lockBooking(con, bookingId);
                if (booking == null) {
                    con.rollback();
                    return RecordOutcome.NOT_FOUND;
                }
                if (!BookingStatus.CONFIRMED.name().equals(booking[0])) {
                    con.rollback();
                    return RecordOutcome.NOT_CONFIRMED;
                }
                if (latest(con, bookingId) != expectedCurrent) {
                    con.rollback();
                    return RecordOutcome.STALE;
                }
                insert(con, bookingId, shipmentId, next, description, source, recordedByUserId);
                if (completesShipment) {
                    try (PreparedStatement ps = con.prepareStatement(
                            "UPDATE shipments SET status = 'COMPLETED' WHERE shipment_id = ?")) {
                        ps.setLong(1, shipmentId);
                        ps.executeUpdate();
                    }
                }
                con.commit();
                return RecordOutcome.RECORDED;
            } catch (SQLIntegrityConstraintViolationException e) {
                con.rollback();
                if (e.getErrorCode() == UserDAO.ER_DUP_ENTRY) {
                    return RecordOutcome.STALE;
                }
                throw e;
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to record tracking for booking " + bookingId, e);
        }
    }

    /**
     * Inserts an event on the caller's connection and transaction (used for the BOOKED event inside
     * the payment transaction).
     *
     * @param con              open connection inside a transaction
     * @param bookingId        booking id
     * @param shipmentId       shipment id
     * @param milestone        milestone
     * @param description      description
     * @param source           source
     * @param recordedByUserId administrator id, or null
     * @throws SQLException on failure (including a duplicate milestone)
     */
    static void insert(Connection con, long bookingId, long shipmentId, Milestone milestone, String description,
                       TrackingSource source, Long recordedByUserId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("INSERT INTO tracking (booking_id, shipment_id, status, "
                + "description, source, recorded_by_user_id) VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setLong(1, bookingId);
            ps.setLong(2, shipmentId);
            ps.setString(3, milestone.name());
            ps.setString(4, description);
            ps.setString(5, source.name());
            if (recordedByUserId == null) {
                ps.setNull(6, Types.BIGINT);
            } else {
                ps.setLong(6, recordedByUserId);
            }
            ps.executeUpdate();
        }
    }

    /**
     * Lists a booking's events in the order they were recorded. Callers must have checked access.
     *
     * @param bookingId booking id
     * @return events, oldest first
     * @throws DataAccessException on database failure
     */
    public List<TrackingEvent> findByBooking(long bookingId) throws DataAccessException {
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT " + COLUMNS
                     + " FROM tracking WHERE booking_id = ? ORDER BY event_time, tracking_id")) {
            ps.setLong(1, bookingId);
            List<TrackingEvent> events = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long by = rs.getLong("recorded_by_user_id");
                    Long recordedBy = rs.wasNull() ? null : by;
                    Timestamp time = rs.getTimestamp("event_time");
                    events.add(new TrackingEvent(rs.getLong("tracking_id"), rs.getLong("booking_id"),
                            rs.getLong("shipment_id"), Milestone.valueOf(rs.getString("status")),
                            rs.getString("description"), TrackingSource.valueOf(rs.getString("source")),
                            recordedBy, time.toLocalDateTime()));
                }
            }
            return events;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load tracking of booking " + bookingId, e);
        }
    }

    private static Long shipmentOf(Connection con, long bookingId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT shipment_id FROM bookings WHERE booking_id = ?")) {
            ps.setLong(1, bookingId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : null;
            }
        }
    }

    private static void lockShipment(Connection con, long shipmentId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT status FROM shipments WHERE shipment_id = ? FOR UPDATE")) {
            ps.setLong(1, shipmentId);
            ps.executeQuery().close();
        }
    }

    private static Milestone latest(Connection con, long bookingId) throws SQLException {
        Milestone latest = null;
        try (PreparedStatement ps = con.prepareStatement("SELECT status FROM tracking WHERE booking_id = ?")) {
            ps.setLong(1, bookingId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Milestone m = Milestone.valueOf(rs.getString(1));
                    if (latest == null || m.ordinal() > latest.ordinal()) {
                        latest = m;
                    }
                }
            }
        }
        return latest;
    }
}

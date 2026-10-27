package com.logiopt.dao;

import com.logiopt.exception.DataAccessException;
import com.logiopt.model.BookingStatus;
import com.logiopt.model.Milestone;
import com.logiopt.model.Payment;
import com.logiopt.model.PaymentMethod;
import com.logiopt.model.PaymentStatus;
import com.logiopt.model.TrackingSource;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * JDBC access to simulated payment attempts. {@link #recordAttempt} runs in one transaction with the
 * same lock order as BookingDAO (shipment row, then booking row): it inserts a PENDING attempt with
 * the next attempt number and the booking's snapshot total, moves it to PAID or FAILED, and on PAID
 * confirms the booking.
 */
public class PaymentDAO {

    /** Result of {@link #recordAttempt}. */
    public enum AttemptOutcome {
        /** Attempt recorded with the requested final state. */
        RECORDED,
        /** No such booking for this user. */
        NOT_FOUND,
        /** The booking is not in the state that allows payment. */
        WRONG_STATE
    }

    /** Outcome plus the attempt (null unless RECORDED). */
    public static final class AttemptResult {
        private final AttemptOutcome outcome;
        private final Payment payment;

        AttemptResult(AttemptOutcome outcome, Payment payment) {
            this.outcome = outcome;
            this.payment = payment;
        }

        /** @return outcome */
        public AttemptOutcome getOutcome() {
            return outcome;
        }

        /** @return the recorded attempt, or null */
        public Payment getPayment() {
            return payment;
        }
    }

    private static final String COLUMNS = "payment_id, booking_id, attempt_no, amount, method, status, created_at, "
            + "paid_at, failed_at";

    private final DBConnection db;

    /**
     * Creates the DAO.
     *
     * @param db connection factory
     */
    public PaymentDAO(DBConnection db) {
        this.db = db;
    }

    /**
     * Records one simulated attempt atomically.
     *
     * @param bookingId      booking id
     * @param ownerUserId    signed-in user (must own the booking)
     * @param method         method label
     * @param result         PAID or FAILED (the attempt is created PENDING first)
     * @param requiredStatus  booking state that allows paying
     * @param statusOnPaid    booking state after a PAID attempt
     * @param milestoneOnPaid tracking milestone recorded (source SYSTEM) in the same transaction when PAID,
     *                        or null for none
     * @param milestoneText   description of that milestone
     * @return outcome and the attempt
     * @throws DataAccessException on database failure (after rollback)
     */
    public AttemptResult recordAttempt(long bookingId, long ownerUserId, PaymentMethod method, PaymentStatus result,
                                       BookingStatus requiredStatus, BookingStatus statusOnPaid,
                                       Milestone milestoneOnPaid, String milestoneText) throws DataAccessException {
        try (Connection con = db.getConnection()) {
            con.setAutoCommit(false);
            try {
                Long shipmentId = BookingDAO.shipmentOfOwnedBooking(con, bookingId, ownerUserId);
                if (shipmentId == null || BookingDAO.lockShipment(con, shipmentId, ownerUserId) == null) {
                    con.rollback();
                    return new AttemptResult(AttemptOutcome.NOT_FOUND, null);
                }
                String[] booking = BookingDAO.lockBooking(con, bookingId);
                if (booking == null) {
                    con.rollback();
                    return new AttemptResult(AttemptOutcome.NOT_FOUND, null);
                }
                if (!requiredStatus.name().equals(booking[0])) {
                    con.rollback();
                    return new AttemptResult(AttemptOutcome.WRONG_STATE, null);
                }
                long paymentId = insertPending(con, bookingId, nextAttempt(con, bookingId), new BigDecimal(booking[2]),
                        method);
                String timestamp = result == PaymentStatus.PAID ? "paid_at" : "failed_at";
                try (PreparedStatement ps = con.prepareStatement("UPDATE payments SET status = ?, " + timestamp
                        + " = CURRENT_TIMESTAMP WHERE payment_id = ? AND status = 'PENDING'")) {
                    ps.setString(1, result.name());
                    ps.setLong(2, paymentId);
                    ps.executeUpdate();
                }
                if (result == PaymentStatus.PAID) {
                    BookingDAO.setBookingStatus(con, bookingId, statusOnPaid);
                    if (milestoneOnPaid != null) {
                        TrackingDAO.insert(con, bookingId, shipmentId, milestoneOnPaid, milestoneText,
                                TrackingSource.SYSTEM, null);
                    }
                }
                Payment payment = find(con, paymentId);
                con.commit();
                return new AttemptResult(AttemptOutcome.RECORDED, payment);
            } catch (SQLIntegrityConstraintViolationException e) {
                con.rollback();
                if (e.getErrorCode() == UserDAO.ER_DUP_ENTRY) {
                    return new AttemptResult(AttemptOutcome.WRONG_STATE, null);
                }
                throw e;
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to record payment for booking " + bookingId, e);
        }
    }

    /**
     * Lists a booking's attempts in order. Callers must have checked access to the booking.
     *
     * @param bookingId booking id
     * @return attempts, first attempt first
     * @throws DataAccessException on database failure
     */
    public List<Payment> findByBooking(long bookingId) throws DataAccessException {
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT " + COLUMNS
                     + " FROM payments WHERE booking_id = ? ORDER BY attempt_no")) {
            ps.setLong(1, bookingId);
            List<Payment> payments = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    payments.add(map(rs));
                }
            }
            return payments;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load payments of booking " + bookingId, e);
        }
    }

    private static int nextAttempt(Connection con, long bookingId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT COALESCE(MAX(attempt_no), 0) + 1 FROM payments WHERE booking_id = ?")) {
            ps.setLong(1, bookingId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private static long insertPending(Connection con, long bookingId, int attemptNo, BigDecimal amount,
                                      PaymentMethod method) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("INSERT INTO payments (booking_id, attempt_no, amount, "
                + "method, status) VALUES (?, ?, ?, ?, 'PENDING')", Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, bookingId);
            ps.setInt(2, attemptNo);
            ps.setBigDecimal(3, amount);
            ps.setString(4, method.name());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("No generated key returned for payment");
                }
                return keys.getLong(1);
            }
        }
    }

    private static Payment find(Connection con, long paymentId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT " + COLUMNS + " FROM payments WHERE payment_id = ?")) {
            ps.setLong(1, paymentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("Payment " + paymentId + " vanished inside its transaction");
                }
                return map(rs);
            }
        }
    }

    private static Payment map(ResultSet rs) throws SQLException {
        return new Payment(rs.getLong("payment_id"), rs.getLong("booking_id"), rs.getInt("attempt_no"),
                rs.getBigDecimal("amount"), PaymentMethod.valueOf(rs.getString("method")),
                PaymentStatus.valueOf(rs.getString("status")), toLocal(rs.getTimestamp("created_at")),
                toLocal(rs.getTimestamp("paid_at")), toLocal(rs.getTimestamp("failed_at")));
    }

    private static LocalDateTime toLocal(Timestamp t) {
        return t == null ? null : t.toLocalDateTime();
    }
}

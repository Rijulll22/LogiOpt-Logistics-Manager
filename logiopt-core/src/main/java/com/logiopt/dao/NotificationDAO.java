package com.logiopt.dao;

import com.logiopt.exception.DataAccessException;
import com.logiopt.model.Notification;
import com.logiopt.model.NotificationType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * JDBC access to in-app notifications. Every query is scoped to the recipient, so one user can
 * never read or change another user's notifications.
 */
public class NotificationDAO {

    private static final String COLUMNS = "notification_id, user_id, type, booking_id, message, is_read, created_at, "
            + "read_at";
    private static final int MAX_MESSAGE = 500;

    private final DBConnection db;

    /**
     * Creates the DAO.
     *
     * @param db connection factory
     */
    public NotificationDAO(DBConnection db) {
        this.db = db;
    }

    /**
     * Inserts a notification unless one with the same (recipient, type, booking, event key) exists.
     *
     * @param userId    recipient
     * @param type      type
     * @param bookingId related booking
     * @param eventKey  event identity within the booking, for example "attempt-2" or "PICKED_UP"
     * @param message   text
     * @return true if inserted, false if it already existed
     * @throws DataAccessException on any other database failure
     */
    public boolean insert(long userId, NotificationType type, long bookingId, String eventKey, String message)
            throws DataAccessException {
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement("INSERT INTO notifications (user_id, type, booking_id, "
                     + "event_key, message) VALUES (?, ?, ?, ?, ?)")) {
            ps.setLong(1, userId);
            ps.setString(2, type.name());
            ps.setLong(3, bookingId);
            ps.setString(4, eventKey);
            ps.setString(5, message.length() > MAX_MESSAGE ? message.substring(0, MAX_MESSAGE - 1) + "…" : message);
            ps.executeUpdate();
            return true;
        } catch (SQLIntegrityConstraintViolationException e) {
            if (e.getErrorCode() == UserDAO.ER_DUP_ENTRY) {
                return false;
            }
            throw new DataAccessException("Failed to insert notification", e);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to insert notification", e);
        }
    }

    /**
     * @param userId recipient
     * @return the recipient's notifications, newest first
     * @throws DataAccessException on database failure
     */
    public List<Notification> findByUser(long userId) throws DataAccessException {
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT " + COLUMNS
                     + " FROM notifications WHERE user_id = ? ORDER BY created_at DESC, notification_id DESC")) {
            ps.setLong(1, userId);
            List<Notification> result = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Timestamp readAt = rs.getTimestamp("read_at");
                    result.add(new Notification(rs.getLong("notification_id"), rs.getLong("user_id"),
                            NotificationType.valueOf(rs.getString("type")), rs.getLong("booking_id"),
                            rs.getString("message"), rs.getBoolean("is_read"),
                            rs.getTimestamp("created_at").toLocalDateTime(),
                            readAt == null ? null : readAt.toLocalDateTime()));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load notifications", e);
        }
    }

    /**
     * @param userId recipient
     * @return number of unread notifications
     * @throws DataAccessException on database failure
     */
    public int countUnread(long userId) throws DataAccessException {
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT COUNT(*) FROM notifications WHERE user_id = ? AND is_read = FALSE")) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to count notifications", e);
        }
    }

    /**
     * Marks one of the recipient's notifications as read (already read stays unchanged).
     *
     * @param notificationId notification id
     * @param userId         recipient
     * @return false if it does not exist or belongs to someone else
     * @throws DataAccessException on database failure
     */
    public boolean markRead(long notificationId, long userId) throws DataAccessException {
        try (Connection con = db.getConnection()) {
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT COUNT(*) FROM notifications WHERE notification_id = ? AND user_id = ?")) {
                ps.setLong(1, notificationId);
                ps.setLong(2, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next() || rs.getInt(1) == 0) {
                        return false;
                    }
                }
            }
            try (PreparedStatement ps = con.prepareStatement("UPDATE notifications SET is_read = TRUE, "
                    + "read_at = CURRENT_TIMESTAMP WHERE notification_id = ? AND user_id = ? AND is_read = FALSE")) {
                ps.setLong(1, notificationId);
                ps.setLong(2, userId);
                ps.executeUpdate();
            }
            return true;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to mark notification " + notificationId, e);
        }
    }

    /**
     * Marks all of the recipient's unread notifications as read.
     *
     * @param userId recipient
     * @return number marked
     * @throws DataAccessException on database failure
     */
    public int markAllRead(long userId) throws DataAccessException {
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement("UPDATE notifications SET is_read = TRUE, "
                     + "read_at = CURRENT_TIMESTAMP WHERE user_id = ? AND is_read = FALSE")) {
            ps.setLong(1, userId);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to mark notifications", e);
        }
    }
}

package com.logiopt.dao;

import com.logiopt.exception.DataAccessException;
import com.logiopt.model.BookingStatus;
import com.logiopt.model.Milestone;
import com.logiopt.model.Role;
import com.logiopt.model.ShipmentStatus;
import com.logiopt.model.TrackingSummary;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only aggregate queries for the dashboards. Per-user queries filter on the owner in SQL
 * (shipments.user_id), so a user's counts never include anyone else's rows.
 */
public class DashboardDAO {

    private static final String LATEST_TRACKING_SQL = "SELECT b.booking_id, b.shipment_id, b.route_name, "
            + "b.shipment_source, b.shipment_destination, t.status AS milestone, t.event_time "
            + "FROM bookings b JOIN shipments s ON s.shipment_id = b.shipment_id "
            + "LEFT JOIN tracking t ON t.tracking_id = (SELECT t2.tracking_id FROM tracking t2 "
            + "WHERE t2.booking_id = b.booking_id ORDER BY t2.event_time DESC, t2.tracking_id DESC LIMIT 1) "
            + "WHERE s.user_id = ? AND b.status = 'CONFIRMED' ORDER BY b.booking_id DESC LIMIT ?";
    private static final String AWAITING_TRACKING_SQL = "SELECT COUNT(*) FROM bookings b WHERE b.status = 'CONFIRMED' "
            + "AND NOT EXISTS (SELECT 1 FROM tracking t WHERE t.booking_id = b.booking_id AND t.status = 'DELIVERED')";

    private final DBConnection db;

    /**
     * Creates the DAO.
     *
     * @param db connection factory
     */
    public DashboardDAO(DBConnection db) {
        this.db = db;
    }

    /**
     * @param userId user id
     * @return the user's shipment count per status (every status present)
     * @throws DataAccessException on database failure
     */
    public Map<ShipmentStatus, Integer> shipmentCounts(long userId) throws DataAccessException {
        Map<ShipmentStatus, Integer> counts = zeros(ShipmentStatus.class);
        for (Map.Entry<String, Integer> e : grouped("SELECT status, COUNT(*) FROM shipments WHERE user_id = ? "
                + "GROUP BY status", userId).entrySet()) {
            counts.put(ShipmentStatus.valueOf(e.getKey()), e.getValue());
        }
        return counts;
    }

    /**
     * @param userId user id
     * @return the user's booking count per status (every status present)
     * @throws DataAccessException on database failure
     */
    public Map<BookingStatus, Integer> bookingCounts(long userId) throws DataAccessException {
        return bookingCounts(grouped("SELECT b.status, COUNT(*) FROM bookings b JOIN shipments s "
                + "ON s.shipment_id = b.shipment_id WHERE s.user_id = ? GROUP BY b.status", userId));
    }

    /**
     * @return booking count per status over all users (every status present)
     * @throws DataAccessException on database failure
     */
    public Map<BookingStatus, Integer> allBookingCounts() throws DataAccessException {
        return bookingCounts(grouped("SELECT status, COUNT(*) FROM bookings GROUP BY status"));
    }

    /**
     * Counts the accounts that can sign in. Disabled accounts, such as the seeded demo user 1, are
     * not counted.
     *
     * @return enabled user count per role (every role present)
     * @throws DataAccessException on database failure
     */
    public Map<Role, Integer> userCounts() throws DataAccessException {
        Map<Role, Integer> counts = zeros(Role.class);
        for (Map.Entry<String, Integer> e : grouped("SELECT role, COUNT(*) FROM users WHERE enabled = TRUE "
                + "GROUP BY role").entrySet()) {
            counts.put(Role.valueOf(e.getKey()), e.getValue());
        }
        return counts;
    }

    /**
     * @return CONFIRMED bookings (all users) whose tracking has no DELIVERED milestone yet
     * @throws DataAccessException on database failure
     */
    public int confirmedAwaitingTracking() throws DataAccessException {
        try (Connection con = db.getConnection(); PreparedStatement ps = con.prepareStatement(AWAITING_TRACKING_SQL);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to count bookings awaiting tracking", e);
        }
    }

    /**
     * Latest milestone of each of the user's CONFIRMED bookings, newest booking first.
     *
     * @param userId user id
     * @param limit  maximum number of bookings
     * @return summaries (milestone null if none is recorded)
     * @throws DataAccessException on database failure
     */
    public List<TrackingSummary> latestTracking(long userId, int limit) throws DataAccessException {
        try (Connection con = db.getConnection(); PreparedStatement ps = con.prepareStatement(LATEST_TRACKING_SQL)) {
            ps.setLong(1, userId);
            ps.setInt(2, Math.max(0, limit));
            List<TrackingSummary> result = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String milestone = rs.getString("milestone");
                    Timestamp at = rs.getTimestamp("event_time");
                    result.add(new TrackingSummary(rs.getLong("booking_id"), rs.getLong("shipment_id"),
                            rs.getString("route_name"), rs.getString("shipment_source"),
                            rs.getString("shipment_destination"), milestone == null ? null : Milestone.valueOf(milestone),
                            at == null ? null : at.toLocalDateTime()));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load tracking summary for user " + userId, e);
        }
    }

    private static Map<BookingStatus, Integer> bookingCounts(Map<String, Integer> grouped) {
        Map<BookingStatus, Integer> counts = zeros(BookingStatus.class);
        for (Map.Entry<String, Integer> e : grouped.entrySet()) {
            counts.put(BookingStatus.valueOf(e.getKey()), e.getValue());
        }
        return counts;
    }

    private Map<String, Integer> grouped(String sql, long... params) throws DataAccessException {
        try (Connection con = db.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setLong(i + 1, params[i]);
            }
            Map<String, Integer> result = new HashMap<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getString(1), rs.getInt(2));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load dashboard counts", e);
        }
    }

    private static <E extends Enum<E>> Map<E, Integer> zeros(Class<E> type) {
        Map<E, Integer> counts = new EnumMap<>(type);
        for (E value : type.getEnumConstants()) {
            counts.put(value, 0);
        }
        return counts;
    }
}

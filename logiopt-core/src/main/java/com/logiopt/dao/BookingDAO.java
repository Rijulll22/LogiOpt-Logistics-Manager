package com.logiopt.dao;

import com.logiopt.exception.DataAccessException;
import com.logiopt.model.Booking;
import com.logiopt.model.BookingSnapshot;
import com.logiopt.model.BookingStatus;
import com.logiopt.model.CargoType;
import com.logiopt.model.CostBreakdown;
import com.logiopt.model.RouteLeg;
import com.logiopt.model.ShipmentStatus;
import com.logiopt.model.TransportMode;
import com.logiopt.util.Numbers;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * JDBC access to bookings and their snapshot legs. Snapshot columns are written once by
 * {@link #create}; no method updates them. Every state change runs in one transaction that locks the
 * shipment row first and then the booking row (the same order everywhere, so concurrent requests
 * wait instead of deadlocking). Owner checks join shipments.user_id; another user's booking and a
 * missing one give the same result.
 */
public class BookingDAO {

    /** Result of {@link #create}. */
    public enum CreateOutcome {
        /** Booking and legs inserted, shipment status updated. */
        CREATED,
        /** No such shipment for this user. */
        SHIPMENT_NOT_FOUND,
        /** The shipment's status does not allow booking. */
        SHIPMENT_NOT_BOOKABLE,
        /** Another active booking exists (checked under lock and by uk_bookings_active_shipment). */
        ALREADY_ACTIVE
    }

    /** Result of {@link #changeStatus}. */
    public enum TransitionOutcome {
        /** Status changed. */
        CHANGED,
        /** No such booking for this user. */
        NOT_FOUND,
        /** The booking was not in the expected state. */
        WRONG_STATE
    }

    /** Outcome plus the generated booking id (0 unless CREATED). */
    public static final class CreateResult {
        private final CreateOutcome outcome;
        private final long bookingId;

        CreateResult(CreateOutcome outcome, long bookingId) {
            this.outcome = outcome;
            this.bookingId = bookingId;
        }

        /** @return outcome */
        public CreateOutcome getOutcome() {
            return outcome;
        }

        /** @return new booking id, or 0 */
        public long getBookingId() {
            return bookingId;
        }
    }

    static final String SELECT_BOOKING = "SELECT b.booking_id, b.shipment_id, s.user_id AS owner_user_id, b.status, "
            + "b.booked_at, b.confirmed_at, b.cancelled_at, b.route_id, b.route_name, b.route_mode, b.shipment_source, "
            + "b.shipment_destination, b.cargo_type, b.weight_kg, b.dispatch_date, b.transport_cost, b.handling_cost, "
            + "b.transfer_cost, b.waiting_cost, b.duration_hours, b.estimated_arrival, b.emissions_kg, b.reliability, "
            + "b.balanced_score, b.strategy_key, b.strategy_name, b.recommendation_reason, b.evaluation_notes "
            + "FROM bookings b JOIN shipments s ON s.shipment_id = b.shipment_id ";
    private static final String INSERT_SQL = "INSERT INTO bookings (shipment_id, route_id, route_name, route_mode, "
            + "shipment_source, shipment_destination, cargo_type, weight_kg, dispatch_date, transport_cost, "
            + "handling_cost, transfer_cost, waiting_cost, strategy_key, strategy_name, total_cost, duration_hours, "
            + "estimated_arrival, emissions_kg, reliability, balanced_score, recommendation_reason, evaluation_notes, "
            + "status, previous_shipment_status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String INSERT_LEG_SQL = "INSERT INTO booking_legs (booking_id, sequence_no, mode, distance_km, "
            + "rate_per_tonne_km, speed_kmh, buffer_hours, emission_factor) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String LEGS_SQL = "SELECT mode, distance_km, rate_per_tonne_km, speed_kmh, buffer_hours, "
            + "emission_factor FROM booking_legs WHERE booking_id = ? ORDER BY sequence_no";
    private static final int MAX_TEXT = 1000;

    private final DBConnection db;

    /**
     * Creates the DAO.
     *
     * @param db connection factory
     */
    public BookingDAO(DBConnection db) {
        this.db = db;
    }

    /**
     * Creates a PENDING_PAYMENT booking with its snapshot in one transaction: locks the user's
     * shipment, checks its status and that no active booking exists, inserts the booking and legs,
     * and sets the shipment status. Any failure rolls everything back.
     *
     * @param shipmentId       shipment id
     * @param ownerUserId      signed-in user (must own the shipment)
     * @param bookableStatuses shipment states that allow booking
     * @param statusAfter      shipment state after booking
     * @param snapshot         server-computed snapshot
     * @return outcome and booking id
     * @throws DataAccessException on database failure (after rollback)
     */
    public CreateResult create(long shipmentId, long ownerUserId, Set<ShipmentStatus> bookableStatuses,
                               ShipmentStatus statusAfter, BookingSnapshot snapshot) throws DataAccessException {
        try (Connection con = db.getConnection()) {
            con.setAutoCommit(false);
            try {
                ShipmentStatus current = lockShipment(con, shipmentId, ownerUserId);
                if (current == null) {
                    con.rollback();
                    return new CreateResult(CreateOutcome.SHIPMENT_NOT_FOUND, 0);
                }
                if (hasActiveBooking(con, shipmentId)) {
                    con.rollback();
                    return new CreateResult(CreateOutcome.ALREADY_ACTIVE, 0);
                }
                if (!bookableStatuses.contains(current)) {
                    con.rollback();
                    return new CreateResult(CreateOutcome.SHIPMENT_NOT_BOOKABLE, 0);
                }
                long bookingId = insertBooking(con, shipmentId, current, snapshot);
                insertLegs(con, bookingId, snapshot.getLegs());
                updateShipmentStatus(con, shipmentId, statusAfter);
                con.commit();
                return new CreateResult(CreateOutcome.CREATED, bookingId);
            } catch (SQLIntegrityConstraintViolationException e) {
                con.rollback();
                if (e.getErrorCode() == UserDAO.ER_DUP_ENTRY) {
                    return new CreateResult(CreateOutcome.ALREADY_ACTIVE, 0);
                }
                throw e;
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to create booking for shipment " + shipmentId, e);
        }
    }

    /**
     * Changes a booking's state in one transaction if it is currently {@code from}; on CANCELLED the
     * shipment gets back the status it had before booking. Validity of the transition is the
     * caller's rule; this method only applies it atomically.
     *
     * @param bookingId   booking id
     * @param ownerUserId signed-in user (must own the booking)
     * @param from        required current state
     * @param to          new state (CONFIRMED sets confirmed_at, CANCELLED sets cancelled_at)
     * @return outcome
     * @throws DataAccessException on database failure (after rollback)
     */
    public TransitionOutcome changeStatus(long bookingId, long ownerUserId, BookingStatus from, BookingStatus to)
            throws DataAccessException {
        try (Connection con = db.getConnection()) {
            con.setAutoCommit(false);
            try {
                Long shipmentId = shipmentOfOwnedBooking(con, bookingId, ownerUserId);
                if (shipmentId == null || lockShipment(con, shipmentId, ownerUserId) == null) {
                    con.rollback();
                    return TransitionOutcome.NOT_FOUND;
                }
                String[] row = lockBooking(con, bookingId);
                if (row == null) {
                    con.rollback();
                    return TransitionOutcome.NOT_FOUND;
                }
                if (!from.name().equals(row[0])) {
                    con.rollback();
                    return TransitionOutcome.WRONG_STATE;
                }
                setBookingStatus(con, bookingId, to);
                if (to == BookingStatus.CANCELLED) {
                    updateShipmentStatus(con, shipmentId, ShipmentStatus.valueOf(row[1]));
                }
                con.commit();
                return TransitionOutcome.CHANGED;
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to change booking " + bookingId, e);
        }
    }

    /**
     * Finds a booking of the given user.
     *
     * @param bookingId   booking id
     * @param ownerUserId signed-in user
     * @return the booking, or empty if missing or someone else's
     * @throws DataAccessException on database failure
     */
    public Optional<Booking> findForOwner(long bookingId, long ownerUserId) throws DataAccessException {
        List<Booking> found = query(SELECT_BOOKING + "WHERE b.booking_id = ? AND s.user_id = ?", bookingId, ownerUserId);
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }

    /**
     * Finds any booking regardless of owner. Only for the administrator's read-only view.
     *
     * @param bookingId booking id
     * @return the booking, or empty
     * @throws DataAccessException on database failure
     */
    public Optional<Booking> findAnyById(long bookingId) throws DataAccessException {
        List<Booking> found = query(SELECT_BOOKING + "WHERE b.booking_id = ?", bookingId);
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }

    /**
     * Finds the active (PENDING_PAYMENT or CONFIRMED) booking of a user's shipment.
     *
     * @param shipmentId  shipment id
     * @param ownerUserId signed-in user
     * @return the active booking, or empty
     * @throws DataAccessException on database failure
     */
    public Optional<Booking> findActiveForShipment(long shipmentId, long ownerUserId) throws DataAccessException {
        List<Booking> found = query(SELECT_BOOKING + "WHERE b.active_shipment_id = ? AND s.user_id = ?",
                shipmentId, ownerUserId);
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }

    /**
     * Lists a user's bookings, newest first.
     *
     * @param ownerUserId signed-in user
     * @return bookings
     * @throws DataAccessException on database failure
     */
    public List<Booking> findByOwner(long ownerUserId) throws DataAccessException {
        return query(SELECT_BOOKING + "WHERE s.user_id = ? ORDER BY b.booking_id DESC", ownerUserId);
    }

    /**
     * Lists a user's most recent bookings, newest first.
     *
     * @param ownerUserId signed-in user
     * @param limit       maximum number of rows
     * @return bookings
     * @throws DataAccessException on database failure
     */
    public List<Booking> findRecentByOwner(long ownerUserId, int limit) throws DataAccessException {
        return query(SELECT_BOOKING + "WHERE s.user_id = ? ORDER BY b.booked_at DESC, b.booking_id DESC LIMIT ?",
                ownerUserId, Math.max(0, limit));
    }

    /**
     * Lists every booking, newest first. Only for the administrator.
     *
     * @return bookings
     * @throws DataAccessException on database failure
     */
    public List<Booking> findAll() throws DataAccessException {
        return query(SELECT_BOOKING + "ORDER BY b.booking_id DESC");
    }

    private List<Booking> query(String sql, long... params) throws DataAccessException {
        try (Connection con = db.getConnection(); PreparedStatement ps = con.prepareStatement(sql);
             PreparedStatement legs = con.prepareStatement(LEGS_SQL)) {
            for (int i = 0; i < params.length; i++) {
                ps.setLong(i + 1, params[i]);
            }
            List<Booking> result = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(map(rs, loadLegs(legs, rs.getLong("booking_id"))));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load bookings", e);
        }
    }

    static ShipmentStatus lockShipment(Connection con, long shipmentId, long ownerUserId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT status FROM shipments WHERE shipment_id = ? AND user_id = ? FOR UPDATE")) {
            ps.setLong(1, shipmentId);
            ps.setLong(2, ownerUserId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? ShipmentStatus.valueOf(rs.getString(1)) : null;
            }
        }
    }

    static Long shipmentOfOwnedBooking(Connection con, long bookingId, long ownerUserId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT b.shipment_id FROM bookings b JOIN shipments s "
                + "ON s.shipment_id = b.shipment_id WHERE b.booking_id = ? AND s.user_id = ?")) {
            ps.setLong(1, bookingId);
            ps.setLong(2, ownerUserId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : null;
            }
        }
    }

    /** @return {status, previous_shipment_status, total_cost} of the locked booking, or null */
    static String[] lockBooking(Connection con, long bookingId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT status, previous_shipment_status, total_cost "
                + "FROM bookings WHERE booking_id = ? FOR UPDATE")) {
            ps.setLong(1, bookingId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? new String[] {rs.getString(1), rs.getString(2), rs.getBigDecimal(3).toPlainString()}
                        : null;
            }
        }
    }

    static void setBookingStatus(Connection con, long bookingId, BookingStatus to) throws SQLException {
        String timestamp = to == BookingStatus.CONFIRMED ? ", confirmed_at = CURRENT_TIMESTAMP"
                : to == BookingStatus.CANCELLED ? ", cancelled_at = CURRENT_TIMESTAMP" : "";
        try (PreparedStatement ps = con.prepareStatement("UPDATE bookings SET status = ?" + timestamp
                + " WHERE booking_id = ?")) {
            ps.setString(1, to.name());
            ps.setLong(2, bookingId);
            ps.executeUpdate();
        }
    }

    private static boolean hasActiveBooking(Connection con, long shipmentId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM bookings WHERE active_shipment_id = ?")) {
            ps.setLong(1, shipmentId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    private static void updateShipmentStatus(Connection con, long shipmentId, ShipmentStatus status) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("UPDATE shipments SET status = ? WHERE shipment_id = ?")) {
            ps.setString(1, status.name());
            ps.setLong(2, shipmentId);
            ps.executeUpdate();
        }
    }

    private static long insertBooking(Connection con, long shipmentId, ShipmentStatus previous, BookingSnapshot s)
            throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            CostBreakdown c = s.getCost();
            ps.setLong(1, shipmentId);
            ps.setLong(2, s.getRouteId());
            ps.setString(3, s.getRouteName());
            ps.setString(4, s.getRouteMode().name());
            ps.setString(5, s.getSource());
            ps.setString(6, s.getDestination());
            ps.setString(7, s.getCargoType().name());
            ps.setBigDecimal(8, BigDecimal.valueOf(s.getWeightKg()).setScale(Numbers.WEIGHT_SCALE, RoundingMode.UNNECESSARY));
            ps.setDate(9, Date.valueOf(s.getDispatchDate()));
            ps.setBigDecimal(10, c.getTransport());
            ps.setBigDecimal(11, c.getHandling());
            ps.setBigDecimal(12, c.getTransfer());
            ps.setBigDecimal(13, c.getWaiting());
            ps.setString(14, s.getStrategyKey());
            ps.setString(15, s.getStrategyName());
            ps.setBigDecimal(16, c.getTotal());
            setDecimal(ps, 17, s.getDurationHours());
            if (s.getEstimatedArrival() == null) {
                ps.setNull(18, Types.TIMESTAMP);
            } else {
                ps.setTimestamp(18, Timestamp.valueOf(s.getEstimatedArrival()));
            }
            setDecimal(ps, 19, s.getEmissionsKg());
            setDecimal(ps, 20, s.getReliability());
            setDecimal(ps, 21, s.getBalancedScore());
            ps.setString(22, truncate(s.getReason()));
            ps.setString(23, truncate(s.getNotes()));
            ps.setString(24, BookingStatus.PENDING_PAYMENT.name());
            ps.setString(25, previous.name());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("No generated key returned for booking");
                }
                return keys.getLong(1);
            }
        }
    }

    private static void insertLegs(Connection con, long bookingId, List<RouteLeg> legs) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(INSERT_LEG_SQL)) {
            int seq = 1;
            for (RouteLeg leg : legs) {
                ps.setLong(1, bookingId);
                ps.setInt(2, seq++);
                ps.setString(3, leg.getMode().name());
                ps.setBigDecimal(4, BigDecimal.valueOf(leg.getDistanceKm()));
                ps.setBigDecimal(5, leg.getRatePerTonneKm());
                setDecimal(ps, 6, leg.getSpeedKmh());
                ps.setBigDecimal(7, BigDecimal.valueOf(leg.getBufferHours()));
                setDecimal(ps, 8, leg.getEmissionFactorKgPerTonneKm());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static List<RouteLeg> loadLegs(PreparedStatement ps, long bookingId) throws SQLException {
        ps.setLong(1, bookingId);
        List<RouteLeg> legs = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                legs.add(new RouteLeg(TransportMode.valueOf(rs.getString("mode")),
                        rs.getBigDecimal("distance_km").doubleValue(), rs.getBigDecimal("rate_per_tonne_km"),
                        toDouble(rs.getBigDecimal("speed_kmh")), rs.getBigDecimal("buffer_hours").doubleValue(),
                        toDouble(rs.getBigDecimal("emission_factor"))));
            }
        }
        return legs;
    }

    private static Booking map(ResultSet rs, List<RouteLeg> legs) throws SQLException {
        Timestamp arrival = rs.getTimestamp("estimated_arrival");
        BookingSnapshot snapshot = new BookingSnapshot(rs.getString("shipment_source"),
                rs.getString("shipment_destination"), CargoType.valueOf(rs.getString("cargo_type")),
                rs.getBigDecimal("weight_kg").doubleValue(), rs.getDate("dispatch_date").toLocalDate(),
                rs.getLong("route_id"), rs.getString("route_name"), TransportMode.valueOf(rs.getString("route_mode")),
                legs, new CostBreakdown(rs.getBigDecimal("transport_cost"), rs.getBigDecimal("handling_cost"),
                rs.getBigDecimal("transfer_cost"), rs.getBigDecimal("waiting_cost")),
                toDouble(rs.getBigDecimal("duration_hours")), arrival == null ? null : arrival.toLocalDateTime(),
                toDouble(rs.getBigDecimal("emissions_kg")), toDouble(rs.getBigDecimal("reliability")),
                toDouble(rs.getBigDecimal("balanced_score")), rs.getString("strategy_key"), rs.getString("strategy_name"),
                rs.getString("recommendation_reason"), rs.getString("evaluation_notes"));
        return new Booking(rs.getLong("booking_id"), rs.getLong("shipment_id"), rs.getLong("owner_user_id"),
                BookingStatus.valueOf(rs.getString("status")), toLocal(rs.getTimestamp("booked_at")),
                toLocal(rs.getTimestamp("confirmed_at")), toLocal(rs.getTimestamp("cancelled_at")), snapshot);
    }

    private static void setDecimal(PreparedStatement ps, int index, Double value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.DECIMAL);
        } else {
            ps.setBigDecimal(index, BigDecimal.valueOf(value));
        }
    }

    private static Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }

    private static LocalDateTime toLocal(Timestamp t) {
        return t == null ? null : t.toLocalDateTime();
    }

    private static String truncate(String text) {
        return text.length() > MAX_TEXT ? text.substring(0, MAX_TEXT - 1) + "…" : text;
    }
}

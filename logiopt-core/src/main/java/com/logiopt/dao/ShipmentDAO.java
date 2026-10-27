package com.logiopt.dao;

import com.logiopt.exception.DataAccessException;
import com.logiopt.model.CargoType;
import com.logiopt.model.OptimisationPreference;
import com.logiopt.model.Shipment;
import com.logiopt.model.ShipmentStatus;
import com.logiopt.util.Numbers;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC access to the shipments table.
 */
public class ShipmentDAO {

    private static final String COLUMNS =
            "shipment_id, user_id, source, destination, cargo_type, weight_kg, dimensions, dispatch_date, status, "
            + "optimisation_preference";
    private static final String INSERT_SQL = "INSERT INTO shipments (user_id, source, destination, cargo_type, "
            + "weight_kg, dimensions, dispatch_date, status, optimisation_preference) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String FIND_BY_ID_SQL = "SELECT " + COLUMNS + " FROM shipments WHERE shipment_id = ?";
    private static final String FIND_BY_ID_FOR_USER_SQL =
            "SELECT " + COLUMNS + " FROM shipments WHERE shipment_id = ? AND user_id = ?";
    private static final String FIND_BY_USER_SQL =
            "SELECT " + COLUMNS + " FROM shipments WHERE user_id = ? ORDER BY shipment_id DESC";
    private static final String FIND_RECENT_BY_USER_SQL =
            "SELECT " + COLUMNS + " FROM shipments WHERE user_id = ? ORDER BY created_at DESC, shipment_id DESC LIMIT ?";
    private static final String UPDATE_DRAFT_SQL = "UPDATE shipments SET source = ?, destination = ?, cargo_type = ?, "
            + "weight_kg = ?, dimensions = ?, dispatch_date = ?, "
            // A shipment without a preference (the web form) keeps the stored one.
            + "optimisation_preference = COALESCE(?, optimisation_preference) "
            + "WHERE shipment_id = ? AND user_id = ? AND status = 'DRAFT'";

    /** Result of {@link #updateDraft}. */
    public enum UpdateOutcome {
        /** The draft was changed. */
        UPDATED,
        /** No such shipment for this user. */
        NOT_FOUND,
        /** The shipment is not a draft (or has an active booking); nothing was changed. */
        NOT_EDITABLE
    }

    /** Outcome plus the shipment's status when the update was decided (null if not found). */
    public static final class UpdateResult {
        private final UpdateOutcome outcome;
        private final ShipmentStatus status;

        UpdateResult(UpdateOutcome outcome, ShipmentStatus status) {
            this.outcome = outcome;
            this.status = status;
        }

        /** @return outcome */
        public UpdateOutcome getOutcome() {
            return outcome;
        }

        /** @return status under the row lock, or null if not found */
        public ShipmentStatus getStatus() {
            return status;
        }
    }

    private final DBConnection db;

    /**
     * Creates the DAO.
     *
     * @param db connection factory
     */
    public ShipmentDAO(DBConnection db) {
        this.db = db;
    }

    /**
     * Inserts a shipment. The weight is written with scale 3 and never rounded: a weight with more
     * decimals fails with ArithmeticException (validation rejects it first).
     *
     * @param shipment validated shipment
     * @return generated shipment id
     * @throws DataAccessException on database failure
     */
    public long insert(Shipment shipment) throws DataAccessException {
        BigDecimal weight = BigDecimal.valueOf(shipment.getWeightKg())
                .setScale(Numbers.WEIGHT_SCALE, RoundingMode.UNNECESSARY);
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, shipment.getUserId());
            ps.setString(2, shipment.getSource().trim());
            ps.setString(3, shipment.getDestination().trim());
            ps.setString(4, shipment.getCargoType().name());
            ps.setBigDecimal(5, weight);
            ps.setString(6, shipment.getDimensions());
            ps.setDate(7, Date.valueOf(shipment.getDispatchDate()));
            ps.setString(8, shipment.getStatus().name());
            ps.setString(9, shipment.hasPreference() ? shipment.getPreference().name() : null);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new DataAccessException("No generated key returned for shipment");
                }
                return keys.getLong(1);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to insert shipment", e);
        }
    }

    /**
     * Finds a shipment by id without an owner check. Package-private on purpose: request handling
     * must use {@link #findByIdForUser(long, long)}.
     *
     * @param shipmentId shipment id
     * @return shipment, or empty if not found
     * @throws DataAccessException on database failure
     */
    Optional<Shipment> findById(long shipmentId) throws DataAccessException {
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(FIND_BY_ID_SQL)) {
            ps.setLong(1, shipmentId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load shipment " + shipmentId, e);
        }
    }

    /**
     * Finds a shipment only if it belongs to the given user. A shipment of another user and a
     * shipment that does not exist give the same empty result, so callers cannot tell them apart.
     *
     * @param shipmentId shipment id
     * @param userId     signed-in user id
     * @return the user's shipment, or empty
     * @throws DataAccessException on database failure
     */
    public Optional<Shipment> findByIdForUser(long shipmentId, long userId) throws DataAccessException {
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(FIND_BY_ID_FOR_USER_SQL)) {
            ps.setLong(1, shipmentId);
            ps.setLong(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load shipment " + shipmentId, e);
        }
    }

    /**
     * Lists a user's shipments, newest first.
     *
     * @param userId user id
     * @return shipments
     * @throws DataAccessException on database failure
     */
    public List<Shipment> findByUser(long userId) throws DataAccessException {
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(FIND_BY_USER_SQL)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Shipment> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(map(rs));
                }
                return result;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to list shipments for user " + userId, e);
        }
    }

    /**
     * Lists a user's most recent shipments, newest first.
     *
     * @param userId user id
     * @param limit  maximum number of rows
     * @return shipments
     * @throws DataAccessException on database failure
     */
    public List<Shipment> findRecentByUser(long userId, int limit) throws DataAccessException {
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(FIND_RECENT_BY_USER_SQL)) {
            ps.setLong(1, userId);
            ps.setInt(2, Math.max(0, limit));
            try (ResultSet rs = ps.executeQuery()) {
                List<Shipment> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(map(rs));
                }
                return result;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to list recent shipments for user " + userId, e);
        }
    }

    /**
     * Replaces the editable fields of a user's DRAFT shipment in one transaction: locks the
     * shipment row (the lock booking takes first, so an edit and a booking of the same shipment run
     * one after the other), checks that it is still a draft without an active booking, then updates
     * it. Bookings and their snapshots are separate rows and are never touched. The weight is
     * written with scale 3 and never rounded (validation rejects more decimals first).
     *
     * @param edited validated shipment carrying the id, owner and new field values
     * @return outcome and the status seen under the lock
     * @throws DataAccessException on database failure (after rollback)
     */
    public UpdateResult updateDraft(Shipment edited) throws DataAccessException {
        BigDecimal weight = BigDecimal.valueOf(edited.getWeightKg())
                .setScale(Numbers.WEIGHT_SCALE, RoundingMode.UNNECESSARY);
        try (Connection con = db.getConnection()) {
            con.setAutoCommit(false);
            try {
                ShipmentStatus current = BookingDAO.lockShipment(con, edited.getShipmentId(), edited.getUserId());
                if (current == null) {
                    con.rollback();
                    return new UpdateResult(UpdateOutcome.NOT_FOUND, null);
                }
                if (current != ShipmentStatus.DRAFT || hasActiveBooking(con, edited.getShipmentId())) {
                    con.rollback();
                    return new UpdateResult(UpdateOutcome.NOT_EDITABLE,
                            current == ShipmentStatus.DRAFT ? ShipmentStatus.BOOKED : current);
                }
                try (PreparedStatement ps = con.prepareStatement(UPDATE_DRAFT_SQL)) {
                    ps.setString(1, edited.getSource().trim());
                    ps.setString(2, edited.getDestination().trim());
                    ps.setString(3, edited.getCargoType().name());
                    ps.setBigDecimal(4, weight);
                    ps.setString(5, edited.getDimensions());
                    ps.setDate(6, Date.valueOf(edited.getDispatchDate()));
                    ps.setString(7, edited.hasPreference() ? edited.getPreference().name() : null);
                    ps.setLong(8, edited.getShipmentId());
                    ps.setLong(9, edited.getUserId());
                    if (ps.executeUpdate() != 1) {
                        throw new SQLException("Locked draft shipment " + edited.getShipmentId() + " was not updated");
                    }
                }
                con.commit();
                return new UpdateResult(UpdateOutcome.UPDATED, ShipmentStatus.DRAFT);
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to update shipment " + edited.getShipmentId(), e);
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

    private static Shipment map(ResultSet rs) throws SQLException {
        return new Shipment(rs.getLong("shipment_id"), rs.getLong("user_id"), rs.getString("source"),
                rs.getString("destination"), CargoType.valueOf(rs.getString("cargo_type")),
                rs.getBigDecimal("weight_kg").doubleValue(), rs.getString("dimensions"),
                rs.getDate("dispatch_date").toLocalDate(), ShipmentStatus.valueOf(rs.getString("status")),
                OptimisationPreference.parseOrNull(rs.getString("optimisation_preference")));
    }
}

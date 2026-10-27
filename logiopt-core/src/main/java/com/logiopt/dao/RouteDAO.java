package com.logiopt.dao;

import com.logiopt.exception.DataAccessException;
import com.logiopt.exception.RouteConfigurationException;
import com.logiopt.model.CargoType;
import com.logiopt.model.Route;
import com.logiopt.model.RouteFactory;
import com.logiopt.model.RouteLeg;
import com.logiopt.model.RouteParams;
import com.logiopt.model.TransportMode;
import com.logiopt.util.Strings;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * JDBC access to routes and route_legs. Builds Route subclasses through RouteFactory and skips
 * (and logs) rows whose configuration is invalid.
 */
public class RouteDAO {

    private static final Logger LOG = Logger.getLogger(RouteDAO.class.getName());

    private static final String NORMALIZED_SOURCE = "LOWER(REGEXP_REPLACE(TRIM(source), '[[:space:]]+', ' '))";
    private static final String NORMALIZED_DESTINATION =
            "LOWER(REGEXP_REPLACE(TRIM(destination), '[[:space:]]+', ' '))";
    private static final String FIND_ROUTES_SQL = "SELECT route_id, name, source, destination, mode, reliability, "
            + "capacity_kg, handling_charge, transfer_charge_per_transfer, transfer_hours_per_transfer, "
            + "waiting_hours, waiting_rate_per_hour, unsupported_cargo FROM routes WHERE "
            + NORMALIZED_SOURCE + " = ? AND " + NORMALIZED_DESTINATION + " = ? ORDER BY route_id";
    private static final String FIND_LEGS_SQL = "SELECT mode, distance_km, rate_per_tonne_km, speed_kmh, "
            + "buffer_hours, emission_factor FROM route_legs WHERE route_id = ? ORDER BY sequence_no";
    private static final String KNOWN_CITIES_SQL = "SELECT city FROM (SELECT TRIM(source) AS city FROM routes "
            + "UNION SELECT TRIM(destination) FROM routes) cities ORDER BY city";

    private final DBConnection db;

    /**
     * Creates the DAO.
     *
     * @param db connection factory
     */
    public RouteDAO(DBConnection db) {
        this.db = db;
    }

    /**
     * Loads all valid routes for a city pair (trim, collapsed whitespace, case-insensitive).
     *
     * @param source      source city
     * @param destination destination city
     * @return routes with their legs, ordered by id
     * @throws DataAccessException on database failure
     */
    public List<Route> findBySourceAndDestination(String source, String destination) throws DataAccessException {
        List<Route> routes = new ArrayList<>();
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(FIND_ROUTES_SQL);
             PreparedStatement legsPs = con.prepareStatement(FIND_LEGS_SQL)) {
            ps.setString(1, Strings.normalize(source));
            ps.setString(2, Strings.normalize(destination));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long routeId = rs.getLong("route_id");
                    try {
                        routes.add(mapRoute(rs, loadLegs(legsPs, routeId)));
                    } catch (RouteConfigurationException | IllegalArgumentException e) {
                        LOG.log(Level.WARNING, "Skipping invalid route {0}: {1}",
                                new Object[] {routeId, e.getMessage()});
                    }
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load routes", e);
        }
        return routes;
    }

    /**
     * Lists every city that is the source or destination of at least one route, alphabetically,
     * with one entry per city after trimming, collapsing whitespace and ignoring case.
     *
     * @return known city names as stored (trimmed)
     * @throws DataAccessException on database failure
     */
    public List<String> knownCities() throws DataAccessException {
        Map<String, String> cities = new LinkedHashMap<>();
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(KNOWN_CITIES_SQL);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String city = rs.getString("city");
                if (!Strings.isBlank(city)) {
                    cities.putIfAbsent(Strings.normalize(city), city);
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load known cities", e);
        }
        return new ArrayList<>(cities.values());
    }

    private static List<RouteLeg> loadLegs(PreparedStatement ps, long routeId) throws SQLException {
        ps.setLong(1, routeId);
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

    private static Route mapRoute(ResultSet rs, List<RouteLeg> legs) throws SQLException {
        RouteParams params = new RouteParams()
                .routeId(rs.getLong("route_id"))
                .name(rs.getString("name"))
                .source(rs.getString("source"))
                .destination(rs.getString("destination"))
                .legs(legs)
                .reliability(toDouble(rs.getBigDecimal("reliability")))
                .capacityKg(rs.getBigDecimal("capacity_kg").doubleValue())
                .handlingCharge(rs.getBigDecimal("handling_charge"))
                .transferChargePerTransfer(rs.getBigDecimal("transfer_charge_per_transfer"))
                .transferHoursPerTransfer(rs.getBigDecimal("transfer_hours_per_transfer").doubleValue())
                .waitingHours(rs.getBigDecimal("waiting_hours").doubleValue())
                .waitingRatePerHour(rs.getBigDecimal("waiting_rate_per_hour"))
                .unsupportedCargo(parseCargo(rs.getString("unsupported_cargo")));
        return RouteFactory.create(TransportMode.valueOf(rs.getString("mode")), params);
    }

    private static Set<CargoType> parseCargo(String text) {
        Set<CargoType> cargo = EnumSet.noneOf(CargoType.class);
        if (text != null) {
            for (String part : text.split(",")) {
                if (!part.isBlank()) {
                    cargo.add(CargoType.valueOf(part.trim()));
                }
            }
        }
        return cargo;
    }

    private static Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }
}

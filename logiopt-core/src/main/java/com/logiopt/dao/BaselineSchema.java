package com.logiopt.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The table structure created by V1__baseline.sql, used to decide whether an existing database
 * that has no migration history can be recorded as already at V1. Columns are compared by name,
 * MySQL column type, nullability and auto_increment; then primary keys, unique keys, foreign keys
 * (target and whether deletes cascade) and CHECK constraint names. CHECK clause text and index
 * names are not compared: mysqldump/clients add charset introducers such as _cp850 and MySQL
 * names implicit foreign-key indexes itself.
 */
final class BaselineSchema {

    /** Tables created by V1, in creation order. */
    static final List<String> TABLES = List.of("users", "shipments", "routes", "route_legs", "bookings",
            "tracking", "payments", "notifications", "reports");

    /** Unique key that V2 adds when missing, so a database without it may still be baselined. */
    static final String OPTIONAL_UNIQUE_KEY = "route_legs(route_id,sequence_no)";

    private static final String NN = " NOT NULL";
    private static final String NULL = " NULL";
    private static final String AI = " NOT NULL auto_increment";

    private static final Map<String, Map<String, String>> COLUMNS = new LinkedHashMap<>();
    private static final Set<String> PRIMARY_KEYS = new TreeSet<>();
    private static final Set<String> UNIQUE_KEYS = new TreeSet<>();
    private static final Set<String> FOREIGN_KEYS = new TreeSet<>();
    private static final Set<String> CHECKS = new TreeSet<>();

    static {
        table("users", "user_id", "bigint" + AI, "name", "varchar(100)" + NN, "email", "varchar(255)" + NN,
                "password_hash", "varchar(255)" + NN, "role", "varchar(20)" + NN, "enabled", "tinyint(1)" + NN,
                "created_at", "timestamp" + NN);
        table("shipments", "shipment_id", "bigint" + AI, "user_id", "bigint" + NN, "source", "varchar(100)" + NN,
                "destination", "varchar(100)" + NN, "cargo_type", "varchar(20)" + NN,
                "weight_kg", "decimal(12,3)" + NN, "dimensions", "varchar(100)" + NULL, "dispatch_date", "date" + NN,
                "status", "varchar(20)" + NN, "created_at", "timestamp" + NN);
        table("routes", "route_id", "bigint" + NN, "name", "varchar(100)" + NN, "source", "varchar(100)" + NN,
                "destination", "varchar(100)" + NN, "mode", "varchar(20)" + NN, "reliability", "decimal(4,3)" + NULL,
                "capacity_kg", "decimal(12,2)" + NN, "handling_charge", "decimal(12,2)" + NN,
                "transfer_charge_per_transfer", "decimal(12,2)" + NN,
                "transfer_hours_per_transfer", "decimal(8,2)" + NN, "waiting_hours", "decimal(8,2)" + NN,
                "waiting_rate_per_hour", "decimal(12,2)" + NN, "unsupported_cargo", "varchar(100)" + NN);
        table("route_legs", "leg_id", "bigint" + AI, "route_id", "bigint" + NN, "sequence_no", "int" + NN,
                "mode", "varchar(20)" + NN, "distance_km", "decimal(10,2)" + NN,
                "rate_per_tonne_km", "decimal(10,4)" + NN, "speed_kmh", "decimal(8,2)" + NULL,
                "buffer_hours", "decimal(8,2)" + NN, "emission_factor", "decimal(8,5)" + NULL);
        table("bookings", "booking_id", "bigint" + AI, "shipment_id", "bigint" + NN, "route_id", "bigint" + NN,
                "strategy_key", "varchar(20)" + NN, "total_cost", "decimal(14,2)" + NN, "status", "varchar(20)" + NN,
                "booked_at", "timestamp" + NN);
        table("tracking", "tracking_id", "bigint" + AI, "booking_id", "bigint" + NN, "location", "varchar(100)" + NN,
                "status", "varchar(30)" + NN, "note", "varchar(255)" + NULL, "event_time", "timestamp" + NN);
        table("payments", "payment_id", "bigint" + AI, "booking_id", "bigint" + NN, "amount", "decimal(14,2)" + NN,
                "method", "varchar(30)" + NN, "status", "varchar(20)" + NN, "paid_at", "timestamp" + NULL);
        table("notifications", "notification_id", "bigint" + AI, "user_id", "bigint" + NN,
                "message", "varchar(500)" + NN, "is_read", "tinyint(1)" + NN, "created_at", "timestamp" + NN);
        table("reports", "report_id", "bigint" + AI, "shipment_id", "bigint" + NN, "strategy_key", "varchar(20)" + NN,
                "file_name", "varchar(255)" + NN, "status", "varchar(20)" + NN, "created_at", "timestamp" + NN);

        for (String t : TABLES) {
            PRIMARY_KEYS.add(t + "(" + COLUMNS.get(t).keySet().iterator().next() + ")");
        }
        UNIQUE_KEYS.add("users(email)");
        UNIQUE_KEYS.add(OPTIONAL_UNIQUE_KEY);
        FOREIGN_KEYS.add("shipments.user_id -> users.user_id");
        FOREIGN_KEYS.add("route_legs.route_id -> routes.route_id ON DELETE CASCADE");
        FOREIGN_KEYS.add("bookings.shipment_id -> shipments.shipment_id");
        FOREIGN_KEYS.add("bookings.route_id -> routes.route_id");
        FOREIGN_KEYS.add("tracking.booking_id -> bookings.booking_id");
        FOREIGN_KEYS.add("payments.booking_id -> bookings.booking_id");
        FOREIGN_KEYS.add("notifications.user_id -> users.user_id");
        FOREIGN_KEYS.add("reports.shipment_id -> shipments.shipment_id");
        CHECKS.addAll(List.of("chk_users_role", "chk_shipments_cargo", "chk_shipments_weight",
                "chk_shipments_status", "chk_routes_mode", "chk_routes_reliability", "chk_routes_capacity",
                "chk_routes_charges", "chk_route_legs_mode", "chk_route_legs_values", "chk_payments_amount"));
    }

    private BaselineSchema() {
    }

    private static void table(String name, String... columnsAndTypes) {
        Map<String, String> columns = new LinkedHashMap<>();
        for (int i = 0; i < columnsAndTypes.length; i += 2) {
            columns.put(columnsAndTypes[i], columnsAndTypes[i + 1]);
        }
        COLUMNS.put(name, columns);
    }

    /** Structure of the baseline tables as found in a database. */
    static final class Snapshot {
        final Map<String, Map<String, String>> columns = new TreeMap<>();
        final Set<String> primaryKeys = new TreeSet<>();
        final Set<String> uniqueKeys = new TreeSet<>();
        final Set<String> foreignKeys = new TreeSet<>();
        final Set<String> checks = new TreeSet<>();
    }

    /** @return the structure V1 creates, as a snapshot (for tests) */
    static Snapshot expected() {
        Snapshot s = new Snapshot();
        for (Map.Entry<String, Map<String, String>> e : COLUMNS.entrySet()) {
            s.columns.put(e.getKey(), new TreeMap<>(e.getValue()));
        }
        s.primaryKeys.addAll(PRIMARY_KEYS);
        s.uniqueKeys.addAll(UNIQUE_KEYS);
        s.foreignKeys.addAll(FOREIGN_KEYS);
        s.checks.addAll(CHECKS);
        return s;
    }

    /**
     * Lists every difference between a database's baseline tables and V1. Missing optional unique
     * key {@link #OPTIONAL_UNIQUE_KEY} is allowed; extra indexes and extra CHECK constraints are ignored.
     *
     * @param actual structure read from the database
     * @return human-readable differences; empty if the database matches V1
     */
    static List<String> compare(Snapshot actual) {
        List<String> diffs = new ArrayList<>();
        for (Map.Entry<String, Map<String, String>> table : COLUMNS.entrySet()) {
            String t = table.getKey();
            Map<String, String> found = actual.columns.get(t);
            if (found == null) {
                diffs.add("table " + t + " is missing");
                continue;
            }
            for (Map.Entry<String, String> column : table.getValue().entrySet()) {
                String have = found.get(column.getKey());
                if (have == null) {
                    diffs.add(t + "." + column.getKey() + ": missing (expected " + column.getValue() + ")");
                } else if (!have.equals(column.getValue())) {
                    diffs.add(t + "." + column.getKey() + ": expected " + column.getValue() + ", found " + have);
                }
            }
            for (String column : found.keySet()) {
                if (!table.getValue().containsKey(column)) {
                    diffs.add(t + "." + column + ": unexpected column (" + found.get(column) + ")");
                }
            }
        }
        missing(diffs, "primary key", PRIMARY_KEYS, actual.primaryKeys, null);
        missing(diffs, "unique key", UNIQUE_KEYS, actual.uniqueKeys, OPTIONAL_UNIQUE_KEY);
        missing(diffs, "foreign key", FOREIGN_KEYS, actual.foreignKeys, null);
        for (String pk : actual.primaryKeys) {
            if (!PRIMARY_KEYS.contains(pk)) {
                diffs.add("unexpected primary key " + pk);
            }
        }
        missing(diffs, "check constraint", CHECKS, actual.checks, null);
        return diffs;
    }

    private static void missing(List<String> diffs, String kind, Set<String> expected, Set<String> actual,
                                String optional) {
        for (String item : expected) {
            if (!actual.contains(item) && !item.equals(optional)) {
                diffs.add("missing " + kind + " " + item);
            }
        }
    }

    /**
     * Reads the structure of the baseline tables of the connected database from information_schema.
     *
     * @param con open connection
     * @return snapshot of the tables listed in {@link #TABLES} that exist
     * @throws SQLException on database failure
     */
    static Snapshot read(Connection con) throws SQLException {
        Snapshot s = new Snapshot();
        String in = String.join(",", Collections.nCopies(TABLES.size(), "?"));
        try (PreparedStatement ps = con.prepareStatement("SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, "
                + "EXTRA FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN (" + in
                + ") ORDER BY TABLE_NAME, ORDINAL_POSITION")) {
            bindTables(ps);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String extra = rs.getString("EXTRA").toLowerCase(Locale.ROOT).contains("auto_increment")
                            ? " auto_increment" : "";
                    String type = rs.getString("COLUMN_TYPE").toLowerCase(Locale.ROOT)
                            + ("YES".equals(rs.getString("IS_NULLABLE")) ? NULL : NN) + extra;
                    s.columns.computeIfAbsent(rs.getString("TABLE_NAME").toLowerCase(Locale.ROOT), k -> new TreeMap<>())
                            .put(rs.getString("COLUMN_NAME").toLowerCase(Locale.ROOT), type);
                }
            }
        }
        try (PreparedStatement ps = con.prepareStatement("SELECT TABLE_NAME, INDEX_NAME, "
                + "GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') AS cols FROM information_schema.STATISTICS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND NON_UNIQUE = 0 AND TABLE_NAME IN (" + in + ") "
                + "GROUP BY TABLE_NAME, INDEX_NAME")) {
            bindTables(ps);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String key = rs.getString("TABLE_NAME").toLowerCase(Locale.ROOT) + "("
                            + rs.getString("cols").toLowerCase(Locale.ROOT) + ")";
                    if ("PRIMARY".equals(rs.getString("INDEX_NAME"))) {
                        s.primaryKeys.add(key);
                    } else {
                        s.uniqueKeys.add(key);
                    }
                }
            }
        }
        try (PreparedStatement ps = con.prepareStatement("SELECT k.TABLE_NAME, k.COLUMN_NAME, "
                + "k.REFERENCED_TABLE_NAME, k.REFERENCED_COLUMN_NAME, r.DELETE_RULE "
                + "FROM information_schema.KEY_COLUMN_USAGE k JOIN information_schema.REFERENTIAL_CONSTRAINTS r "
                + "ON r.CONSTRAINT_SCHEMA = k.CONSTRAINT_SCHEMA AND r.CONSTRAINT_NAME = k.CONSTRAINT_NAME "
                + "AND r.TABLE_NAME = k.TABLE_NAME "
                + "WHERE k.TABLE_SCHEMA = DATABASE() AND k.REFERENCED_TABLE_NAME IS NOT NULL AND k.TABLE_NAME IN ("
                + in + ")")) {
            bindTables(ps);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    s.foreignKeys.add(rs.getString(1).toLowerCase(Locale.ROOT) + "." + rs.getString(2).toLowerCase(Locale.ROOT)
                            + " -> " + rs.getString(3).toLowerCase(Locale.ROOT) + "." + rs.getString(4).toLowerCase(Locale.ROOT)
                            + ("CASCADE".equals(rs.getString(5)) ? " ON DELETE CASCADE" : ""));
                }
            }
        }
        try (PreparedStatement ps = con.prepareStatement("SELECT CONSTRAINT_NAME FROM information_schema.TABLE_CONSTRAINTS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND CONSTRAINT_TYPE = 'CHECK' AND TABLE_NAME IN (" + in + ")")) {
            bindTables(ps);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    s.checks.add(rs.getString(1).toLowerCase(Locale.ROOT));
                }
            }
        }
        return s;
    }

    private static void bindTables(PreparedStatement ps) throws SQLException {
        for (int i = 0; i < TABLES.size(); i++) {
            ps.setString(i + 1, TABLES.get(i));
        }
    }
}

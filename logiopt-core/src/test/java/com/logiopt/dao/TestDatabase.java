package com.logiopt.dao;

import com.logiopt.exception.DataAccessException;
import com.logiopt.util.Strings;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Access to the disposable test database for DAO and migration tests. Uses only
 * LOGIOPT_TEST_DB_URL (never LOGIOPT_DB_URL), refuses a database whose name does not end in
 * "_test" both from the URL and from SELECT DATABASE(), and re-checks before every destructive call.
 * Tests are skipped when LOGIOPT_TEST_DB_URL is unset; a non-test name fails them.
 */
public final class TestDatabase {

    /** Environment variable naming a filtered mysqldump file for the upgrade-path tests. */
    public static final String ENV_UPGRADE_DUMP = "LOGIOPT_TEST_UPGRADE_DUMP";

    private static final Pattern FORBIDDEN_ANYWHERE = Pattern.compile("(?i)@@GLOBAL|SQL_LOG_BIN");
    private static final Pattern FORBIDDEN_START =
            Pattern.compile("(?is)^(/\\*!\\d+\\s*)?(USE|CREATE\\s+DATABASE|DROP\\s+DATABASE|ALTER\\s+DATABASE)\\b.*");

    private TestDatabase() {
    }

    /**
     * Returns the test database, skipping the calling test class when LOGIOPT_TEST_DB_URL is unset.
     *
     * @return connection factory for a database whose name ends in _test
     * @throws DataAccessException if the URL or the connected database is not a _test database
     */
    public static DBConnection connect() throws DataAccessException {
        assumeTrue(!Strings.isBlank(System.getenv(DBConnection.ENV_TEST_URL)),
                DBConnection.ENV_TEST_URL + " is not set; database tests skipped");
        DBConnection db = DBConnection.fromTestConfiguration();
        try (Connection con = db.getConnection()) {
            DBConnection.requireTestDatabase(con);
        } catch (SQLException e) {
            throw new DataAccessException("Cannot close test connection", e);
        }
        return db;
    }

    /**
     * Returns the filtered dump for upgrade tests, skipping when LOGIOPT_TEST_UPGRADE_DUMP is unset.
     *
     * @return dump path
     */
    public static Path upgradeDump() {
        String value = System.getenv(ENV_UPGRADE_DUMP);
        assumeTrue(!Strings.isBlank(value), ENV_UPGRADE_DUMP + " is not set; upgrade-path tests skipped");
        Path dump = Paths.get(value.trim());
        assumeTrue(Files.isRegularFile(dump), ENV_UPGRADE_DUMP + " does not name a file; upgrade-path tests skipped");
        return dump;
    }

    /** Drops every table in the test database. */
    public static void dropAllTables(DBConnection db) throws SQLException, DataAccessException {
        try (Connection con = db.getConnection(); Statement st = con.createStatement()) {
            DBConnection.requireTestDatabase(con);
            List<String> tables = new ArrayList<>();
            try (ResultSet rs = st.executeQuery("SELECT TABLE_NAME FROM information_schema.TABLES "
                    + "WHERE TABLE_SCHEMA = DATABASE()")) {
                while (rs.next()) {
                    tables.add(rs.getString(1));
                }
            }
            st.execute("SET FOREIGN_KEY_CHECKS = 0");
            for (String table : tables) {
                st.execute("DROP TABLE `" + table.replace("`", "``") + "`");
            }
            st.execute("SET FOREIGN_KEY_CHECKS = 1");
        }
    }

    /**
     * @return a connection factory for a closed local port: every use fails at once with
     *         DataAccessException (for error-state tests that must not touch any database)
     * @throws DataAccessException never for this fixed URL
     */
    public static DBConnection unreachable() throws DataAccessException {
        return DBConnection.fromTestConfiguration(Map.of(DBConnection.ENV_TEST_URL,
                "jdbc:mysql://127.0.0.1:1/unreachable_test?connectTimeout=1000"));
    }

    /** Executes statements on one connection of the test database. */
    public static void execute(DBConnection db, String... statements) throws SQLException, DataAccessException {
        try (Connection con = db.getConnection(); Statement st = con.createStatement()) {
            DBConnection.requireTestDatabase(con);
            for (String sql : statements) {
                st.execute(sql);
            }
        }
    }

    /**
     * Restores a mysqldump file into the test database on one connection. Statements that would
     * switch or create databases or change global server state are refused.
     */
    public static void restoreDump(DBConnection db, Path dump) throws SQLException, DataAccessException, IOException {
        List<String> statements = MigrationRunner.splitStatements(Files.readString(dump, StandardCharsets.UTF_8));
        for (String sql : statements) {
            if (FORBIDDEN_ANYWHERE.matcher(sql).find() || FORBIDDEN_START.matcher(sql).matches()) {
                throw new IllegalStateException("Refusing dump statement: "
                        + sql.substring(0, Math.min(sql.length(), 80)));
            }
        }
        execute(db, statements.toArray(new String[0]));
    }

    /** @return the single long value of a query on the test database */
    public static long queryLong(DBConnection db, String sql) throws SQLException, DataAccessException {
        try (Connection con = db.getConnection(); Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            DBConnection.requireTestDatabase(con);
            rs.next();
            return rs.getLong(1);
        }
    }

    /** @return the single string value of a query on the test database */
    public static String queryString(DBConnection db, String sql) throws SQLException, DataAccessException {
        try (Connection con = db.getConnection(); Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            DBConnection.requireTestDatabase(con);
            return rs.next() ? rs.getString(1) : null;
        }
    }

    /** @return whether a table exists in the test database */
    public static boolean tableExists(DBConnection db, String table) throws SQLException, DataAccessException {
        return queryLong(db, "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() "
                + "AND TABLE_NAME = '" + table.replace("'", "''") + "'") > 0;
    }

    /** @return a structure snapshot of the baseline tables in the test database */
    static BaselineSchema.Snapshot readSchema(DBConnection db) throws SQLException, DataAccessException {
        try (Connection con = db.getConnection()) {
            DBConnection.requireTestDatabase(con);
            return BaselineSchema.read(con);
        }
    }
}

package com.logiopt.dao;

import com.logiopt.exception.DataAccessException;
import com.logiopt.util.Strings;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Creates JDBC connections from db.properties, overridable by the environment variables
 * LOGIOPT_DB_URL, LOGIOPT_DB_USER and LOGIOPT_DB_PASSWORD.
 */
public class DBConnection {

    /** Environment variable overriding the JDBC URL. */
    public static final String ENV_URL = "LOGIOPT_DB_URL";
    /** Environment variable overriding the user. */
    public static final String ENV_USER = "LOGIOPT_DB_USER";
    /** Environment variable overriding the password. */
    public static final String ENV_PASSWORD = "LOGIOPT_DB_PASSWORD";
    /** Classpath resource with default settings. */
    public static final String PROPERTIES_RESOURCE = "/db.properties";
    /** Environment variable with the JDBC URL of the disposable test database. */
    public static final String ENV_TEST_URL = "LOGIOPT_TEST_DB_URL";
    /** Environment variable with the test database user; falls back to LOGIOPT_DB_USER. */
    public static final String ENV_TEST_USER = "LOGIOPT_TEST_DB_USER";
    /** Environment variable with the test database password; falls back to LOGIOPT_DB_PASSWORD. */
    public static final String ENV_TEST_PASSWORD = "LOGIOPT_TEST_DB_PASSWORD";
    /** Required suffix of every database that tests or test migrations may change. */
    public static final String TEST_DATABASE_SUFFIX = "_test";

    private static final String DRIVER_CLASS = "com.mysql.cj.jdbc.Driver";
    private static final Pattern DATABASE_IN_URL = Pattern.compile("^jdbc:mysql://[^/]*/([^?;/]+)");

    private final String url;
    private final String user;
    private final String password;

    /**
     * Creates a connection factory.
     *
     * @param url      JDBC URL
     * @param user     database user
     * @param password database password (never logged)
     */
    public DBConnection(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password == null ? "" : password;
    }

    /**
     * Loads db.properties and applies environment variable overrides.
     *
     * @return connection factory
     * @throws DataAccessException if the properties cannot be read or no URL is configured
     */
    public static DBConnection fromConfiguration() throws DataAccessException {
        Properties props = new Properties();
        try (InputStream in = DBConnection.class.getResourceAsStream(PROPERTIES_RESOURCE)) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException e) {
            throw new DataAccessException("Cannot read " + PROPERTIES_RESOURCE, e);
        }
        Map<String, String> env = System.getenv();
        String url = pick(env.get(ENV_URL), props.getProperty("db.url"));
        if (Strings.isBlank(url)) {
            throw new DataAccessException("No database URL configured");
        }
        return new DBConnection(url, pick(env.get(ENV_USER), props.getProperty("db.user")),
                pick(env.get(ENV_PASSWORD), props.getProperty("db.password")));
    }

    /**
     * Creates a connection factory for the disposable test database named by LOGIOPT_TEST_DB_URL.
     * It never falls back to LOGIOPT_DB_URL or db.properties, and it refuses any URL whose database
     * name does not end in "_test". Callers must also check the connected database with
     * {@link #requireTestDatabase(Connection)}.
     *
     * @return test connection factory
     * @throws DataAccessException if LOGIOPT_TEST_DB_URL is unset or names a non-test database
     */
    public static DBConnection fromTestConfiguration() throws DataAccessException {
        return fromTestConfiguration(System.getenv());
    }

    static DBConnection fromTestConfiguration(Map<String, String> env) throws DataAccessException {
        String url = env.get(ENV_TEST_URL);
        if (Strings.isBlank(url)) {
            throw new DataAccessException(ENV_TEST_URL + " is not set");
        }
        String name = databaseName(url);
        if (name == null || !name.endsWith(TEST_DATABASE_SUFFIX)) {
            throw new DataAccessException("Refusing to use " + ENV_TEST_URL + ": database name '" + name
                    + "' does not end in " + TEST_DATABASE_SUFFIX);
        }
        return new DBConnection(url, pick(env.get(ENV_TEST_USER), env.get(ENV_USER)),
                pick(env.get(ENV_TEST_PASSWORD), env.get(ENV_PASSWORD)));
    }

    /**
     * Extracts the database name from a MySQL JDBC URL (jdbc:mysql://host:port/name?params).
     *
     * @param url JDBC URL
     * @return database name, or null if the URL names none
     */
    public static String databaseName(String url) {
        if (url == null) {
            return null;
        }
        Matcher m = DATABASE_IN_URL.matcher(url.trim());
        return m.find() ? m.group(1) : null;
    }

    /**
     * Checks that a connection is really attached to a database whose name ends in "_test".
     *
     * @param con open connection
     * @return the connected database name
     * @throws DataAccessException if it is not a test database
     */
    public static String requireTestDatabase(Connection con) throws DataAccessException {
        try (PreparedStatement ps = con.prepareStatement("SELECT DATABASE()");
             ResultSet rs = ps.executeQuery()) {
            String name = rs.next() ? rs.getString(1) : null;
            if (name == null || !name.endsWith(TEST_DATABASE_SUFFIX)) {
                throw new DataAccessException("Refusing: connected database '" + name + "' does not end in "
                        + TEST_DATABASE_SUFFIX);
            }
            return name;
        } catch (SQLException e) {
            throw new DataAccessException("Cannot determine the connected database", e);
        }
    }

    /** @return database name from this factory's URL, or null */
    public String getDatabaseName() {
        return databaseName(url);
    }

    private static String pick(String override, String fallback) {
        return Strings.isBlank(override) ? fallback : override;
    }

    /**
     * Opens a new connection; callers close it with try-with-resources.
     *
     * @return open connection
     * @throws DataAccessException if the driver is missing or the connection fails
     */
    public Connection getConnection() throws DataAccessException {
        try {
            Class.forName(DRIVER_CLASS);
            return DriverManager.getConnection(url, user, password);
        } catch (ClassNotFoundException e) {
            throw new DataAccessException("MySQL JDBC driver not found", e);
        } catch (SQLException e) {
            throw new DataAccessException("Cannot connect to the database", e);
        }
    }
}

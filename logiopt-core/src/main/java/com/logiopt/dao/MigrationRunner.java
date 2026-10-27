package com.logiopt.dao;

import com.logiopt.exception.DataAccessException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Applies the versioned SQL scripts under /db/migration in order and records each one in the
 * schema_migrations table of the connected database.
 *
 * <ul>
 *     <li>Script names are V&lt;n&gt;__&lt;description&gt;.sql with n = 1, 2, 3, ... without gaps.</li>
 *     <li>A database with no history: if it has none of the V1 tables, V1 runs (fresh install); if
 *     it has all of them with exactly the V1 structure, V1 is recorded as BASELINE without running
 *     (upgrade); anything else stops with the list of differences and changes nothing.</li>
 *     <li>Each script runs on one connection inside a transaction, but MySQL commits DDL
 *     implicitly, so a failure can leave earlier statements of that script applied. The failed
 *     script is recorded with success = FALSE and every later run refuses to continue until the
 *     schema is repaired by hand and that row is deleted.</li>
 *     <li>A recorded script whose checksum (SHA-256 of the text with LF line endings) has changed,
 *     or a recorded version this build does not know, also stops the run.</li>
 *     <li>A named database lock (GET_LOCK) keeps two runners from working at the same time.</li>
 * </ul>
 *
 * Script syntax: one statement per ';' at the end of a line; lines starting with "--" are comments.
 */
public class MigrationRunner {

    /** Migration scripts on the classpath, in order. */
    public static final List<String> SCRIPTS = List.of(
            "/db/migration/V1__baseline.sql",
            "/db/migration/V2__route_legs_unique_key.sql",
            "/db/migration/V3__seed_reference_data.sql",
            "/db/migration/V4__users_auth.sql",
            "/db/migration/V5__bookings_payments.sql",
            "/db/migration/V6__tracking_notifications.sql",
            "/db/migration/V7__report_jobs.sql",
            "/db/migration/V8__routes_natural_key.sql",
            "/db/migration/V9__seed_all_city_pairs.sql",
            "/db/migration/V10__shipment_optimisation_preference.sql");

    /** History table name. */
    public static final String HISTORY_TABLE = "schema_migrations";
    /** Type recorded for a script that was executed. */
    public static final String TYPE_SQL = "SQL";
    /** Type recorded for V1 when an existing schema was accepted without running it. */
    public static final String TYPE_BASELINE = "BASELINE";

    static final String LOCK_NAME = "logiopt_schema_migrations";
    static final int LOCK_TIMEOUT_SECONDS = 30;
    static final int MIN_MAJOR = 8;
    static final int MIN_MINOR = 0;
    static final int MIN_PATCH = 19;

    private static final Pattern NAME = Pattern.compile("V(\\d+)__([A-Za-z0-9_]+)\\.sql");
    private static final Pattern VERSION = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)");
    private static final int MAX_ERROR_LENGTH = 1000;
    private static final String CREATE_HISTORY_SQL = "CREATE TABLE IF NOT EXISTS " + HISTORY_TABLE + " ("
            + "version INT NOT NULL, description VARCHAR(200) NOT NULL, script VARCHAR(255) NOT NULL, "
            + "checksum CHAR(64) NOT NULL, type VARCHAR(10) NOT NULL, success BOOLEAN NOT NULL, "
            + "error_message VARCHAR(1000) NULL, started_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), "
            + "finished_at TIMESTAMP(3) NULL, execution_ms BIGINT NULL, PRIMARY KEY (version)"
            + ") ENGINE = InnoDB DEFAULT CHARSET = utf8mb4";

    private final DBConnection db;
    private final List<Migration> migrations;

    /** One versioned script. */
    static final class Migration {
        final int version;
        final String description;
        final String script;
        final String sql;
        final String checksum;

        Migration(int version, String description, String script, String sql) {
            this.version = version;
            this.description = description;
            this.script = script;
            this.sql = sql;
            this.checksum = checksum(sql);
        }
    }

    /** A row of schema_migrations. */
    static final class Applied {
        final int version;
        final String script;
        final String checksum;
        final String type;
        final boolean success;
        final String errorMessage;

        Applied(int version, String script, String checksum, String type, boolean success, String errorMessage) {
            this.version = version;
            this.script = script;
            this.checksum = checksum;
            this.type = type;
            this.success = success;
            this.errorMessage = errorMessage;
        }
    }

    /**
     * Creates a runner for the application's migrations.
     *
     * @param db connection factory of the target database
     * @throws DataAccessException if a script is missing or misnamed
     */
    public MigrationRunner(DBConnection db) throws DataAccessException {
        this(db, SCRIPTS, true);
    }

    /**
     * Creates a runner for the given classpath scripts. Tests use this for prefixes of the
     * application scripts and for test-only scripts with a separate high version such as V999, so
     * versions only need to start at 1 and increase; the application list itself has no gaps.
     *
     * @param db      connection factory of the target database
     * @param scripts classpath resources in version order
     * @throws DataAccessException if a script is missing or misnamed or the versions are out of order
     */
    MigrationRunner(DBConnection db, List<String> scripts) throws DataAccessException {
        this(db, scripts, false);
    }

    private MigrationRunner(DBConnection db, List<String> scripts, boolean contiguous) throws DataAccessException {
        if (db == null || scripts == null || scripts.isEmpty()) {
            throw new IllegalArgumentException("Connection factory and scripts are required");
        }
        this.db = db;
        List<Migration> loaded = new ArrayList<>();
        for (String script : scripts) {
            loaded.add(load(script));
        }
        if (contiguous) {
            checkOrder(loaded);
        } else {
            checkAscending(loaded);
        }
        this.migrations = Collections.unmodifiableList(loaded);
    }

    static Migration load(String resource) throws DataAccessException {
        String fileName = resource.substring(resource.lastIndexOf('/') + 1);
        Matcher m = NAME.matcher(fileName);
        if (!m.matches()) {
            throw new DataAccessException("Migration script name must be V<n>__<description>.sql: " + resource);
        }
        try (InputStream in = MigrationRunner.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new DataAccessException("Migration script not found on the classpath: " + resource);
            }
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return new Migration(Integer.parseInt(m.group(1)), m.group(2).replace('_', ' '), fileName, sql);
        } catch (IOException e) {
            throw new DataAccessException("Cannot read migration script " + resource, e);
        }
    }

    static void checkOrder(List<Migration> migrations) throws DataAccessException {
        for (int i = 0; i < migrations.size(); i++) {
            if (migrations.get(i).version != i + 1) {
                throw new DataAccessException("Migration versions must be 1, 2, 3, ... without gaps; position "
                        + (i + 1) + " is " + migrations.get(i).script);
            }
        }
    }

    static void checkAscending(List<Migration> migrations) throws DataAccessException {
        int previous = 0;
        for (Migration m : migrations) {
            if (m.version <= previous || (previous == 0 && m.version != 1)) {
                throw new DataAccessException("Migration versions must start at 1 and increase; " + m.script
                        + " is out of order");
            }
            previous = m.version;
        }
    }

    private Migration known(int version) {
        for (Migration m : migrations) {
            if (m.version == version) {
                return m;
            }
        }
        return null;
    }

    /**
     * Splits a script into statements: a statement ends at a line whose trimmed text ends with ';';
     * blank lines and lines starting with "--" are skipped.
     *
     * @param script SQL text
     * @return statements without the trailing ';'
     */
    static List<String> splitStatements(String script) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : script.replace("\r\n", "\n").replace('\r', '\n').split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                continue;
            }
            current.append(line).append('\n');
            if (trimmed.endsWith(";")) {
                String statement = current.toString().trim();
                statements.add(statement.substring(0, statement.length() - 1).trim());
                current.setLength(0);
            }
        }
        if (current.toString().trim().length() > 0) {
            throw new IllegalArgumentException("Script ends with a statement that has no ';': "
                    + current.toString().trim());
        }
        return statements;
    }

    /**
     * SHA-256 of a script after normalizing line endings to LF, so a Git checkout with CRLF does
     * not look like a changed script.
     *
     * @param sql script text
     * @return lowercase hex digest
     */
    static String checksum(String sql) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(sql.replace("\r\n", "\n").getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    /**
     * Checks that the server reports at least MySQL 8.0.19 (CHECK constraints are enforced from
     * 8.0.16, INSERT ... AS alias needs 8.0.19). Versions are compared numerically, so 26.10.0 passes.
     *
     * @param version text of SELECT VERSION()
     * @return true if supported
     */
    static boolean isSupportedServerVersion(String version) {
        if (version == null) {
            return false;
        }
        Matcher m = VERSION.matcher(version.trim());
        if (!m.find()) {
            return false;
        }
        int[] have = {Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3))};
        int[] need = {MIN_MAJOR, MIN_MINOR, MIN_PATCH};
        for (int i = 0; i < have.length; i++) {
            if (have[i] != need[i]) {
                return have[i] > need[i];
            }
        }
        return true;
    }

    /** @return the versions this runner knows, in order */
    List<Migration> getMigrations() {
        return migrations;
    }

    /** @return the newest version this build contains, which the application needs */
    public int requiredVersion() {
        return migrations.get(migrations.size() - 1).version;
    }

    /**
     * Reads, without changing anything, the highest of this runner's versions recorded as successful
     * with every earlier one of its versions recorded too.
     *
     * @return applied version, or 0 if there is no history table or it is empty
     * @throws DataAccessException if the database is unreachable or a failed migration is recorded
     */
    public int appliedVersion() throws DataAccessException {
        try (Connection con = db.getConnection()) {
            if (!tableExists(con, HISTORY_TABLE)) {
                return 0;
            }
            Map<Integer, Applied> applied = readHistory(con);
            for (Applied a : applied.values()) {
                if (!a.success) {
                    throw new DataAccessException("Migration V" + a.version + " (" + a.script + ") is recorded as failed: "
                            + a.errorMessage);
                }
            }
            int version = 0;
            for (Migration m : migrations) {
                if (!applied.containsKey(m.version)) {
                    break;
                }
                version = m.version;
            }
            return version;
        } catch (SQLException e) {
            throw new DataAccessException("Cannot read " + HISTORY_TABLE + ": " + e.getMessage(), e);
        }
    }

    /**
     * Brings the connected database up to the newest version.
     *
     * @return one line per action taken (empty if the database was already current)
     * @throws DataAccessException if the server is too old, the schema does not match, a recorded
     *                             migration failed or changed, or a script fails
     */
    public List<String> migrate() throws DataAccessException {
        List<String> log = new ArrayList<>();
        try (Connection con = db.getConnection()) {
            String database = currentDatabase(con);
            requireSupportedServer(con);
            String lockName = LOCK_NAME + "." + database;
            lock(con, lockName);
            try {
                boolean historyExists = tableExists(con, HISTORY_TABLE);
                Map<Integer, Applied> applied = historyExists ? readHistory(con) : new TreeMap<>();
                verifyHistory(applied);
                if (applied.isEmpty()) {
                    boolean baselined = inspectUnversionedSchema(con, database);
                    try (PreparedStatement ps = con.prepareStatement(CREATE_HISTORY_SQL)) {
                        ps.execute();
                    }
                    if (baselined) {
                        Migration v1 = migrations.get(0);
                        recordBaseline(con, v1);
                        applied.put(v1.version, new Applied(v1.version, v1.script, v1.checksum, TYPE_BASELINE, true,
                                null));
                        log.add("V1 recorded as BASELINE: existing schema in '" + database + "' matches "
                                + v1.script);
                    }
                }
                for (Migration m : migrations) {
                    if (!applied.containsKey(m.version)) {
                        long ms = apply(con, m);
                        log.add("V" + m.version + " applied: " + m.script + " (" + ms + " ms)");
                    }
                }
            } finally {
                unlock(con, lockName);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Migration failed: " + e.getMessage(), e);
        }
        return log;
    }

    /**
     * Describes the migration state without changing anything.
     *
     * @return one line per known version and per recorded row
     * @throws DataAccessException on database failure
     */
    public List<String> status() throws DataAccessException {
        List<String> lines = new ArrayList<>();
        try (Connection con = db.getConnection()) {
            lines.add("Database: " + currentDatabase(con));
            Map<Integer, Applied> applied = tableExists(con, HISTORY_TABLE) ? readHistory(con) : new TreeMap<>();
            if (applied.isEmpty()) {
                lines.add("No migration history (" + HISTORY_TABLE + " missing or empty).");
            }
            for (Migration m : migrations) {
                Applied a = applied.get(m.version);
                String state = a == null ? "pending"
                        : (a.success ? a.type : "FAILED: " + a.errorMessage)
                        + (a.checksum.equals(m.checksum) ? "" : " (CHECKSUM CHANGED)");
                lines.add("V" + m.version + " " + m.script + ": " + state);
            }
            for (Applied a : applied.values()) {
                if (known(a.version) == null) {
                    lines.add("V" + a.version + " " + a.script + ": recorded but unknown to this build");
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Cannot read migration status: " + e.getMessage(), e);
        }
        return lines;
    }

    private void verifyHistory(Map<Integer, Applied> applied) throws DataAccessException {
        for (Applied a : applied.values()) {
            if (!a.success) {
                throw new DataAccessException("Migration V" + a.version + " (" + a.script + ") failed earlier: "
                        + a.errorMessage + ". MySQL DDL is not transactional, so part of it may be applied. "
                        + "Repair the schema by hand, delete that row from " + HISTORY_TABLE + ", then run again.");
            }
            Migration m = known(a.version);
            if (m == null) {
                throw new DataAccessException("Database records migration V" + a.version + " (" + a.script
                        + ") that this build does not know; refusing to run an older build's migrations.");
            }
            if (!m.checksum.equals(a.checksum)) {
                throw new DataAccessException("Migration V" + a.version + " (" + m.script
                        + ") has changed since it was recorded (checksum mismatch); applied scripts must not be edited.");
            }
        }
        for (int i = 1; i < migrations.size(); i++) {
            Migration m = migrations.get(i);
            if (applied.containsKey(m.version) && !applied.containsKey(migrations.get(i - 1).version)) {
                throw new DataAccessException("Migration history has a gap before V" + m.version);
            }
        }
    }

    /**
     * Decides how to treat a database without history; writes nothing.
     *
     * @return true if all baseline tables exist with the V1 structure; false if none exist
     */
    private boolean inspectUnversionedSchema(Connection con, String database) throws SQLException,
            DataAccessException {
        List<String> present = new ArrayList<>();
        List<String> absent = new ArrayList<>();
        for (String table : BaselineSchema.TABLES) {
            (tableExists(con, table) ? present : absent).add(table);
        }
        if (present.isEmpty()) {
            return false;
        }
        if (!absent.isEmpty()) {
            throw new DataAccessException("Database '" + database + "' has " + present.size() + " of "
                    + BaselineSchema.TABLES.size() + " baseline tables (missing: " + String.join(", ", absent)
                    + ") and no migration history; nothing was changed.");
        }
        List<String> diffs = BaselineSchema.compare(BaselineSchema.read(con));
        if (!diffs.isEmpty()) {
            throw new DataAccessException("Existing schema in '" + database + "' does not match "
                    + migrations.get(0).script + "; nothing was changed. Differences:\n - "
                    + String.join("\n - ", diffs));
        }
        return true;
    }

    private long apply(Connection con, Migration m) throws SQLException, DataAccessException {
        List<String> statements = splitStatements(m.sql);
        try (PreparedStatement ps = con.prepareStatement("INSERT INTO " + HISTORY_TABLE
                + " (version, description, script, checksum, type, success) VALUES (?, ?, ?, ?, ?, FALSE)")) {
            ps.setInt(1, m.version);
            ps.setString(2, m.description);
            ps.setString(3, m.script);
            ps.setString(4, m.checksum);
            ps.setString(5, TYPE_SQL);
            ps.executeUpdate();
        }
        long start = System.nanoTime();
        int index = 0;
        try {
            con.setAutoCommit(false);
            // The only plain Statement in the application: it runs the checksummed migration scripts from the
            // classpath (DDL and seed data, never request data). Every other query is a PreparedStatement.
            try (Statement st = con.createStatement()) {
                for (String sql : statements) {
                    index++;
                    st.execute(sql);
                }
            }
            con.commit();
        } catch (SQLException e) {
            rollbackQuietly(con);
            con.setAutoCommit(true);
            String error = "statement " + index + " of " + statements.size() + " failed (SQLState "
                    + e.getSQLState() + "): " + e.getMessage();
            try (PreparedStatement ps = con.prepareStatement("UPDATE " + HISTORY_TABLE
                    + " SET error_message = ?, finished_at = CURRENT_TIMESTAMP(3) WHERE version = ?")) {
                ps.setString(1, error.length() > MAX_ERROR_LENGTH ? error.substring(0, MAX_ERROR_LENGTH) : error);
                ps.setInt(2, m.version);
                ps.executeUpdate();
            }
            throw new DataAccessException("Migration V" + m.version + " (" + m.script + ") " + error
                    + ". MySQL DDL is not transactional, so earlier statements may be applied. Repair the schema, "
                    + "delete the V" + m.version + " row from " + HISTORY_TABLE + ", then run again.", e);
        }
        con.setAutoCommit(true);
        long ms = (System.nanoTime() - start) / 1_000_000;
        try (PreparedStatement ps = con.prepareStatement("UPDATE " + HISTORY_TABLE
                + " SET success = TRUE, finished_at = CURRENT_TIMESTAMP(3), execution_ms = ? WHERE version = ?")) {
            ps.setLong(1, ms);
            ps.setInt(2, m.version);
            ps.executeUpdate();
        }
        return ms;
    }

    private static void recordBaseline(Connection con, Migration v1) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("INSERT INTO " + HISTORY_TABLE
                + " (version, description, script, checksum, type, success, finished_at, execution_ms) "
                + "VALUES (?, ?, ?, ?, ?, TRUE, CURRENT_TIMESTAMP(3), 0)")) {
            ps.setInt(1, v1.version);
            ps.setString(2, v1.description);
            ps.setString(3, v1.script);
            ps.setString(4, v1.checksum);
            ps.setString(5, TYPE_BASELINE);
            ps.executeUpdate();
        }
    }

    private static Map<Integer, Applied> readHistory(Connection con) throws SQLException {
        Map<Integer, Applied> applied = new TreeMap<>();
        try (PreparedStatement ps = con.prepareStatement("SELECT version, script, checksum, type, success, "
                + "error_message FROM " + HISTORY_TABLE + " ORDER BY version");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                applied.put(rs.getInt(1), new Applied(rs.getInt(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getBoolean(5), rs.getString(6)));
            }
        }
        return applied;
    }

    private static String currentDatabase(Connection con) throws SQLException, DataAccessException {
        try (PreparedStatement ps = con.prepareStatement("SELECT DATABASE()"); ResultSet rs = ps.executeQuery()) {
            String name = rs.next() ? rs.getString(1) : null;
            if (name == null) {
                throw new DataAccessException("The JDBC URL does not select a database; refusing to migrate.");
            }
            return name;
        }
    }

    private static void requireSupportedServer(Connection con) throws SQLException, DataAccessException {
        try (PreparedStatement ps = con.prepareStatement("SELECT VERSION()"); ResultSet rs = ps.executeQuery()) {
            String version = rs.next() ? rs.getString(1) : null;
            if (!isSupportedServerVersion(version)) {
                throw new DataAccessException("MySQL server version " + version + " is not supported; "
                        + MIN_MAJOR + "." + MIN_MINOR + "." + MIN_PATCH + " or newer is required.");
            }
        }
    }

    private static boolean tableExists(Connection con, String table) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM information_schema.TABLES "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    private static void lock(Connection con, String lockName) throws SQLException, DataAccessException {
        try (PreparedStatement ps = con.prepareStatement("SELECT GET_LOCK(?, ?)")) {
            ps.setString(1, lockName);
            ps.setInt(2, LOCK_TIMEOUT_SECONDS);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || rs.getInt(1) != 1) {
                    throw new DataAccessException("Another migration run holds the lock " + lockName + "; try again.");
                }
            }
        }
    }

    private static void unlock(Connection con, String lockName) {
        try (PreparedStatement ps = con.prepareStatement("SELECT RELEASE_LOCK(?)")) {
            ps.setString(1, lockName);
            ps.executeQuery().close();
        } catch (SQLException e) {
            // The lock is released anyway when the connection closes.
        }
    }

    private static void rollbackQuietly(Connection con) {
        try {
            con.rollback();
        } catch (SQLException e) {
            // Nothing more to undo; the original failure is reported.
        }
    }
}

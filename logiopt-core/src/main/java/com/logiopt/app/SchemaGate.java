package com.logiopt.app;

import com.logiopt.exception.DataAccessException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Read-only startup check of schema_migrations. The application never migrates by itself: while the
 * database is behind the version this build needs (or cannot be checked), every request gets a
 * generic 503 page, and the exact MigrationTool command is written to the server log only (the page
 * reveals no database name, version or command). The check is repeated at most once per
 * {@code recheck} until it passes; after that it is not repeated. {@code onReady} runs once, the
 * first time the check passes (it seeds the administrator).
 */
public class SchemaGate {

    /** Read-only source of the applied schema version. */
    @FunctionalInterface
    public interface VersionProbe {
        /**
         * @return applied schema version
         * @throws DataAccessException if it cannot be read or a failed migration is recorded
         */
        int appliedVersion() throws DataAccessException;
    }

    /** Text of the 503 page; it names no database, version or command (those go to the server log). */
    public static final String PAGE_MESSAGE = "LogiOpt is temporarily unavailable because its database is not ready. "
            + "Please try again later. Administrators: see the server log.";

    private static final Logger LOG = Logger.getLogger(SchemaGate.class.getName());

    private final VersionProbe probe;
    private final int requiredVersion;
    private final String databaseName;
    private final Clock clock;
    private final Duration recheck;
    private final Runnable onReady;

    private boolean ready;
    private Instant lastCheck;
    private String message = PAGE_MESSAGE;

    /**
     * Creates the gate.
     *
     * @param probe           applied-version source
     * @param requiredVersion version this build needs
     * @param databaseName    configured database name (shown in the instructions)
     * @param clock           time source
     * @param recheck         minimum time between checks while not ready
     * @param onReady         action run once when the check first passes
     */
    public SchemaGate(VersionProbe probe, int requiredVersion, String databaseName, Clock clock, Duration recheck,
                      Runnable onReady) {
        if (probe == null || clock == null || recheck == null || onReady == null || requiredVersion < 1) {
            throw new IllegalArgumentException("Probe, clock, recheck interval, action and version are required");
        }
        this.probe = probe;
        this.requiredVersion = requiredVersion;
        this.databaseName = databaseName == null ? "<database>" : databaseName;
        this.clock = clock;
        this.recheck = recheck;
        this.onReady = onReady;
    }

    /**
     * Tells whether requests may be served, checking the database if the last check is older than
     * the recheck interval.
     *
     * @return true once the applied version is at least the required one
     */
    public boolean isReady() {
        boolean becameReady = false;
        synchronized (this) {
            if (ready) {
                return true;
            }
            Instant now = clock.instant();
            if (lastCheck != null && now.isBefore(lastCheck.plus(recheck))) {
                return false;
            }
            lastCheck = now;
            try {
                int applied = probe.appliedVersion();
                if (applied >= requiredVersion) {
                    if (applied > requiredVersion) {
                        LOG.warning("Database schema version " + applied + " is newer than this build expects ("
                                + requiredVersion + ").");
                    }
                    ready = true;
                    becameReady = true;
                    message = null;
                    LOG.info("Database schema is at version " + applied + "; requests are served.");
                } else {
                    message = PAGE_MESSAGE;
                    LOG.warning("Database schema of '" + databaseName + "' is at version " + applied
                            + ", but this build needs version " + requiredVersion + ". LogiOpt does not migrate by "
                            + "itself; requests get HTTP 503 until you run: " + command(""));
                }
            } catch (DataAccessException e) {
                message = PAGE_MESSAGE;
                LOG.log(Level.WARNING, "Database schema of '" + databaseName + "' could not be verified; requests get "
                        + "HTTP 503. Inspect it with: " + command(" --status"), e);
            }
        }
        if (!becameReady) {
            return false;
        }
        try {
            onReady.run();
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Start-up action after the schema check failed", e);
        }
        return true;
    }

    private String command(String extra) {
        return "java -cp \"logiopt-web\\target\\logiopt\\WEB-INF\\classes;logiopt-web\\target\\logiopt\\WEB-INF\\lib\\*\" "
                + "com.logiopt.dao.MigrationTool --target=main --confirm-database=" + databaseName + extra
                + " (see README section 1)";
    }

    /** @return the generic 503 page text while not ready, or null when ready */
    public synchronized String getMessage() {
        return message;
    }
}

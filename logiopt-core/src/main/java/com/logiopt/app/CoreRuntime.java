package com.logiopt.app;

import com.logiopt.dao.BookingDAO;
import com.logiopt.dao.DashboardDAO;
import com.logiopt.dao.DBConnection;
import com.logiopt.dao.MigrationRunner;
import com.logiopt.dao.NotificationDAO;
import com.logiopt.dao.PaymentDAO;
import com.logiopt.dao.ReportDAO;
import com.logiopt.dao.RouteDAO;
import com.logiopt.dao.ShipmentDAO;
import com.logiopt.dao.TrackingDAO;
import com.logiopt.dao.UserDAO;
import com.logiopt.exception.DataAccessException;
import com.logiopt.report.ReportService;
import com.logiopt.service.AuthService;
import com.logiopt.service.BookingService;
import com.logiopt.service.CostService;
import com.logiopt.service.DashboardService;
import com.logiopt.service.DurationService;
import com.logiopt.service.EligibilityService;
import com.logiopt.service.EmissionService;
import com.logiopt.service.HistoryService;
import com.logiopt.service.LoginThrottle;
import com.logiopt.service.NotificationService;
import com.logiopt.service.PasswordHasher;
import com.logiopt.service.PaymentService;
import com.logiopt.service.PlanningService;
import com.logiopt.service.RecommendationService;
import com.logiopt.service.RouteEvaluator;
import com.logiopt.service.ShipmentService;
import com.logiopt.service.ShipmentValidator;
import com.logiopt.service.TrackingService;
import com.logiopt.service.TrackingSimulator;
import com.mysql.cj.jdbc.AbandonedConnectionCleanupThread;

import java.nio.file.Path;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.util.Enumeration;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Builds and owns the services shared by every front end (web and desktop): one object graph per
 * process, created from the same environment variables, with a read-only schema check, administrator
 * seeding from LOGIOPT_ADMIN_PASSWORD, the optional tracking simulator and an orderly shutdown.
 * It never migrates the database.
 */
public final class CoreRuntime {

    /** Minimum time between schema checks of the web front end's gate while the schema is behind. */
    public static final Duration SCHEMA_RECHECK = Duration.ofSeconds(10);

    private static final Logger LOG = Logger.getLogger(CoreRuntime.class.getName());

    private final DBConnection db;
    private final Function<String, String> env;
    private final String migrationClasspath;
    private final MigrationRunner migrations;
    private final AppServices services;
    private final TrackingService tracking;

    private TrackingSimulator simulator;
    private boolean shutdown;

    /**
     * Creates the services. Opens no connection; nothing touches the database until it is used.
     *
     * @param db                 database connections
     * @param env                environment lookup (System::getenv in production)
     * @param reportsDir         directory for report files
     * @param migrationClasspath classpath shown in the logged MigrationTool command
     * @throws DataAccessException if the migration scripts are missing from the build
     */
    public CoreRuntime(DBConnection db, Function<String, String> env, Path reportsDir, String migrationClasspath)
            throws DataAccessException {
        if (db == null || env == null || reportsDir == null || migrationClasspath == null) {
            throw new IllegalArgumentException("Database, environment, reports directory and classpath are required");
        }
        this.db = db;
        this.env = env;
        this.migrationClasspath = migrationClasspath;
        this.migrations = new MigrationRunner(db);
        ShipmentValidator validator = new ShipmentValidator(Clock.systemDefaultZone());
        RecommendationService recommendations = new RecommendationService();
        RouteEvaluator evaluator = new RouteEvaluator(new CostService(), new DurationService(), new EmissionService());
        PlanningService planning = new PlanningService(validator, new EligibilityService(), evaluator,
                recommendations);
        ReportService reports = new ReportService(reportsDir, Clock.systemDefaultZone(), ReportService.DEFAULT_THREADS,
                new ReportDAO(db));
        UserDAO userDao = new UserDAO(db);
        AuthService auth = new AuthService(userDao,
                new PasswordHasher(PasswordHasher.DEFAULT_ITERATIONS, new SecureRandom()),
                new LoginThrottle(Clock.systemUTC(), LoginThrottle.DEFAULT_MAX_FAILURES, LoginThrottle.DEFAULT_WINDOW,
                        LoginThrottle.DEFAULT_LOCKOUT));
        SchemaGate gate = new SchemaGate(migrations::appliedVersion, migrations.requiredVersion(), db.getDatabaseName(),
                Clock.systemUTC(), SCHEMA_RECHECK, this::onSchemaReady);
        ShipmentDAO shipmentDao = new ShipmentDAO(db);
        RouteDAO routeDao = new RouteDAO(db);
        BookingDAO bookingDao = new BookingDAO(db);
        NotificationService notifications = new NotificationService(new NotificationDAO(db), userDao,
                NotificationService.DEFAULT_THREADS, NotificationService.DEFAULT_QUEUE_CAPACITY);
        BookingService bookings = new BookingService(shipmentDao, routeDao, bookingDao, planning,
                Clock.systemDefaultZone(), notifications);
        PaymentService payments = new PaymentService(bookingDao, new PaymentDAO(db), notifications);
        this.tracking = new TrackingService(bookingDao, new TrackingDAO(db), notifications);
        this.services = new AppServices(shipmentDao, routeDao, validator, recommendations, planning, reports, auth,
                gate, bookings, payments, tracking, notifications, new ShipmentService(shipmentDao, validator),
                new DashboardService(new DashboardDAO(db), notifications),
                new HistoryService(shipmentDao, bookingDao, reports));
        LOG.info("LogiOpt services initialized; reports directory " + reports.getReportsDir());
    }

    /**
     * Creates the runtime from LOGIOPT_DB_URL / _USER / _PASSWORD (or db.properties) and
     * LOGIOPT_REPORTS_DIR, exactly as the web application does.
     *
     * @param migrationClasspath classpath shown in the logged MigrationTool command
     * @return runtime
     * @throws DataAccessException if the configuration is invalid or the migration scripts are missing
     */
    public static CoreRuntime fromEnvironment(String migrationClasspath) throws DataAccessException {
        return new CoreRuntime(DBConnection.fromConfiguration(), System::getenv,
                ReportService.configuredDirectory().toAbsolutePath(), migrationClasspath);
    }

    /** @return the shared services */
    public AppServices services() {
        return services;
    }

    /** @return name of the configured database */
    public String databaseName() {
        return db.getDatabaseName();
    }

    /** @return schema version this build needs */
    public int requiredVersion() {
        return migrations.requiredVersion();
    }

    /**
     * Read-only check of schema_migrations. When the database cannot be used, the exact MigrationTool
     * command is written to the log (never returned, so it is not shown to users).
     *
     * @return what was found
     */
    public SchemaStatus checkSchema() {
        int required = migrations.requiredVersion();
        try (Connection ignored = db.getConnection()) {
            LOG.fine("Database connection for the schema check opened");
        } catch (DataAccessException | SQLException e) {
            LOG.log(Level.WARNING, "Database '" + databaseName() + "' could not be reached for the schema check", e);
            return new SchemaStatus(SchemaStatus.State.UNREACHABLE, null, required);
        }
        int applied;
        try {
            applied = migrations.appliedVersion();
        } catch (DataAccessException e) {
            LOG.log(Level.WARNING, "Database schema of '" + databaseName() + "' could not be verified. Inspect it with: "
                    + migrationCommand(" --status"), e);
            return new SchemaStatus(SchemaStatus.State.FAILED, null, required);
        }
        if (applied < required) {
            LOG.warning("Database schema of '" + databaseName() + "' is at version " + applied + ", but this build "
                    + "needs version " + required + ". LogiOpt does not migrate by itself; run: " + migrationCommand(""));
            return new SchemaStatus(SchemaStatus.State.BEHIND, applied, required);
        }
        if (applied > required) {
            LOG.warning("Database schema version " + applied + " is newer than this build expects (" + required + ").");
            return new SchemaStatus(SchemaStatus.State.NEWER, applied, required);
        }
        LOG.info("Database schema is at version " + applied + ".");
        return new SchemaStatus(SchemaStatus.State.CURRENT, applied, required);
    }

    /** @return the MigrationTool command for the configured database, with this front end's classpath */
    String migrationCommand(String extra) {
        return "java -cp \"" + migrationClasspath + "\" com.logiopt.dao.MigrationTool --target=main --confirm-database="
                + databaseName() + extra + " (see README section 1)";
    }

    /**
     * Seeds the administrator from LOGIOPT_ADMIN_PASSWORD (see {@link AuthService#seedAdmin(String)}).
     *
     * @return what was done
     * @throws DataAccessException on database failure
     */
    public AuthService.AdminSeedResult seedAdmin() throws DataAccessException {
        return services.getAuthService().seedAdmin(env.apply(AuthService.ENV_ADMIN_PASSWORD));
    }

    /**
     * Runs once after the schema check passes: seeds the administrator and records report jobs left
     * unfinished by an earlier run as FAILED. Failures are logged, never thrown.
     */
    public void onSchemaReady() {
        try {
            seedAdmin();
        } catch (DataAccessException e) {
            LOG.log(Level.SEVERE, "Administrator could not be seeded", e);
        }
        try {
            services.getReportService().failInterruptedJobs();
        } catch (DataAccessException e) {
            LOG.log(Level.SEVERE, "Unfinished report jobs could not be checked", e);
        }
    }

    /**
     * Starts the tracking simulator only if LOGIOPT_SIM_INTERVAL_SECONDS is set to a valid value.
     *
     * @return true if it was started
     */
    public synchronized boolean startSimulatorFromEnvironment() {
        if (shutdown || simulator != null) {
            return false;
        }
        Duration interval;
        try {
            interval = TrackingSimulator.parseInterval(env.apply(TrackingSimulator.ENV_INTERVAL));
        } catch (IllegalArgumentException e) {
            LOG.warning(e.getMessage() + "; tracking simulator is off.");
            return false;
        }
        if (interval == null) {
            LOG.info(TrackingSimulator.ENV_INTERVAL + " is not set; tracking simulator is off.");
            return false;
        }
        simulator = new TrackingSimulator(tracking::advanceAll, interval);
        simulator.start();
        return true;
    }

    /** @return true once {@link #shutdown()} has run */
    public synchronized boolean isShutdown() {
        return shutdown;
    }

    /** Stops the simulator, the notification executor and the report executor. Safe to call twice. */
    public synchronized void shutdown() {
        if (shutdown) {
            return;
        }
        shutdown = true;
        if (simulator != null) {
            simulator.shutdown();
            simulator = null;
        }
        services.getNotificationService().shutdown();
        services.getReportService().shutdown();
    }

    /**
     * Deregisters the JDBC drivers loaded by the given class loader and stops Connector/J's cleanup
     * thread, so an undeploy or exit does not leak them.
     *
     * @param loader class loader whose drivers are deregistered
     */
    public static void deregisterJdbcDrivers(ClassLoader loader) {
        Enumeration<Driver> drivers = DriverManager.getDrivers();
        while (drivers.hasMoreElements()) {
            Driver driver = drivers.nextElement();
            if (driver.getClass().getClassLoader() == loader) {
                try {
                    DriverManager.deregisterDriver(driver);
                    LOG.info("Deregistered JDBC driver " + driver.getClass().getName());
                } catch (SQLException e) {
                    LOG.log(Level.WARNING, "Could not deregister JDBC driver " + driver.getClass().getName(), e);
                }
            }
        }
        AbandonedConnectionCleanupThread.checkedShutdown();
    }
}

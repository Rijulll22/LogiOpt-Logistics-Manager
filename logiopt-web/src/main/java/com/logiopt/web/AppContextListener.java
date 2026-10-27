package com.logiopt.web;

import com.logiopt.app.AppServices;
import com.logiopt.app.CoreRuntime;
import com.logiopt.dao.DBConnection;
import com.logiopt.exception.DataAccessException;
import com.logiopt.report.ReportService;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

import java.io.File;
import java.util.logging.Logger;

/**
 * Creates the shared {@link CoreRuntime} once at startup and keeps its services in the servlet
 * context; shuts it down and deregisters the JDBC driver on undeploy.
 */
@WebListener
public class AppContextListener implements ServletContextListener {

    /** Exploded WAR folder, relative to the project folder. */
    private static final String EXPLODED_WAR = String.join(File.separator, "logiopt-web", "target", "logiopt", "WEB-INF");
    /** Classpath of MigrationTool from the exploded WAR, shown in the logged command (separators of this OS). */
    static final String MIGRATION_CLASSPATH = EXPLODED_WAR + File.separator + "classes" + File.pathSeparator
            + EXPLODED_WAR + File.separator + "lib" + File.separator + "*";

    private static final Logger LOG = Logger.getLogger(AppContextListener.class.getName());

    private CoreRuntime runtime;

    @Override
    public void contextInitialized(ServletContextEvent event) {
        DBConnection db;
        try {
            db = DBConnection.fromConfiguration();
        } catch (DataAccessException e) {
            throw new IllegalStateException("LogiOpt database configuration is invalid: " + e.getMessage(), e);
        }
        try {
            runtime = new CoreRuntime(db, System::getenv, ReportService.configuredDirectory().toAbsolutePath(),
                    MIGRATION_CLASSPATH);
        } catch (DataAccessException e) {
            throw new IllegalStateException("LogiOpt migration scripts are missing from the build: " + e.getMessage(), e);
        }
        AppServices services = runtime.services();
        event.getServletContext().setAttribute(AppServices.ATTRIBUTE, services);
        runtime.startSimulatorFromEnvironment();
        if (!services.getSchemaGate().isReady()) {
            LOG.warning("Requests will get HTTP 503 until the database schema is migrated: "
                    + services.getSchemaGate().getMessage());
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        if (runtime != null) {
            runtime.shutdown();
            runtime = null;
        }
        event.getServletContext().removeAttribute(AppServices.ATTRIBUTE);
        CoreRuntime.deregisterJdbcDrivers(Thread.currentThread().getContextClassLoader());
    }
}

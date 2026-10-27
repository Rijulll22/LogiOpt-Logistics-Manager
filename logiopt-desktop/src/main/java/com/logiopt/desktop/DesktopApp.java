package com.logiopt.desktop;

import com.logiopt.app.CoreRuntime;
import com.logiopt.app.SchemaStatus;
import com.logiopt.desktop.support.BackgroundRunner;
import com.logiopt.desktop.support.ExecutorBackgroundRunner;
import com.logiopt.desktop.support.Navigator;
import com.logiopt.desktop.support.Session;
import com.logiopt.desktop.view.DesktopWindow;
import com.logiopt.desktop.view.Dialogs;
import com.logiopt.exception.DataAccessException;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The JavaFX application. Start-up: build the shared {@link CoreRuntime} from the environment, check
 * the schema read-only before any window opens (a modal dialog with Retry or Exit if the database is
 * behind, has a failed migration or cannot be reached), seed the administrator, then open the window.
 * It never migrates. Exit stops the pollers and shuts down the background runner and the runtime.
 */
public final class DesktopApp extends Application {

    /** MigrationTool classpath from the project folder, shown in the logged command. */
    static final String MIGRATION_CLASSPATH = "logiopt-desktop\\target\\lib\\*";

    private static final Logger LOG = Logger.getLogger(DesktopApp.class.getName());

    private CoreRuntime runtime;
    private Exception configurationError;
    private BackgroundRunner runner;
    private DesktopWindow window;

    @Override
    public void init() {
        try {
            runtime = CoreRuntime.fromEnvironment(MIGRATION_CLASSPATH);
        } catch (DataAccessException | RuntimeException e) {
            LOG.log(Level.SEVERE, "LogiOpt could not be configured", e);
            configurationError = e;
        }
    }

    @Override
    public void start(Stage stage) {
        Platform.setImplicitExit(false);
        runner = new ExecutorBackgroundRunner(ExecutorBackgroundRunner.DEFAULT_PREFIX, 2, Platform::runLater);
        if (configurationError != null) {
            Dialogs.fatal("LogiOpt is not configured.", "Set LOGIOPT_DB_URL, LOGIOPT_DB_USER and LOGIOPT_DB_PASSWORD "
                    + "for this window (see the README), then start LogiOpt again. Details are in the log.");
            Platform.exit();
            return;
        }
        checkSchema(stage);
    }

    private void checkSchema(Stage stage) {
        runner.run(runtime::checkSchema, status -> {
            if (status.isUsable()) {
                prepare(stage);
            } else {
                refuse(stage, status);
            }
        }, error -> {
            LOG.log(Level.SEVERE, "Schema check failed", error);
            refuse(stage, new SchemaStatus(SchemaStatus.State.UNREACHABLE, null, runtime.requiredVersion()));
        });
    }

    private void refuse(Stage stage, SchemaStatus status) {
        if (Dialogs.askRetry(status)) {
            checkSchema(stage);
        } else {
            Platform.exit();
        }
    }

    private void prepare(Stage stage) {
        runner.run(() -> {
            runtime.onSchemaReady();
            runtime.startSimulatorFromEnvironment();
            return Boolean.TRUE;
        }, ok -> open(stage), error -> {
            LOG.log(Level.SEVERE, "Start-up actions failed", error);
            open(stage);
        });
    }

    private void open(Stage stage) {
        Session session = new Session();
        window = new DesktopWindow(stage, runtime.services(), runner, session);
        Navigator navigator = new Navigator(session, window);
        window.setNavigator(navigator);
        stage.setOnCloseRequest(e -> Platform.exit());
        navigator.start();
        window.show();
    }

    @Override
    public void stop() {
        if (window != null) {
            window.close();
        }
        if (runner != null) {
            runner.shutdown();
        }
        if (runtime != null) {
            runtime.shutdown();
        }
        CoreRuntime.deregisterJdbcDrivers(DesktopApp.class.getClassLoader());
        LOG.info("LogiOpt desktop client stopped");
    }
}

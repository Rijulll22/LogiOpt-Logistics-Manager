package com.logiopt.desktop.tools;

import javafx.application.Platform;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/**
 * Starts the JavaFX toolkit without showing a window (for the screenshot tool and the view smoke
 * tests) and runs work on the JavaFX thread. The toolkit needs a display; without one,
 * {@link #start(Duration)} returns false and {@link #failure()} says why.
 */
public final class FxToolkit {

    private static final long CALL_TIMEOUT_SECONDS = 60;

    private static Boolean started;
    private static String failure = "";

    private FxToolkit() {
    }

    /**
     * @param timeout how long to wait for the toolkit
     * @return true if the toolkit runs (started now or earlier)
     */
    public static synchronized boolean start(Duration timeout) {
        if (started != null) {
            return started;
        }
        CountDownLatch ready = new CountDownLatch(1);
        try {
            Platform.startup(ready::countDown);
        } catch (IllegalStateException alreadyRunning) {
            ready.countDown();
        } catch (RuntimeException | Error e) {
            failure = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
            started = false;
            return false;
        }
        try {
            started = ready.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            started = false;
        }
        if (started) {
            Platform.setImplicitExit(false);
        } else if (failure.isEmpty()) {
            failure = "the JavaFX toolkit did not start within " + timeout.toSeconds() + " s";
        }
        return started;
    }

    /** @return why the toolkit could not start, or an empty string */
    public static synchronized String failure() {
        return failure;
    }

    /**
     * Runs work on the JavaFX thread and waits for it.
     *
     * @param work work
     * @param <T>  result type
     * @return result
     * @throws Exception the work's failure
     */
    public static <T> T call(Callable<T> work) throws Exception {
        if (Platform.isFxApplicationThread()) {
            return work.call();
        }
        FutureTask<T> task = new FutureTask<>(work);
        Platform.runLater(task);
        try {
            return task.get(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw e;
        }
    }
}

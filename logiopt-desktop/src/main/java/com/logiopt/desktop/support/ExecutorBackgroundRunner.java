package com.logiopt.desktop.support;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link BackgroundRunner} on a fixed pool of named daemon threads ({@code <prefix>-1}, ...). The
 * callbacks go to {@code uiExecutor} (Platform::runLater in the desktop client), so they always run on
 * the UI thread. After {@link #shutdown()} new work is dropped and logged.
 */
public final class ExecutorBackgroundRunner implements BackgroundRunner {

    /** Default name prefix of the worker threads. */
    public static final String DEFAULT_PREFIX = "logiopt-desktop-io";

    private static final Logger LOG = Logger.getLogger(ExecutorBackgroundRunner.class.getName());
    private static final long SHUTDOWN_WAIT_SECONDS = 5;

    private final ThreadPoolExecutor executor;
    private final Executor uiExecutor;

    /**
     * @param threadPrefix worker thread name prefix
     * @param threads      number of worker threads (at least 1)
     * @param uiExecutor   executor that runs callbacks on the UI thread
     */
    public ExecutorBackgroundRunner(String threadPrefix, int threads, Executor uiExecutor) {
        if (threadPrefix == null || threadPrefix.isBlank() || threads < 1 || uiExecutor == null) {
            throw new IllegalArgumentException("Thread prefix, thread count and UI executor are required");
        }
        AtomicInteger counter = new AtomicInteger();
        this.executor = new ThreadPoolExecutor(threads, threads, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(),
                runnable -> {
                    Thread thread = new Thread(runnable, threadPrefix + "-" + counter.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                });
        this.uiExecutor = uiExecutor;
    }

    @Override
    public <T> void run(Work<T> work, Consumer<? super T> onSuccess, Consumer<? super Throwable> onFailure) {
        try {
            executor.execute(() -> {
                T result;
                try {
                    result = work.call();
                } catch (Exception | Error e) {
                    LOG.log(Level.FINE, "Background work failed", e);
                    deliver(() -> onFailure.accept(e));
                    return;
                }
                deliver(() -> onSuccess.accept(result));
            });
        } catch (RejectedExecutionException e) {
            LOG.info("Background work ignored because the client is shutting down");
        }
    }

    private void deliver(Runnable callback) {
        try {
            uiExecutor.execute(() -> {
                try {
                    callback.run();
                } catch (RuntimeException e) {
                    LOG.log(Level.SEVERE, "A UI callback failed", e);
                }
            });
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "A result could not be delivered to the UI thread", e);
        }
    }

    /** @return true once shut down */
    public boolean isShutdown() {
        return executor.isShutdown();
    }

    /**
     * Waits for queued and running work to finish (for tests and orderly exit).
     *
     * @param timeoutMillis maximum wait
     * @return true if idle within the time
     * @throws InterruptedException if interrupted
     */
    public boolean awaitIdle(long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (executor.getActiveCount() > 0 || !executor.getQueue().isEmpty()) {
            if (System.currentTimeMillis() > deadline) {
                return false;
            }
            Thread.sleep(10);
        }
        return true;
    }

    @Override
    public void shutdown() {
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)) {
                LOG.warning("Background work did not stop within " + SHUTDOWN_WAIT_SECONDS + " s");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

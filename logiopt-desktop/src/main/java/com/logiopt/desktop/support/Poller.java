package com.logiopt.desktop.support;

import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runs one task repeatedly on a single named daemon thread, with a fixed delay between the end of one
 * run and the start of the next, so runs never overlap. A failing run is logged and the next one still
 * happens. {@link #stop()} ends the thread; a stopped poller cannot be started again.
 */
public final class Poller {

    private static final Logger LOG = Logger.getLogger(Poller.class.getName());

    private final String threadName;
    private final Duration delay;
    private final Runnable task;
    private ScheduledExecutorService executor;
    private volatile Thread worker;
    private boolean stopped;

    /**
     * @param threadName name of the polling thread
     * @param delay      pause between runs (the first run starts at once)
     * @param task       work done on the polling thread
     */
    public Poller(String threadName, Duration delay, Runnable task) {
        if (threadName == null || delay == null || delay.isNegative() || delay.isZero() || task == null) {
            throw new IllegalArgumentException("A thread name, a positive delay and a task are required");
        }
        this.threadName = threadName;
        this.delay = delay;
        this.task = task;
    }

    /** Starts polling; does nothing if already started or stopped. */
    public synchronized void start() {
        if (executor != null || stopped) {
            return;
        }
        ScheduledThreadPoolExecutor pool = new ScheduledThreadPoolExecutor(1, r -> {
            Thread t = new Thread(r, threadName);
            t.setDaemon(true);
            worker = t;
            return t;
        });
        pool.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        executor = pool;
        executor.scheduleWithFixedDelay(this::runOnce, 0, delay.toMillis(), TimeUnit.MILLISECONDS);
    }

    private void runOnce() {
        try {
            task.run();
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Background poll on " + threadName + " failed", e);
        }
    }

    /** Stops polling and ends the thread (a run in progress finishes). Safe to call twice. */
    public synchronized void stop() {
        stopped = true;
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    /** @return true between {@link #start()} and {@link #stop()} */
    public synchronized boolean isRunning() {
        return executor != null && !stopped;
    }

    /**
     * @param timeout how long to wait
     * @return true if the polling thread has ended (or never started)
     * @throws InterruptedException if interrupted while waiting
     */
    public boolean awaitStopped(Duration timeout) throws InterruptedException {
        ScheduledExecutorService current;
        synchronized (this) {
            current = executor;
        }
        if (current == null) {
            return true;
        }
        long deadline = System.nanoTime() + timeout.toNanos();
        if (!current.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            return false;
        }
        Thread thread = worker;
        if (thread != null) {
            thread.join(Math.max(1, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())));
            return !thread.isAlive();
        }
        return true;
    }
}

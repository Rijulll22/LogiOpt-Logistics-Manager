package com.logiopt.service;

import com.logiopt.util.Strings;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Optional automatic tracking progress, off by default. Only when LOGIOPT_SIM_INTERVAL_SECONDS is set
 * does the application create one of these; it then calls the same idempotent method as the admin
 * "Advance" button ({@link TrackingService#advanceAll}, which uses {@link TrackingService#advance}) on
 * one named daemon thread, at a fixed delay. A failing pass is logged and the schedule continues.
 * Call {@link #shutdown} on undeploy.
 */
public class TrackingSimulator {

    /** Environment variable enabling the simulator: seconds between passes (positive integer). */
    public static final String ENV_INTERVAL = "LOGIOPT_SIM_INTERVAL_SECONDS";
    /** Name of the scheduler thread. */
    public static final String THREAD_NAME = "logiopt-tracking-sim";

    private static final Logger LOG = Logger.getLogger(TrackingSimulator.class.getName());
    private static final long SHUTDOWN_WAIT_SECONDS = 5;

    /** One simulation pass; returns the number of milestones recorded. */
    @FunctionalInterface
    public interface Pass {
        /**
         * @return milestones recorded
         * @throws Exception on failure (logged; the schedule continues)
         */
        int run() throws Exception;
    }

    private final Pass pass;
    private final Duration initialDelay;
    private final Duration interval;
    private ScheduledExecutorService scheduler;

    /**
     * Creates a simulator that first runs after one interval.
     *
     * @param pass     the pass to run, normally {@code trackingService::advanceAll}
     * @param interval time between passes
     */
    public TrackingSimulator(Pass pass, Duration interval) {
        this(pass, interval, interval);
    }

    TrackingSimulator(Pass pass, Duration initialDelay, Duration interval) {
        if (pass == null || interval == null || initialDelay == null || interval.isNegative() || interval.isZero()
                || initialDelay.isNegative()) {
            throw new IllegalArgumentException("A pass and a positive interval are required");
        }
        this.pass = pass;
        this.initialDelay = initialDelay;
        this.interval = interval;
    }

    /**
     * Reads the interval from the environment variable's value.
     *
     * @param value value of LOGIOPT_SIM_INTERVAL_SECONDS
     * @return interval, or null if unset or blank (simulator off)
     * @throws IllegalArgumentException if set but not a positive whole number of seconds
     */
    public static Duration parseInterval(String value) {
        if (Strings.isBlank(value)) {
            return null;
        }
        long seconds;
        try {
            seconds = Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(ENV_INTERVAL + " must be a positive whole number of seconds", e);
        }
        if (seconds < 1) {
            throw new IllegalArgumentException(ENV_INTERVAL + " must be a positive whole number of seconds");
        }
        return Duration.ofSeconds(seconds);
    }

    /** Starts the schedule (once). */
    public synchronized void start() {
        if (scheduler != null) {
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread t = new Thread(runnable, THREAD_NAME);
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(this::tick, initialDelay.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS);
        LOG.info("Tracking simulator started; one pass every " + interval.getSeconds() + " s");
    }

    void tick() {
        try {
            int recorded = pass.run();
            LOG.fine("Tracking simulator recorded " + recorded + " milestones");
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Tracking simulator pass failed; the schedule continues", e);
        }
    }

    /** @return true between {@link #start} and {@link #shutdown} */
    public synchronized boolean isRunning() {
        return scheduler != null && !scheduler.isShutdown();
    }

    /** Stops the schedule and waits briefly for a running pass. */
    public synchronized void shutdown() {
        if (scheduler == null) {
            return;
        }
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        LOG.info("Tracking simulator stopped");
    }
}

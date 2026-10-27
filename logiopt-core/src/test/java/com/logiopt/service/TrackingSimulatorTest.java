package com.logiopt.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The optional scheduler: off unless configured, runs passes on one named daemon thread, survives a
 * failing pass and stops on shutdown. Uses latches, never sleeps.
 */
class TrackingSimulatorTest {

    @Test
    void offUnlessAPositiveIntervalIsConfigured() {
        assertNull(TrackingSimulator.parseInterval(null));
        assertNull(TrackingSimulator.parseInterval("  "));
        assertEquals(Duration.ofSeconds(30), TrackingSimulator.parseInterval(" 30 "));
        assertThrows(IllegalArgumentException.class, () -> TrackingSimulator.parseInterval("0"));
        assertThrows(IllegalArgumentException.class, () -> TrackingSimulator.parseInterval("-5"));
        assertThrows(IllegalArgumentException.class, () -> TrackingSimulator.parseInterval("fast"));
        assertEquals("LOGIOPT_SIM_INTERVAL_SECONDS", TrackingSimulator.ENV_INTERVAL);
    }

    @Test
    void runsPassesOnANamedDaemonThreadSurvivesFailuresAndStops() throws Exception {
        CountDownLatch threePasses = new CountDownLatch(3);
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<Thread> thread = new AtomicReference<>();
        TrackingSimulator simulator = new TrackingSimulator(() -> {
            thread.set(Thread.currentThread());
            threePasses.countDown();
            if (calls.incrementAndGet() == 1) {
                throw new IllegalStateException("first pass fails");
            }
            return 1;
        }, Duration.ZERO, Duration.ofMillis(10));
        assertFalse(simulator.isRunning());
        simulator.start();
        simulator.start();
        assertTrue(simulator.isRunning());
        assertTrue(threePasses.await(10, TimeUnit.SECONDS), "the schedule continued after a failing pass");
        assertEquals(TrackingSimulator.THREAD_NAME, thread.get().getName());
        assertTrue(thread.get().isDaemon());
        simulator.shutdown();
        assertFalse(simulator.isRunning());
        thread.get().join(TimeUnit.SECONDS.toMillis(10));
        assertFalse(thread.get().isAlive(), "the scheduler thread ended, so no pass can run any more");
    }

    @Test
    void invalidConstructionRejected() {
        assertThrows(IllegalArgumentException.class, () -> new TrackingSimulator(null, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> new TrackingSimulator(() -> 0, Duration.ZERO));
    }
}

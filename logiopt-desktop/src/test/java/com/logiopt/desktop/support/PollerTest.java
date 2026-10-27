package com.logiopt.desktop.support;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The repeating background task behind the unread badge and the report status: latches, no sleeps. */
class PollerTest {

    private static final long WAIT = 10;

    @Test
    void runsOnOneNamedDaemonThreadNeverOverlapsAndStops() throws Exception {
        AtomicInteger running = new AtomicInteger();
        AtomicInteger most = new AtomicInteger();
        AtomicReference<Thread> thread = new AtomicReference<>();
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch three = new CountDownLatch(3);
        Poller poller = new Poller("test-poller", Duration.ofMillis(1), () -> {
            most.accumulateAndGet(running.incrementAndGet(), Math::max);
            thread.set(Thread.currentThread());
            firstEntered.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            running.decrementAndGet();
            three.countDown();
        });
        poller.start();
        poller.start();
        assertTrue(firstEntered.await(WAIT, TimeUnit.SECONDS));
        assertEquals(1, running.get(), "a slow run is never joined by a second one");
        release.countDown();
        assertTrue(three.await(WAIT, TimeUnit.SECONDS));
        assertEquals(1, most.get());
        assertEquals("test-poller", thread.get().getName());
        assertTrue(thread.get().isDaemon());
        poller.stop();
        assertTrue(poller.awaitStopped(Duration.ofSeconds(WAIT)));
        assertFalse(poller.isRunning());
        assertFalse(thread.get().isAlive());
        poller.start();
        assertFalse(poller.isRunning(), "a stopped poller stays stopped");
    }

    @Test
    void aFailingRunIsLoggedAndTheNextOneStillHappens() throws Exception {
        CountDownLatch two = new CountDownLatch(2);
        Poller poller = new Poller("failing-poller", Duration.ofMillis(1), () -> {
            two.countDown();
            throw new IllegalStateException("simulated failure");
        });
        poller.start();
        assertTrue(two.await(WAIT, TimeUnit.SECONDS));
        poller.stop();
        assertTrue(poller.awaitStopped(Duration.ofSeconds(WAIT)));
    }

    @Test
    void refusesMissingArguments() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> new Poller(null, Duration.ofSeconds(1), () -> { }));
        assertThrows(IllegalArgumentException.class, () -> new Poller("p", Duration.ZERO, () -> { }));
        assertThrows(IllegalArgumentException.class, () -> new Poller("p", Duration.ofSeconds(1), null));
        assertTrue(new Poller("p", Duration.ofSeconds(1), () -> { }).awaitStopped(Duration.ZERO), "never started");
    }
}

package com.logiopt.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginThrottleTest {

    private static final String IP = "192.0.2.1";

    /** Clock the test moves forward explicitly; no sleeps. */
    static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-25T10:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final MutableClock clock = new MutableClock();
    private final LoginThrottle throttle = new LoginThrottle(clock, LoginThrottle.DEFAULT_MAX_FAILURES,
            LoginThrottle.DEFAULT_WINDOW, LoginThrottle.DEFAULT_LOCKOUT);

    private void fail(String email, String ip, int times) {
        for (int i = 0; i < times; i++) {
            throttle.recordFailure(email, ip);
        }
    }

    @Test
    void fifthFailureWithinFiveMinutesLocksForFiveMinutes() {
        fail("a@example.test", IP, 4);
        assertFalse(throttle.isLocked("a@example.test", IP));
        throttle.recordFailure("a@example.test", IP);
        assertTrue(throttle.isLocked("a@example.test", IP));
        clock.advance(Duration.ofMinutes(5).minusSeconds(1));
        assertTrue(throttle.isLocked("a@example.test", IP));
        clock.advance(Duration.ofSeconds(1));
        assertFalse(throttle.isLocked("a@example.test", IP));
        throttle.recordFailure("a@example.test", IP);
        assertFalse(throttle.isLocked("a@example.test", IP), "the count starts again after a lockout");
    }

    @Test
    void failuresOutsideTheWindowAreForgotten() {
        fail("a@example.test", IP, 4);
        clock.advance(Duration.ofMinutes(5));
        throttle.recordFailure("a@example.test", IP);
        assertFalse(throttle.isLocked("a@example.test", IP));
    }

    @Test
    void pairsAreIndependentAndEmailIsNormalized() {
        fail(" A@Example.TEST ", IP, 5);
        assertTrue(throttle.isLocked("a@example.test", IP));
        assertFalse(throttle.isLocked("a@example.test", "198.51.100.7"));
        assertFalse(throttle.isLocked("b@example.test", IP));
    }

    @Test
    void successClearsFailures() {
        fail("a@example.test", IP, 4);
        throttle.recordSuccess("a@example.test", IP);
        fail("a@example.test", IP, 4);
        assertFalse(throttle.isLocked("a@example.test", IP));
    }

    @Test
    void staleEntriesArePurgedSoMemoryStaysBounded() {
        for (int i = 0; i <= LoginThrottle.PURGE_THRESHOLD; i++) {
            throttle.recordFailure("user" + i + "@example.test", IP);
        }
        clock.advance(Duration.ofMinutes(6));
        throttle.recordFailure("last@example.test", IP);
        assertEquals(1, throttle.trackedPairs());
    }
}

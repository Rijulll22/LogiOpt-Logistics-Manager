package com.logiopt.service;

import com.logiopt.util.Strings;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory sign-in throttling per (email, client address): after {@code maxFailures} failed attempts
 * within {@code window}, that pair is locked for {@code lockout}. A successful sign-in clears the
 * pair. State is lost on restart and is per server instance. Unknown and known emails are treated
 * the same, so a lockout reveals nothing about which accounts exist.
 */
public class LoginThrottle {

    /** Default failures before lockout. */
    public static final int DEFAULT_MAX_FAILURES = 5;
    /** Default window in which failures are counted. */
    public static final Duration DEFAULT_WINDOW = Duration.ofMinutes(5);
    /** Default lockout length. */
    public static final Duration DEFAULT_LOCKOUT = Duration.ofMinutes(5);

    /** Pairs kept before stale entries are purged, so the map cannot grow without bound. */
    static final int PURGE_THRESHOLD = 10_000;

    private final Clock clock;
    private final int maxFailures;
    private final Duration window;
    private final Duration lockout;
    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

    /** Failures and lockout of one (email, address) pair; guarded by its own monitor. */
    private static final class Attempts {
        private final Deque<Instant> failures = new ArrayDeque<>();
        private Instant lockedUntil;
    }

    /**
     * Creates a throttle.
     *
     * @param clock       time source
     * @param maxFailures failures that trigger a lockout
     * @param window      period in which failures are counted
     * @param lockout     lockout length
     */
    public LoginThrottle(Clock clock, int maxFailures, Duration window, Duration lockout) {
        if (clock == null || maxFailures < 1 || window == null || lockout == null
                || window.isNegative() || window.isZero() || lockout.isNegative() || lockout.isZero()) {
            throw new IllegalArgumentException("Clock, a positive failure limit, window and lockout are required");
        }
        this.clock = clock;
        this.maxFailures = maxFailures;
        this.window = window;
        this.lockout = lockout;
    }

    /**
     * Tells whether sign-in for this pair is currently locked.
     *
     * @param email         submitted email (normalized here)
     * @param clientAddress client IP address
     * @return true while locked
     */
    public boolean isLocked(String email, String clientAddress) {
        Attempts a = attempts.get(key(email, clientAddress));
        if (a == null) {
            return false;
        }
        synchronized (a) {
            return a.lockedUntil != null && clock.instant().isBefore(a.lockedUntil);
        }
    }

    /**
     * Records a failed attempt; the {@code maxFailures}-th failure within the window starts a lockout.
     *
     * @param email         submitted email
     * @param clientAddress client IP address
     */
    public void recordFailure(String email, String clientAddress) {
        if (attempts.size() > PURGE_THRESHOLD) {
            purgeStale();
        }
        Instant now = clock.instant();
        Attempts a = attempts.computeIfAbsent(key(email, clientAddress), k -> new Attempts());
        synchronized (a) {
            if (a.lockedUntil != null && !now.isBefore(a.lockedUntil)) {
                a.lockedUntil = null;
            }
            prune(a, now);
            a.failures.addLast(now);
            if (a.failures.size() >= maxFailures) {
                a.lockedUntil = now.plus(lockout);
                a.failures.clear();
            }
        }
    }

    /**
     * Clears the failures of a pair after a successful sign-in.
     *
     * @param email         submitted email
     * @param clientAddress client IP address
     */
    public void recordSuccess(String email, String clientAddress) {
        attempts.remove(key(email, clientAddress));
    }

    /** @return number of tracked pairs (for tests) */
    int trackedPairs() {
        return attempts.size();
    }

    private void prune(Attempts a, Instant now) {
        Instant cutoff = now.minus(window);
        while (!a.failures.isEmpty() && !a.failures.peekFirst().isAfter(cutoff)) {
            a.failures.removeFirst();
        }
    }

    private void purgeStale() {
        Instant now = clock.instant();
        attempts.entrySet().removeIf(e -> {
            Attempts a = e.getValue();
            synchronized (a) {
                prune(a, now);
                boolean locked = a.lockedUntil != null && now.isBefore(a.lockedUntil);
                return !locked && a.failures.isEmpty();
            }
        });
    }

    private static String key(String email, String clientAddress) {
        return Strings.normalize(email) + "|" + (clientAddress == null ? "" : clientAddress);
    }
}

package com.logiopt.web;

import com.logiopt.app.AppServices;
import com.logiopt.app.SchemaGate;
import com.logiopt.exception.DataAccessException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaGateTest {

    private static final class StepClock extends Clock {
        Instant now = Instant.parse("2026-10-25T10:00:00Z");

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

    private final StepClock clock = new StepClock();
    private final AtomicInteger applied = new AtomicInteger(3);
    private final AtomicInteger probes = new AtomicInteger();
    private final AtomicInteger readyActions = new AtomicInteger();

    private SchemaGate gate() {
        return new SchemaGate(() -> {
            probes.incrementAndGet();
            return applied.get();
        }, 4, "logiopt", clock, Duration.ofSeconds(10), readyActions::incrementAndGet);
    }

    /** Collects what SchemaGate logs. */
    private static List<String> captureLog(Runnable action) {
        Logger logger = Logger.getLogger(SchemaGate.class.getName());
        List<String> lines = new ArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                lines.add(record.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        logger.addHandler(handler);
        try {
            action.run();
        } finally {
            logger.removeHandler(handler);
        }
        return lines;
    }

    private static void assertRevealsNothing(String page) {
        assertEquals(SchemaGate.PAGE_MESSAGE, page);
        for (String secret : List.of("logiopt'", "confirm-database", "MigrationTool", "version 3", "version 4", "java -cp")) {
            assertFalse(page.contains(secret), "503 page must not contain " + secret);
        }
    }

    @Test
    void behindDatabaseShowsAGenericPageAndLogsTheExactCommand() {
        SchemaGate gate = gate();
        List<String> log = captureLog(() -> assertFalse(gate.isReady()));
        assertRevealsNothing(gate.getMessage());
        String logged = String.join("\n", log);
        assertTrue(logged.contains("is at version 3, but this build needs version 4"), logged);
        assertTrue(logged.contains("java -cp \"logiopt-web\\target\\logiopt\\WEB-INF\\classes;logiopt-web\\target\\logiopt\\WEB-INF\\lib\\*\" "
                + "com.logiopt.dao.MigrationTool --target=main --confirm-database=logiopt"), logged);
        assertTrue(logged.contains("does not migrate by itself"), logged);
        assertEquals(0, readyActions.get());
    }

    @Test
    void rechecksAtMostOncePerIntervalAndOpensAfterMigration() {
        SchemaGate gate = gate();
        assertFalse(gate.isReady());
        assertFalse(gate.isReady());
        assertEquals(1, probes.get());
        applied.set(4);
        clock.now = clock.now.plusSeconds(9);
        assertFalse(gate.isReady());
        clock.now = clock.now.plusSeconds(1);
        assertTrue(gate.isReady());
        assertTrue(gate.isReady());
        assertEquals(2, probes.get());
        assertEquals(1, readyActions.get());
        assertNull(gate.getMessage());
    }

    @Test
    void unreadableHistoryIsNotServedWithAGenericMessage() {
        SchemaGate gate = new SchemaGate(() -> {
            throw new DataAccessException("Migration V4 (V4__users_auth.sql) is recorded as failed: secret detail");
        }, 4, "logiopt", clock, Duration.ofSeconds(10), readyActions::incrementAndGet);
        List<String> log = captureLog(() -> assertFalse(gate.isReady()));
        assertRevealsNothing(gate.getMessage());
        assertFalse(gate.getMessage().contains("secret detail"));
        assertTrue(String.join("\n", log).contains("--confirm-database=logiopt --status"), log.toString());
    }

    @Test
    void newerDatabaseIsServed() {
        applied.set(5);
        assertTrue(gate().isReady());
    }

    @Test
    void filterAnswers503WhileBehind() throws Exception {
        AppServices services = new AppServices(null, null, null, null, null, null, null, gate(), null, null, null, null,
                null, null, null);
        SchemaGateFilter filter = WebFakes.init(new SchemaGateFilter(), WebFakes.context(services));
        WebFakes.Request r = new WebFakes.Request("GET", "/login");
        assertFalse(WebFakes.run(r, List.of(filter), null));
        assertEquals(503, r.response.status);
        assertEquals("/WEB-INF/jsp/error.jsp", r.response.forwardedTo);
        assertRevealsNothing((String) r.attributes.get("errorMessage"));
    }
}

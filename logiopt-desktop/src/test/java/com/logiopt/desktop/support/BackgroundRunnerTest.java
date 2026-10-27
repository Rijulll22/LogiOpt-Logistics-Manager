package com.logiopt.desktop.support;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The background runner: work runs on named daemon threads, never on the UI thread; callbacks run on
 * the UI executor; failures reach the failure callback; shutdown ends the threads. A single-thread
 * executor named "fake-ui" stands in for the JavaFX thread, so no display is needed.
 */
class BackgroundRunnerTest {

    private final ExecutorService ui = Executors.newSingleThreadExecutor(r -> new Thread(r, "fake-ui"));
    private ExecutorBackgroundRunner runner;

    @AfterEach
    void stop() {
        if (runner != null) {
            runner.shutdown();
        }
        ui.shutdownNow();
    }

    @Test
    void workRunsOnNamedDaemonThreadsAndCallbacksOnTheUiExecutor() throws Exception {
        runner = new ExecutorBackgroundRunner("logiopt-desktop-io", 2, ui);
        AtomicReference<String> workThread = new AtomicReference<>();
        AtomicReference<Boolean> daemon = new AtomicReference<>();
        AtomicReference<String> callbackThread = new AtomicReference<>();
        AtomicReference<Integer> result = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        runner.run(() -> {
            workThread.set(Thread.currentThread().getName());
            daemon.set(Thread.currentThread().isDaemon());
            return 42;
        }, value -> {
            callbackThread.set(Thread.currentThread().getName());
            result.set(value);
            done.countDown();
        }, error -> done.countDown());
        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertTrue(workThread.get().startsWith("logiopt-desktop-io-"), workThread.get());
        assertTrue(daemon.get());
        assertEquals("fake-ui", callbackThread.get());
        assertEquals(42, result.get());
    }

    @Test
    void failuresReachTheFailureCallbackOnTheUiExecutor() throws Exception {
        runner = new ExecutorBackgroundRunner("io", 1, ui);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<String> thread = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        runner.run(() -> {
            throw new IllegalStateException("boom");
        }, value -> done.countDown(), error -> {
            failure.set(error);
            thread.set(Thread.currentThread().getName());
            done.countDown();
        });
        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertInstanceOf(IllegalStateException.class, failure.get());
        assertEquals("fake-ui", thread.get());
    }

    @Test
    void aFailingCallbackDoesNotStopLaterWork() throws Exception {
        runner = new ExecutorBackgroundRunner("io", 1, ui);
        CountDownLatch second = new CountDownLatch(1);
        runner.run(() -> 1, value -> {
            throw new IllegalStateException("callback bug");
        }, error -> { });
        runner.run(() -> 2, value -> second.countDown(), error -> { });
        assertTrue(second.await(5, TimeUnit.SECONDS));
    }

    @Test
    void shutdownStopsTheThreadsAndDropsNewWork() throws Exception {
        List<String> calls = new CopyOnWriteArrayList<>();
        Executor direct = Runnable::run;
        AtomicReference<Thread> worker = new AtomicReference<>();
        runner = new ExecutorBackgroundRunner("io", 2, direct);
        runner.run(() -> {
            worker.set(Thread.currentThread());
            return "a";
        }, calls::add, e -> calls.add("failed"));
        assertTrue(runner.awaitIdle(5000));
        runner.shutdown();
        runner.shutdown();
        assertTrue(runner.isShutdown());
        worker.get().join(5000);
        assertFalse(worker.get().isAlive(), "the worker thread ends on shutdown");
        runner.run(() -> "late", calls::add, e -> calls.add("failed"));
        assertEquals(List.of("a"), calls, "work submitted after shutdown is dropped");
    }

    @Test
    void immediateRunnerRunsWorkAndCallbackAtOnce() {
        List<String> calls = new CopyOnWriteArrayList<>();
        ImmediateRunner immediate = new ImmediateRunner();
        immediate.run(() -> "ok", calls::add, e -> calls.add("failed"));
        immediate.run(() -> {
            throw new IllegalArgumentException("x");
        }, v -> calls.add("ok?"), e -> calls.add(e.getClass().getSimpleName()));
        immediate.shutdown();
        assertEquals(List.of("ok", "IllegalArgumentException"), calls);
    }
}

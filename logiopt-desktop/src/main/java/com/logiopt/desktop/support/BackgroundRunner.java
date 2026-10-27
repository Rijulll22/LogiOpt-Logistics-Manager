package com.logiopt.desktop.support;

import java.util.function.Consumer;

/**
 * Runs work that may touch the database away from the UI thread and delivers the outcome back on
 * the UI thread. Presenters call services only inside {@link Work#call()}.
 */
public interface BackgroundRunner {

    /**
     * Work done in the background.
     *
     * @param <T> result type
     */
    @FunctionalInterface
    interface Work<T> {
        /**
         * @return result
         * @throws Exception any failure, passed to the failure callback
         */
        T call() throws Exception;
    }

    /**
     * Runs {@code work} in the background, then exactly one of the callbacks on the UI thread.
     *
     * @param work      background work
     * @param onSuccess receives the result
     * @param onFailure receives the failure
     * @param <T>       result type
     */
    <T> void run(Work<T> work, Consumer<? super T> onSuccess, Consumer<? super Throwable> onFailure);

    /** Stops accepting work and ends the background threads. Safe to call twice. */
    void shutdown();
}

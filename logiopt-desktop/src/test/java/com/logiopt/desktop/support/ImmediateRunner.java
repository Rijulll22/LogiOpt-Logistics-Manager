package com.logiopt.desktop.support;

import java.util.function.Consumer;

/**
 * {@link BackgroundRunner} that runs the work and its callback at once on the calling thread. Used by
 * tests (no display needed); test code only, never used by the running client.
 */
public final class ImmediateRunner implements BackgroundRunner {

    @Override
    public <T> void run(Work<T> work, Consumer<? super T> onSuccess, Consumer<? super Throwable> onFailure) {
        T result;
        try {
            result = work.call();
        } catch (Exception | Error e) {
            onFailure.accept(e);
            return;
        }
        onSuccess.accept(result);
    }

    @Override
    public void shutdown() {
        // nothing to stop
    }
}

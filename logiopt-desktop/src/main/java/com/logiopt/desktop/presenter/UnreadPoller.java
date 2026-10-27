package com.logiopt.desktop.presenter;

import com.logiopt.desktop.support.Poller;
import com.logiopt.model.User;
import com.logiopt.service.NotificationService;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Keeps the unread badge current while a customer is signed in: counts the user's unread
 * notifications on one named daemon thread every {@link #PERIOD} (runs never overlap) and hands the
 * badge text to the UI thread. A failed count is logged and the badge keeps its last text; nothing is
 * shown as a dialog. Stopped on sign-out and on exit. Not started for the administrator, who receives
 * no notifications.
 */
public final class UnreadPoller {

    /** Name of the polling thread. */
    public static final String THREAD_NAME = "logiopt-unread-poller";
    /** Time between two counts. */
    public static final Duration PERIOD = Duration.ofSeconds(15);

    private static final Logger LOG = Logger.getLogger(UnreadPoller.class.getName());

    private final Poller poller;

    /**
     * @param notifications notification service
     * @param user          signed-in customer
     * @param ui            runs the badge update on the UI thread
     * @param badge         receives the badge text
     * @param period        time between two counts
     */
    public UnreadPoller(NotificationService notifications, User user, Executor ui, Consumer<String> badge,
                        Duration period) {
        this.poller = new Poller(THREAD_NAME, period, () -> {
            try {
                String text = ShellPresenter.badge(notifications.unreadCount(user));
                ui.execute(() -> badge.accept(text));
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Counting unread notifications failed; the badge keeps its last value", e);
            }
        });
    }

    /** Starts polling. */
    public void start() {
        poller.start();
    }

    /** Stops polling and ends the thread. Safe to call twice. */
    public void stop() {
        poller.stop();
    }

    /** @return true while polling */
    public boolean isRunning() {
        return poller.isRunning();
    }

    /**
     * @param timeout how long to wait
     * @return true if the polling thread has ended
     * @throws InterruptedException if interrupted
     */
    public boolean awaitStopped(Duration timeout) throws InterruptedException {
        return poller.awaitStopped(timeout);
    }
}

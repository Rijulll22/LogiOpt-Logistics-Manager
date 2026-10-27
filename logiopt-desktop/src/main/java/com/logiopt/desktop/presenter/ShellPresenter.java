package com.logiopt.desktop.presenter;

import com.logiopt.desktop.support.BackgroundRunner;
import com.logiopt.model.User;
import com.logiopt.service.NotificationService;

import java.time.Duration;

/**
 * Top bar of the main window: the signed-in user and the unread-notification badge. The badge is
 * refreshed whenever a page opens and every 15 seconds by the {@link UnreadPoller}. The
 * administrator receives no notifications, so the badge is hidden for that account.
 */
public final class ShellPresenter {

    /** What the top bar can show. */
    public interface View {
        /**
         * @param name  display name
         * @param email email address
         */
        void showUser(String name, String email);

        /** @param text badge text, or null to hide the badge */
        void showUnread(String text);
    }

    /** How long the badge waits for notifications still being written. */
    static final Duration NOTIFICATION_WAIT = Duration.ofSeconds(2);

    private final NotificationService notifications;
    private final BackgroundRunner runner;
    private final User user;
    private final View view;

    /**
     * @param notifications notification service
     * @param runner        background runner
     * @param user          signed-in user
     * @param view          top bar
     */
    public ShellPresenter(NotificationService notifications, BackgroundRunner runner, User user, View view) {
        this.notifications = notifications;
        this.runner = runner;
        this.user = user;
        this.view = view;
    }

    /** Shows the user and refreshes the badge in the background. */
    public void refresh() {
        view.showUser(user.getName(), user.getEmail());
        if (user.isAdmin()) {
            view.showUnread(null);
            return;
        }
        runner.run(() -> notifications.unreadCount(user), count -> view.showUnread(badge(count)),
                error -> view.showUnread(null));
    }

    /**
     * Refreshes the badge after a booking, payment or cancellation: waits briefly for the in-app
     * notifications those create (they are written in the background), then counts.
     */
    public void refreshAfterChange() {
        if (user.isAdmin()) {
            return;
        }
        runner.run(() -> {
            notifications.awaitIdle(NOTIFICATION_WAIT);
            return notifications.unreadCount(user);
        }, count -> view.showUnread(badge(count)), error -> view.showUnread(null));
    }

    /** @return "No unread", "1 unread", "12 unread" */
    public static String badge(int count) {
        return count <= 0 ? "No unread" : count + " unread";
    }
}

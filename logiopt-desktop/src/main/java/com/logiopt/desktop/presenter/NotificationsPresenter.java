package com.logiopt.desktop.presenter;

import com.logiopt.desktop.presenter.Models.NotificationsModel;
import com.logiopt.desktop.support.BackgroundRunner;
import com.logiopt.desktop.support.ErrorMessages;
import com.logiopt.model.User;
import com.logiopt.service.NotificationService;

import java.util.function.LongConsumer;

/**
 * The signed-in user's notifications, newest first, with mark one or all as read and Open to the
 * related booking. {@link NotificationService} only ever lists, counts and marks the user's own
 * notifications; another user's id looks missing.
 */
public final class NotificationsPresenter {

    /** Shown after Mark all as read. */
    public static final String ALL_READ = "All notifications are marked as read.";

    /** What the notifications page can show. */
    public interface View {
        /** Shows a loading state. */
        void showLoading();

        /** @param model notifications and the unread summary */
        void showNotifications(NotificationsModel model);

        /** @param message error state text */
        void showError(String message);

        /** @param message result of an action, or why it failed */
        void showMessage(String message);

        /** @param busy true while a change runs */
        void setBusy(boolean busy);
    }

    private final NotificationService notifications;
    private final BackgroundRunner runner;
    private final User user;
    private final Runnable onChanged;
    private final LongConsumer onOpen;
    private final View view;
    private boolean busy;

    /**
     * @param notifications notification service
     * @param runner        background runner
     * @param user          signed-in user
     * @param onChanged     runs after a change (refreshes the unread badge)
     * @param onOpen        opens a booking
     * @param view          page
     */
    public NotificationsPresenter(NotificationService notifications, BackgroundRunner runner, User user,
                                  Runnable onChanged, LongConsumer onOpen, View view) {
        this.notifications = notifications;
        this.runner = runner;
        this.user = user;
        this.onChanged = onChanged;
        this.onOpen = onOpen;
        this.view = view;
    }

    /** Loads the notifications in the background. */
    public void load() {
        view.showLoading();
        runner.run(() -> ViewModels.notifications(notifications.list(user)), view::showNotifications,
                error -> view.showError(ErrorMessages.of(error)));
    }

    /** @param notificationId one of the user's notifications */
    public void markRead(long notificationId) {
        change(() -> notifications.markRead(user, notificationId), null);
    }

    /** Marks every notification of the user as read. */
    public void markAllRead() {
        change(() -> notifications.markAllRead(user), ALL_READ);
    }

    /**
     * Marks the notification as read and opens its booking. The booking page checks ownership again.
     *
     * @param notificationId notification
     * @param bookingId      its booking
     */
    public void open(long notificationId, long bookingId) {
        if (busy) {
            return;
        }
        busy = true;
        view.setBusy(true);
        runner.run(() -> {
            notifications.markRead(user, notificationId);
            return Boolean.TRUE;
        }, ok -> {
            busy = false;
            view.setBusy(false);
            onChanged.run();
            onOpen.accept(bookingId);
        }, error -> {
            busy = false;
            view.setBusy(false);
            view.showMessage(ErrorMessages.of(error));
        });
    }

    /** A change done in the background. */
    @FunctionalInterface
    private interface Change {
        void run() throws Exception;
    }

    private void change(Change change, String done) {
        if (busy) {
            return;
        }
        busy = true;
        view.setBusy(true);
        runner.run(() -> {
            change.run();
            return ViewModels.notifications(notifications.list(user));
        }, model -> {
            busy = false;
            view.setBusy(false);
            view.showNotifications(model);
            view.showMessage(done);
            onChanged.run();
        }, error -> {
            busy = false;
            view.setBusy(false);
            view.showMessage(ErrorMessages.of(error));
        });
    }
}

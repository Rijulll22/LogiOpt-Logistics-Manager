package com.logiopt.desktop.presenter;

import com.logiopt.desktop.presenter.Models.HistoryModel;
import com.logiopt.desktop.support.BackgroundRunner;
import com.logiopt.desktop.support.ErrorMessages;
import com.logiopt.model.User;
import com.logiopt.service.HistoryService;

/** The signed-in user's newest shipments, bookings and reports (owner-only, through HistoryService). */
public final class HistoryPresenter {

    /** What the history page can show. */
    public interface View {
        /** Shows a loading state. */
        void showLoading();

        /** @param model the three tables */
        void showHistory(HistoryModel model);

        /** @param message error state text */
        void showError(String message);
    }

    private final HistoryService history;
    private final BackgroundRunner runner;
    private final User user;
    private final View view;

    /**
     * @param history history service
     * @param runner  background runner
     * @param user    signed-in user
     * @param view    page
     */
    public HistoryPresenter(HistoryService history, BackgroundRunner runner, User user, View view) {
        this.history = history;
        this.runner = runner;
        this.user = user;
        this.view = view;
    }

    /** Loads the history in the background. */
    public void load() {
        view.showLoading();
        runner.run(() -> ViewModels.history(history.of(user)), view::showHistory,
                error -> view.showError(ErrorMessages.of(error)));
    }
}

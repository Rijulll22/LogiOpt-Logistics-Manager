package com.logiopt.desktop.presenter;

import com.logiopt.desktop.presenter.Models.UserRow;
import com.logiopt.desktop.support.BackgroundRunner;
import com.logiopt.desktop.support.ErrorMessages;
import com.logiopt.model.User;
import com.logiopt.service.AuthService;

import java.util.ArrayList;
import java.util.List;

/**
 * Every user account for the administrator: name, email, role and whether it is enabled. No password
 * data is loaded ({@link User} carries none). Anyone else is refused here and by {@link AuthService}.
 */
public final class AdminUsersPresenter {

    /** What the users page can show. */
    public interface View {
        /** Shows a loading state. */
        void showLoading();

        /** @param rows every user */
        void showUsers(List<UserRow> rows);

        /** @param message error state text */
        void showError(String message);
    }

    private final AuthService auth;
    private final BackgroundRunner runner;
    private final User admin;
    private final View view;

    /**
     * @param auth   authentication service
     * @param runner background runner
     * @param admin  signed-in user (must be the administrator)
     * @param view   page
     */
    public AdminUsersPresenter(AuthService auth, BackgroundRunner runner, User admin, View view) {
        this.auth = auth;
        this.runner = runner;
        this.admin = admin;
        this.view = view;
    }

    /** Loads the users in the background. */
    public void load() {
        if (!admin.isAdmin()) {
            view.showError(ErrorMessages.NOT_FOUND);
            return;
        }
        view.showLoading();
        runner.run(() -> {
            List<UserRow> rows = new ArrayList<>();
            for (User u : auth.listUsers(admin)) {
                rows.add(ViewModels.userRow(u));
            }
            return rows;
        }, view::showUsers, error -> view.showError(ErrorMessages.of(error)));
    }
}

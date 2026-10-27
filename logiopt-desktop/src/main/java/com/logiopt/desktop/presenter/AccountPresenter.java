package com.logiopt.desktop.presenter;

import com.logiopt.desktop.presenter.Models.AccountModel;
import com.logiopt.desktop.support.BackgroundRunner;
import com.logiopt.desktop.support.ErrorMessages;
import com.logiopt.exception.AccountChangeException;
import com.logiopt.model.User;
import com.logiopt.service.AuthService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The signed-in user's own account: profile, name change and password change, all through
 * AuthService, which only ever changes the account of the user passed in (taken from the session).
 * After a change the user stays signed in; a new name is handed to {@code onUserChanged} so the
 * session and the top bar show it.
 */
public final class AccountPresenter {

    /** Notice after a name change. */
    public static final String NAME_SAVED = "Your name was updated.";
    /** Notice after a password change. */
    public static final String PASSWORD_CHANGED = "Your password was changed. You stay signed in; use the new password "
            + "next time.";
    /** Field key: current password. */
    public static final String CURRENT = "current";
    /** Field key: new password. */
    public static final String NEW = "new";
    /** Field key: confirmation. */
    public static final String CONFIRM = "confirm";
    /** Key for messages that belong to no field. */
    public static final String GENERAL = "";

    /** What the account page can show. */
    public interface View {
        /** Shows a loading state. */
        void showLoading();

        /** @param model profile and what can be changed */
        void showAccount(AccountModel model);

        /** @param message error state text */
        void showError(String message);

        /** @param errors name errors (empty clears them) */
        void showNameErrors(List<String> errors);

        /** @param notice success notice of the name form */
        void showNameSaved(String notice);

        /** @param errors password errors by field key (empty clears them) */
        void showPasswordErrors(Map<String, List<String>> errors);

        /** @param notice success notice of the password form (also clears the password fields) */
        void showPasswordChanged(String notice);

        /** @param busy true while a change is being saved */
        void setBusy(boolean busy);
    }

    /** Result of a name change: the renamed user and the refreshed profile. */
    private record Renamed(User user, AccountModel model) {
    }

    private final AuthService auth;
    private final BackgroundRunner runner;
    private final Consumer<User> onUserChanged;
    private final View view;
    private User user;

    /**
     * @param auth          account service
     * @param runner        background runner
     * @param user          signed-in user
     * @param onUserChanged receives the user after a name change (to update the session)
     * @param view          page
     */
    public AccountPresenter(AuthService auth, BackgroundRunner runner, User user, Consumer<User> onUserChanged, View view) {
        this.auth = auth;
        this.runner = runner;
        this.user = user;
        this.onUserChanged = onUserChanged;
        this.view = view;
    }

    /** Loads the profile in the background. */
    public void load() {
        view.showLoading();
        User actor = user;
        runner.run(() -> ViewModels.account(auth.profile(actor)), view::showAccount,
                error -> view.showError(ErrorMessages.of(error)));
    }

    /** @param name new display name */
    public void saveName(String name) {
        User actor = user;
        view.showNameErrors(List.of());
        view.setBusy(true);
        runner.run(() -> {
            User renamed = auth.changeName(actor, name);
            return new Renamed(renamed, ViewModels.account(auth.profile(renamed)));
        }, result -> {
            view.setBusy(false);
            user = result.user();
            onUserChanged.accept(user);
            view.showAccount(result.model());
            view.showNameSaved(NAME_SAVED);
        }, error -> {
            view.setBusy(false);
            view.showNameErrors(error instanceof AccountChangeException ? ((AccountChangeException) error).getErrors()
                    : List.of(ErrorMessages.of(error)));
        });
    }

    /**
     * @param current     current password (cleared)
     * @param newPassword new password (cleared)
     * @param confirm     new password again (cleared)
     */
    public void changePassword(char[] current, char[] newPassword, char[] confirm) {
        User actor = user;
        view.showPasswordErrors(Map.of());
        view.setBusy(true);
        runner.run(() -> {
            auth.changePassword(actor, current, newPassword, confirm, LoginPresenter.CLIENT_ADDRESS);
            return Boolean.TRUE;
        }, ok -> {
            view.setBusy(false);
            view.showPasswordChanged(PASSWORD_CHANGED);
        }, error -> {
            view.setBusy(false);
            view.showPasswordErrors(error instanceof AccountChangeException
                    ? byField(((AccountChangeException) error).getErrors())
                    : Map.of(GENERAL, List.of(ErrorMessages.of(error))));
        });
    }

    /** @return password messages grouped by the field they are about */
    static Map<String, List<String>> byField(List<String> messages) {
        Map<String, List<String>> grouped = new LinkedHashMap<>();
        for (String m : messages) {
            String field;
            if (m.equals(AuthService.WRONG_CURRENT_PASSWORD) || m.equals(AuthService.PASSWORD_LOCKED)) {
                field = CURRENT;
            } else if (m.equals(AuthService.PASSWORDS_DIFFER)) {
                field = CONFIRM;
            } else if (m.startsWith("Password must")) {
                field = NEW;
            } else {
                field = GENERAL;
            }
            grouped.computeIfAbsent(field, k -> new ArrayList<>()).add(m);
        }
        return grouped;
    }
}

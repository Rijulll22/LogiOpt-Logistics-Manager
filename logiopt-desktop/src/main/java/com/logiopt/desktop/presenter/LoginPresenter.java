package com.logiopt.desktop.presenter;

import com.logiopt.desktop.support.BackgroundRunner;
import com.logiopt.desktop.support.ErrorMessages;
import com.logiopt.model.User;
import com.logiopt.service.AuthService;

import java.util.Arrays;
import java.util.function.Consumer;

/**
 * Sign-in through {@link AuthService#login}, so the same throttling and generic messages apply as on
 * the web. The password array is cleared after use.
 */
public final class LoginPresenter {

    /** Address used for sign-in throttling: the desktop client signs in from this machine only. */
    public static final String CLIENT_ADDRESS = "desktop-client";
    /** Same text as the web sign-in page. */
    public static final String INVALID_MESSAGE = "Email or password is incorrect.";
    /** Same text as the web sign-in page. */
    public static final String LOCKED_MESSAGE = "Too many failed sign-in attempts. Please wait a few minutes and try again.";
    /** Shown before anything is sent when a field is empty. */
    public static final String MISSING_MESSAGE = "Enter your email and password.";

    /** What the sign-in card can show. */
    public interface View {
        /** @param busy true while signing in (disables the form) */
        void setBusy(boolean busy);

        /** @param message error to show, or null to clear it */
        void showError(String message);
    }

    private final AuthService auth;
    private final BackgroundRunner runner;
    private final Consumer<User> onSignedIn;
    private final View view;

    /**
     * @param auth       sign-in service
     * @param runner     background runner
     * @param onSignedIn receives the user after a successful sign-in
     * @param view       sign-in card
     */
    public LoginPresenter(AuthService auth, BackgroundRunner runner, Consumer<User> onSignedIn, View view) {
        this.auth = auth;
        this.runner = runner;
        this.onSignedIn = onSignedIn;
        this.view = view;
    }

    /**
     * @param email    entered email
     * @param password entered password (cleared by this call)
     */
    public void signIn(String email, char[] password) {
        if (email == null || email.isBlank() || password == null || password.length == 0) {
            clear(password);
            view.showError(MISSING_MESSAGE);
            return;
        }
        view.showError(null);
        view.setBusy(true);
        runner.run(() -> auth.login(email, password, CLIENT_ADDRESS), result -> {
            view.setBusy(false);
            switch (result.getStatus()) {
                case SUCCESS:
                    onSignedIn.accept(result.getUser());
                    break;
                case LOCKED:
                    view.showError(LOCKED_MESSAGE);
                    break;
                default:
                    view.showError(INVALID_MESSAGE);
            }
        }, error -> {
            clear(password);
            view.setBusy(false);
            view.showError(ErrorMessages.of(error));
        });
    }

    private static void clear(char[] password) {
        if (password != null) {
            Arrays.fill(password, '\0');
        }
    }
}

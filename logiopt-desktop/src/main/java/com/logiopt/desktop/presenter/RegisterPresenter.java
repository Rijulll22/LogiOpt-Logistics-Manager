package com.logiopt.desktop.presenter;

import com.logiopt.desktop.support.BackgroundRunner;
import com.logiopt.desktop.support.ErrorMessages;
import com.logiopt.exception.InvalidRegistrationException;
import com.logiopt.service.AuthService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Registration through {@link AuthService#register}. A duplicate email looks exactly like a new
 * registration (the service ignores it), so the client never reveals which addresses exist.
 */
public final class RegisterPresenter {

    /** Same text as the web sign-in page after registering. */
    public static final String REGISTERED_NOTICE = "Registration received. If this email address was not already "
            + "registered, you can now sign in with it.";
    /** Field keys for inline errors. */
    public static final String EMAIL = "email";
    /** Field key. */
    public static final String NAME = "name";
    /** Field key. */
    public static final String PASSWORD = "password";
    /** Key for errors that belong to no field. */
    public static final String GENERAL = "";

    /** What the registration card can show. */
    public interface View {
        /** @param busy true while registering */
        void setBusy(boolean busy);

        /** @param errors messages by field key (EMAIL, NAME, PASSWORD, GENERAL); empty clears them */
        void showErrors(Map<String, List<String>> errors);
    }

    private final AuthService auth;
    private final BackgroundRunner runner;
    private final Consumer<String> onRegistered;
    private final View view;

    /**
     * @param auth         registration service
     * @param runner       background runner
     * @param onRegistered receives the notice to show on the sign-in card
     * @param view         registration card
     */
    public RegisterPresenter(AuthService auth, BackgroundRunner runner, Consumer<String> onRegistered, View view) {
        this.auth = auth;
        this.runner = runner;
        this.onRegistered = onRegistered;
        this.view = view;
    }

    /**
     * @param email    entered email
     * @param name     entered name
     * @param password entered password (cleared by the service)
     */
    public void register(String email, String name, char[] password) {
        view.showErrors(Map.of());
        view.setBusy(true);
        runner.run(() -> {
            auth.register(email, name, password);
            return Boolean.TRUE;
        }, ok -> {
            view.setBusy(false);
            onRegistered.accept(REGISTERED_NOTICE);
        }, error -> {
            view.setBusy(false);
            if (error instanceof InvalidRegistrationException) {
                view.showErrors(byField(((InvalidRegistrationException) error).getErrors()));
            } else {
                view.showErrors(Map.of(GENERAL, List.of(ErrorMessages.of(error))));
            }
        });
    }

    /** @return messages grouped by the field they are about */
    static Map<String, List<String>> byField(List<String> messages) {
        Map<String, List<String>> grouped = new LinkedHashMap<>();
        for (String m : messages) {
            String field = m.startsWith("Email") || m.startsWith("Enter a valid email") ? EMAIL
                    : m.startsWith("Name") ? NAME : m.startsWith("Password") ? PASSWORD : GENERAL;
            grouped.computeIfAbsent(field, k -> new ArrayList<>()).add(m);
        }
        return grouped;
    }
}

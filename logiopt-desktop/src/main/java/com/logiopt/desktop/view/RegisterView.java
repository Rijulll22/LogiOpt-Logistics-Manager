package com.logiopt.desktop.view;

import com.logiopt.desktop.presenter.RegisterPresenter;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Map;

/** Registration card: email, name and a password of at least 8 characters, with inline errors. */
public final class RegisterView implements RegisterPresenter.View {

    /** Receives the entered values. */
    @FunctionalInterface
    public interface Submit {
        /**
         * @param email    email
         * @param name     name
         * @param password password (the receiver clears it)
         */
        void accept(String email, String name, char[] password);
    }

    private final TextField email = new TextField();
    private final TextField name = new TextField();
    private final PasswordField password = new PasswordField();
    private final Label emailError = Theme.fieldError();
    private final Label nameError = Theme.fieldError();
    private final Label passwordError = Theme.fieldError();
    private final Label general = Theme.banner(Theme.Banner.ERROR);
    private final Button create = Theme.primaryButton("Create account");
    private final Button back = Theme.linkButton("Sign in");
    private final ScrollPane root;

    /** Builds the card. */
    public RegisterView() {
        VBox card = WelcomePage.card("Create an account", "Register to save shipments, compare routes and book them.");
        email.setPromptText("you@example.com");
        name.setPromptText("Your name");
        password.setPromptText("At least 8 characters");
        email.setId("email");
        name.setId("name");
        password.setId("password");
        emailError.setId("emailError");
        nameError.setId("nameError");
        passwordError.setId("passwordError");
        Theme.styleInput(email);
        Theme.styleInput(name);
        Theme.styleInput(password);
        create.setId("register");
        create.setDefaultButton(true);
        create.setMaxWidth(Double.MAX_VALUE);
        Label prompt = Theme.muted("Already registered?");
        HBox footer = new HBox(4, prompt, back);
        footer.setAlignment(Pos.CENTER);
        card.getChildren().addAll(general, WelcomePage.field("Email", email, emailError),
                WelcomePage.field("Name", name, nameError), WelcomePage.field("Password", password, passwordError),
                create, Theme.divider(), footer);
        root = WelcomePage.page(card);
    }

    /** @return screen content */
    public ScrollPane root() {
        return root;
    }

    /** @param action receives the values when Create account is chosen */
    public void onRegister(Submit action) {
        create.setOnAction(e -> {
            char[] secret = password.getText().toCharArray();
            password.clear();
            action.accept(email.getText(), name.getText(), secret);
        });
    }

    /** @param action returns to the sign-in card */
    public void onBack(Runnable action) {
        back.setOnAction(e -> action.run());
    }

    /** Puts the cursor into the email field. */
    public void focus() {
        email.requestFocus();
    }

    @Override
    public void setBusy(boolean busy) {
        create.setDisable(busy);
        create.setText(busy ? "Creating account…" : "Create account");
    }

    @Override
    public void showErrors(Map<String, List<String>> errors) {
        Theme.setMessage(emailError, join(errors.get(RegisterPresenter.EMAIL)));
        Theme.setMessage(nameError, join(errors.get(RegisterPresenter.NAME)));
        Theme.setMessage(passwordError, join(errors.get(RegisterPresenter.PASSWORD)));
        Theme.setMessage(general, join(errors.get(RegisterPresenter.GENERAL)));
    }

    private static String join(List<String> messages) {
        return messages == null || messages.isEmpty() ? null : String.join(" ", messages);
    }
}

package com.logiopt.desktop.view;

import com.logiopt.desktop.presenter.LoginPresenter;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.function.BiConsumer;

/** Sign-in card on the gradient background. */
public final class LoginView implements LoginPresenter.View {

    private final TextField email = new TextField();
    private final PasswordField password = new PasswordField();
    private final Button signIn = Theme.primaryButton("Sign in");
    private final Button createAccount = Theme.linkButton("Create an account");
    private final Label notice = Theme.banner(Theme.Banner.NOTICE);
    private final Label error = Theme.banner(Theme.Banner.ERROR);
    private final ScrollPane root;

    /** Builds the card. */
    public LoginView() {
        VBox card = WelcomePage.card("Sign in", "Plan, compare and book shipments across road, rail and multimodal routes.");
        email.setPromptText("you@example.com");
        email.setId("email");
        password.setPromptText("Your password");
        password.setId("password");
        Theme.styleInput(email);
        Theme.styleInput(password);
        signIn.setId("signIn");
        signIn.setDefaultButton(true);
        signIn.setMaxWidth(Double.MAX_VALUE);
        createAccount.setId("createAccount");
        notice.setId("notice");
        error.setId("error");
        Label prompt = Theme.muted("No account yet?");
        HBox footer = new HBox(4, prompt, createAccount);
        footer.setAlignment(Pos.CENTER);
        card.getChildren().addAll(notice, error, WelcomePage.field("Email", email, null),
                WelcomePage.field("Password", password, null), signIn, Theme.divider(), footer);
        root = WelcomePage.page(card);
    }

    /** @return screen content */
    public ScrollPane root() {
        return root;
    }

    /** @param action receives the email and password when Sign in is chosen (Enter works too) */
    public void onSignIn(BiConsumer<String, char[]> action) {
        signIn.setOnAction(e -> {
            char[] secret = password.getText().toCharArray();
            password.clear();
            action.accept(email.getText(), secret);
        });
    }

    /** @param action opens the registration card */
    public void onCreateAccount(Runnable action) {
        createAccount.setOnAction(e -> action.run());
    }

    /** @param message notice above the form, or null */
    public void showNotice(String message) {
        Theme.setMessage(notice, message);
    }

    /** @param value email to prefill */
    public void setEmail(String value) {
        email.setText(value);
    }

    /** Puts the cursor into the email field. */
    public void focus() {
        email.requestFocus();
    }

    @Override
    public void setBusy(boolean busy) {
        signIn.setDisable(busy);
        email.setDisable(busy);
        password.setDisable(busy);
        signIn.setText(busy ? "Signing in…" : "Sign in");
    }

    @Override
    public void showError(String message) {
        Theme.setMessage(error, message);
        if (message != null) {
            Theme.setMessage(notice, null);
        }
    }
}

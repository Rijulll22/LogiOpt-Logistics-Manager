package com.logiopt.desktop.view;

import com.logiopt.desktop.presenter.AccountPresenter;
import com.logiopt.desktop.presenter.Models.AccountModel;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.FontWeight;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Account page: the profile card, then the change-name and change-password cards side by side
 * (stacked when the window is narrow). The administrator sees why the password cannot be changed.
 */
public final class AccountView implements AccountPresenter.View {

    /** Receives the three password fields. */
    @FunctionalInterface
    public interface PasswordChange {
        /**
         * @param current     current password
         * @param newPassword new password
         * @param confirm     new password again
         */
        void accept(char[] current, char[] newPassword, char[] confirm);
    }

    /** Narrowest a form card gets before the two cards stack. */
    static final double FORM_CARD_MIN_WIDTH = 320;

    private final VBox root = new VBox(Theme.space(3));
    private final Label profileName = value();
    private final Label profileEmail = value();
    private final HBox profileRole = new HBox();
    private final Label profileSince = value();
    private final TextField name = new TextField();
    private final Label nameError = Theme.fieldError();
    private final Label nameNotice = Theme.banner(Theme.Banner.SUCCESS);
    private final Button saveName = Theme.primaryButton("Save name");
    private final PasswordField current = new PasswordField();
    private final PasswordField fresh = new PasswordField();
    private final PasswordField confirm = new PasswordField();
    private final Label currentError = Theme.fieldError();
    private final Label freshError = Theme.fieldError();
    private final Label confirmError = Theme.fieldError();
    private final Label passwordGeneral = Theme.banner(Theme.Banner.ERROR);
    private final Label passwordNotice = Theme.banner(Theme.Banner.SUCCESS);
    private final Label passwordNote = Theme.muted("");
    private final Button changePassword = Theme.primaryButton("Change password");
    private final VBox passwordFields = new VBox(Theme.space(2));
    private final VBox content = new VBox(Theme.space(3));
    private Runnable retry = () -> { };

    /** Builds the page (empty until {@link #showAccount}). */
    public AccountView() {
        root.setId("account");
        name.setId("accountName");
        current.setId("currentPassword");
        fresh.setId("newPassword");
        confirm.setId("confirmPassword");
        nameError.setId("nameError");
        currentError.setId("currentError");
        freshError.setId("newError");
        confirmError.setId("confirmError");
        passwordGeneral.setId("passwordError");
        nameNotice.setId("nameNotice");
        passwordNotice.setId("passwordNotice");
        saveName.setId("saveName");
        changePassword.setId("changePassword");
        profileRole.setId("profileRole");
        profileName.setId("profileName");
        profileEmail.setId("profileEmail");
        profileSince.setId("profileSince");
        name.setPromptText("Your name");
        current.setPromptText("Current password");
        fresh.setPromptText("At least 8 characters");
        confirm.setPromptText("Type the new password again");
        for (TextField f : List.of(name, current, fresh, confirm)) {
            Theme.styleInput(f);
        }
        name.setOnAction(e -> saveName.fire());
        confirm.setOnAction(e -> changePassword.fire());
        content.getChildren().addAll(profileCard(), formCards());
        root.getChildren().setAll(content);
    }

    private static Label value() {
        Label label = Theme.text("", 15, FontWeight.NORMAL, Theme.TEXT);
        label.setWrapText(true);
        return label;
    }

    private VBox profileCard() {
        VBox card = Theme.card();
        card.setId("profileCard");
        GridPane grid = new GridPane();
        grid.setHgap(Theme.space(3));
        grid.setVgap(Theme.space(2));
        ColumnConstraints labels = new ColumnConstraints();
        labels.setMinWidth(120);
        ColumnConstraints values = new ColumnConstraints();
        values.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(labels, values);
        grid.addRow(0, Theme.fieldLabel("Name"), profileName);
        grid.addRow(1, Theme.fieldLabel("Email"), profileEmail);
        grid.addRow(2, Theme.fieldLabel("Role"), profileRole);
        grid.addRow(3, Theme.fieldLabel("Member since"), profileSince);
        card.getChildren().addAll(new VBox(2, Theme.cardTitle("Profile"),
                Theme.muted("Your account as LogiOpt knows it. The email address cannot be changed.")), grid);
        return card;
    }

    private CardGrid formCards() {
        VBox nameCard = Theme.card();
        nameCard.setId("nameCard");
        HBox nameButtons = new HBox(Theme.spacer(), saveName);
        nameButtons.setAlignment(Pos.CENTER_RIGHT);
        nameCard.getChildren().addAll(new VBox(2, Theme.cardTitle("Change name"), Theme.muted("Shown in the top bar.")),
                nameNotice, field("Name", name, nameError), nameButtons);

        VBox passwordCard = Theme.card();
        passwordCard.setId("passwordCard");
        HBox passwordButtons = new HBox(Theme.spacer(), changePassword);
        passwordButtons.setAlignment(Pos.CENTER_RIGHT);
        passwordFields.getChildren().addAll(field("Current password", current, currentError),
                field("New password", fresh, freshError), field("Confirm new password", confirm, confirmError),
                passwordButtons);
        passwordCard.getChildren().addAll(new VBox(2, Theme.cardTitle("Change password"), passwordNote),
                passwordNotice, passwordGeneral, passwordFields);

        CardGrid grid = new CardGrid(FORM_CARD_MIN_WIDTH, Theme.space(3));
        grid.setId("accountForms");
        grid.getChildren().addAll(nameCard, passwordCard);
        return grid;
    }

    private static VBox field(String label, Node input, Label error) {
        return new VBox(Theme.space(1) - 2, Theme.fieldLabel(label), input, error);
    }

    /** @return page content */
    public VBox root() {
        return root;
    }

    /** @param action receives the new name */
    public void onSaveName(Consumer<String> action) {
        saveName.setOnAction(e -> action.accept(name.getText()));
    }

    /** @param action receives the password fields, which are cleared at once */
    public void onChangePassword(PasswordChange action) {
        changePassword.setOnAction(e -> {
            char[] a = current.getText().toCharArray();
            char[] b = fresh.getText().toCharArray();
            char[] c = confirm.getText().toCharArray();
            current.clear();
            fresh.clear();
            confirm.clear();
            action.accept(a, b, c);
        });
    }

    /** @param action reloads after an error */
    public void onRetry(Runnable action) {
        retry = action;
    }

    @Override
    public void showLoading() {
        root.getChildren().setAll(PageStates.loading("Loading your account…"));
    }

    @Override
    public void showAccount(AccountModel model) {
        profileName.setText(model.name());
        profileEmail.setText(model.email());
        profileRole.getChildren().setAll(Theme.chip(model.role(), model.roleChip()));
        profileSince.setText(model.memberSince());
        name.setText(model.name());
        passwordNote.setText(model.passwordNote());
        passwordFields.setVisible(model.canChangePassword());
        passwordFields.setManaged(model.canChangePassword());
        root.getChildren().setAll(content);
    }

    @Override
    public void showError(String message) {
        root.getChildren().setAll(PageStates.error(message, "Try again", retry));
    }

    @Override
    public void showNameErrors(List<String> errors) {
        Theme.setMessage(nameError, errors.isEmpty() ? null : String.join(" ", errors));
        if (!errors.isEmpty()) {
            Theme.setMessage(nameNotice, null);
        }
    }

    @Override
    public void showNameSaved(String notice) {
        Theme.setMessage(nameError, null);
        Theme.setMessage(nameNotice, notice);
    }

    @Override
    public void showPasswordErrors(Map<String, List<String>> errors) {
        Theme.setMessage(currentError, join(errors.get(AccountPresenter.CURRENT)));
        Theme.setMessage(freshError, join(errors.get(AccountPresenter.NEW)));
        Theme.setMessage(confirmError, join(errors.get(AccountPresenter.CONFIRM)));
        Theme.setMessage(passwordGeneral, join(errors.get(AccountPresenter.GENERAL)));
        if (!errors.isEmpty()) {
            Theme.setMessage(passwordNotice, null);
        }
    }

    @Override
    public void showPasswordChanged(String notice) {
        showPasswordErrors(Map.of());
        Theme.setMessage(passwordNotice, notice);
    }

    @Override
    public void setBusy(boolean busy) {
        saveName.setDisable(busy);
        changePassword.setDisable(busy);
    }

    private static String join(List<String> messages) {
        return messages == null || messages.isEmpty() ? null : String.join(" ", messages);
    }
}

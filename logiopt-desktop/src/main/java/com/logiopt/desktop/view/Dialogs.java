package com.logiopt.desktop.view;

import com.logiopt.app.SchemaStatus;
import com.logiopt.desktop.support.SchemaDialogText;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Modality;

import java.util.Optional;

/** Modal dialogs of the desktop client. */
public final class Dialogs {

    /** Retry button of the start-up dialog. */
    public static final ButtonType RETRY = new ButtonType("Retry", ButtonBar.ButtonData.OK_DONE);
    /** Exit button of the start-up dialog. */
    public static final ButtonType EXIT = new ButtonType("Exit", ButtonBar.ButtonData.CANCEL_CLOSE);

    private static final double BODY_WIDTH = 460;

    private Dialogs() {
    }

    /**
     * @param status result of the schema check (not usable)
     * @return the dialog content with Retry and Exit
     */
    public static DialogPane schemaPane(SchemaStatus status) {
        SchemaDialogText text = SchemaDialogText.of(status);
        return pane(text.getHeader(), text.getContent(), RETRY, EXIT);
    }

    /** @return a themed dialog pane: bold header, wrapping body text, the given buttons (first is primary) */
    static DialogPane pane(String header, String body, ButtonType... buttons) {
        DialogPane pane = new DialogPane();
        Theme.applyRoot(pane);
        pane.setBackground(Theme.background(Theme.SURFACE, 0));
        Label title = Theme.cardTitle(header);
        Text text = new Text(body);
        text.setId("dialogBody");
        text.setFont(Theme.font(14));
        text.setFill(Theme.TEXT);
        text.setWrappingWidth(BODY_WIDTH);
        VBox content = new VBox(Theme.space(2), title, text);
        content.setPadding(new Insets(Theme.space(3), Theme.space(3), Theme.space(1), Theme.space(3)));
        pane.setContent(content);
        pane.getButtonTypes().setAll(buttons);
        for (int i = 0; i < buttons.length; i++) {
            Button button = (Button) pane.lookupButton(buttons[i]);
            if (i == 0 && buttons.length > 1) {
                Theme.makePrimary(button);
            } else {
                Theme.makeSecondary(button);
            }
        }
        pane.setPrefWidth(BODY_WIDTH + Theme.space(6) + 4);
        return pane;
    }

    /**
     * Shows the start-up dialog and waits.
     *
     * @param status result of the schema check (not usable)
     * @return true for Retry, false for Exit or closing the dialog
     */
    public static boolean askRetry(SchemaStatus status) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(SchemaDialogText.TITLE);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setDialogPane(schemaPane(status));
        Optional<ButtonType> choice = dialog.showAndWait();
        return choice.isPresent() && choice.get() == RETRY;
    }

    /** "Yes" of a confirmation dialog. */
    public static final ButtonType YES = new ButtonType("Yes, cancel it", ButtonBar.ButtonData.OK_DONE);
    /** "No" of a confirmation dialog. */
    public static final ButtonType NO = new ButtonType("Keep it", ButtonBar.ButtonData.CANCEL_CLOSE);

    /**
     * Asks for confirmation and waits.
     *
     * @param header question
     * @param body   consequences
     * @param owner  a node of the window the dialog belongs to
     * @return true if confirmed
     */
    public static boolean confirm(String header, String body, Node owner) {
        return confirm(header, body, YES, NO, owner);
    }

    /**
     * Asks for confirmation with own button labels and waits.
     *
     * @param header question
     * @param body   consequences
     * @param yes    label of the confirming button
     * @param no     label of the refusing button
     * @param owner  a node of the window the dialog belongs to
     * @return true if confirmed
     */
    public static boolean confirm(String header, String body, String yes, String no, Node owner) {
        return confirm(header, body, new ButtonType(yes, ButtonBar.ButtonData.OK_DONE),
                new ButtonType(no, ButtonBar.ButtonData.CANCEL_CLOSE), owner);
    }

    private static boolean confirm(String header, String body, ButtonType yes, ButtonType no, Node owner) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Please confirm");
        dialog.initModality(Modality.WINDOW_MODAL);
        if (owner.getScene() != null && owner.getScene().getWindow() != null) {
            dialog.initOwner(owner.getScene().getWindow());
        }
        dialog.setDialogPane(pane(header, body, yes, no));
        Optional<ButtonType> choice = dialog.showAndWait();
        return choice.isPresent() && choice.get() == yes;
    }

    /**
     * Shows a modal error with a single Exit button and waits.
     *
     * @param header  short header line
     * @param message details (no stack trace)
     */
    public static void fatal(String header, String message) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(SchemaDialogText.TITLE);
        dialog.initModality(Modality.APPLICATION_MODAL);
        DialogPane pane = pane(header, message, EXIT);
        dialog.setDialogPane(pane);
        dialog.showAndWait();
    }
}

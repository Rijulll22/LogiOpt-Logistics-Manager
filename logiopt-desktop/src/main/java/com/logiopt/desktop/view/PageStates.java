package com.logiopt.desktop.view;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** Loading and error states shared by the pages. */
final class PageStates {

    private PageStates() {
    }

    /** @return a centered spinner with a short text */
    static Node loading(String text) {
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(28, 28);
        HBox box = new HBox(Theme.space(2), spinner, Theme.muted(text));
        box.setAlignment(Pos.CENTER);
        box.setId("loading");
        VBox card = Theme.card();
        card.setAlignment(Pos.CENTER);
        card.setMinHeight(160);
        card.getChildren().add(box);
        return card;
    }

    /**
     * @param message what went wrong
     * @param action  label of the button, or null for none
     * @param onClick button action
     * @return a card with the error banner and a button
     */
    static Node error(String message, String action, Runnable onClick) {
        VBox card = Theme.card();
        card.setId("errorState");
        Label banner = Theme.banner(Theme.Banner.ERROR);
        Theme.setMessage(banner, message);
        card.getChildren().add(banner);
        if (action != null) {
            Button button = Theme.secondaryButton(action);
            button.setId("errorAction");
            button.setOnAction(e -> onClick.run());
            card.getChildren().add(button);
        }
        card.setMaxHeight(VBox.USE_PREF_SIZE);
        return card;
    }
}

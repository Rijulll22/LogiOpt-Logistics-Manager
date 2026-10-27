package com.logiopt.desktop.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.FontWeight;

/** The full-window gradient with one centered card, shared by the sign-in and registration screens. */
final class WelcomePage {

    /** Width of the centered card. */
    static final double CARD_WIDTH = 440;

    private WelcomePage() {
    }

    /**
     * @param heading  card heading
     * @param subtitle line under it
     * @return the card to fill (brand, heading and subtitle already added)
     */
    static VBox card(String heading, String subtitle) {
        VBox card = Theme.card();
        card.setSpacing(Theme.space(2));
        card.setPadding(new Insets(Theme.space(5), Theme.space(5), Theme.space(4), Theme.space(5)));
        card.setPrefWidth(CARD_WIDTH);
        card.setMaxWidth(CARD_WIDTH);
        card.setMaxHeight(VBox.USE_PREF_SIZE);
        Label brand = Theme.text("LogiOpt", 16, FontWeight.BOLD, Theme.TEAL);
        Label title = Theme.displayHeading(heading);
        Label sub = Theme.muted(subtitle);
        VBox head = new VBox(Theme.space(1), brand, title, sub);
        head.setPadding(new Insets(0, 0, Theme.space(1), 0));
        card.getChildren().add(head);
        return card;
    }

    /**
     * @return the gradient page with the card in its centre; it scrolls vertically when the window is
     *         shorter than the card
     */
    static ScrollPane page(VBox card) {
        StackPane page = new StackPane(card);
        Theme.applyRoot(page);
        page.setBackground(Theme.welcomeBackground());
        page.setPadding(new Insets(Theme.space(4)));
        StackPane.setAlignment(card, Pos.CENTER);
        ScrollPane scroll = new ScrollPane(page);
        Theme.styleScroll(scroll);
        scroll.setFitToHeight(true);
        return scroll;
    }

    /** @return a label-over-field group */
    static VBox field(String label, Node input, Label error) {
        VBox group = new VBox(Theme.space(1) - 2, Theme.fieldLabel(label), input);
        if (error != null) {
            group.getChildren().add(error);
        }
        return group;
    }
}

package com.logiopt.desktop.view;

import com.logiopt.desktop.presenter.Models.OrderSummaryModel;
import com.logiopt.desktop.presenter.Models.StrategyOption;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.util.List;

/**
 * "Review your order": every detail of the chosen route before anything is booked. Only Confirm
 * booking books; Cancel closes without a call. Confirm is disabled while the booking runs.
 */
public final class OrderSummaryView {

    /** Receives the confirmed route and strategy. */
    @FunctionalInterface
    public interface Confirm {
        /**
         * @param routeId     route to book
         * @param strategyKey strategy the booking is recorded under
         */
        void accept(long routeId, String strategyKey);
    }

    private final BorderPane root = new BorderPane();
    private final ComboBox<StrategyOption> strategy = new ComboBox<>();
    private final Label error = Theme.banner(Theme.Banner.ERROR);
    private final Button confirm = Theme.primaryButton("Confirm booking");
    private final Button cancel = Theme.secondaryButton("Cancel");
    private final OrderSummaryModel model;

    /** @param model the order to review */
    public OrderSummaryView(OrderSummaryModel model) {
        this.model = model;
        Theme.applyRoot(root);
        root.setBackground(Theme.background(Theme.SURFACE, 0));
        root.setId("orderSummary");
        strategy.setId("orderStrategy");
        confirm.setId("confirmBooking");
        cancel.setId("cancelOrder");
        error.setId("orderError");
        strategy.setItems(FXCollections.observableArrayList(model.strategies()));
        strategy.setConverter(new StringConverter<>() {
            @Override
            public String toString(StrategyOption option) {
                return option == null ? "" : option.name();
            }

            @Override
            public StrategyOption fromString(String text) {
                return null;
            }
        });
        for (StrategyOption option : model.strategies()) {
            if (option.key().equals(model.selected())) {
                strategy.setValue(option);
            }
        }
        Theme.styleInput(strategy);
        strategy.setMaxWidth(260);
        VBox head = new VBox(2, Theme.cardTitle("Review your order"), Theme.muted(model.title()
                + " · nothing is booked until you choose Confirm booking."));
        VBox strategyBox = new VBox(Theme.space(1) - 2, Theme.fieldLabel("Book under strategy"), strategy,
                Theme.muted("Pre-selected from the shipment's optimisation preference. The booking records this "
                        + "strategy's reason."));
        VBox body = new VBox(Theme.space(3), head, SummaryBlock.of(model.summary(), List.of()), strategyBox, error);
        body.setPadding(new Insets(Theme.space(3)));
        body.setBackground(Theme.background(Theme.SURFACE, 0));
        ScrollPane scroll = new ScrollPane(body);
        Theme.styleScroll(scroll);
        root.setCenter(scroll);
        confirm.setDefaultButton(true);
        cancel.setCancelButton(true);
        HBox buttons = new HBox(Theme.space(2), Theme.spacer(), cancel, confirm);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        buttons.setPadding(new Insets(Theme.space(2), Theme.space(3), Theme.space(2), Theme.space(3)));
        root.setBottom(new VBox(Theme.divider(), buttons));
    }

    /** @return dialog content */
    public BorderPane root() {
        return root;
    }

    /** @param action receives route and strategy when Confirm booking is chosen */
    public void onConfirm(Confirm action) {
        confirm.setOnAction(e -> {
            if (!confirm.isDisabled()) {
                action.accept(model.routeId(), strategy.getValue() == null ? model.selected() : strategy.getValue().key());
            }
        });
    }

    /** @param action closes the dialog without booking */
    public void onCancel(Runnable action) {
        cancel.setOnAction(e -> action.run());
    }

    /** @param busy true while booking (Confirm disabled, a second click does nothing) */
    public void setBusy(boolean busy) {
        confirm.setDisable(busy);
        cancel.setDisable(busy);
        strategy.setDisable(busy);
        confirm.setText(busy ? "Booking…" : "Confirm booking");
    }

    /** @param message why booking failed, or null to clear it */
    public void showError(String message) {
        Theme.setMessage(error, message);
    }
}

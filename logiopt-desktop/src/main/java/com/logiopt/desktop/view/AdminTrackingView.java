package com.logiopt.desktop.view;

import com.logiopt.desktop.presenter.AdminTrackingPresenter;
import com.logiopt.desktop.presenter.Models.AdminTrackingRow;
import com.logiopt.desktop.support.ChipKind;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.FontWeight;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * The administrator's tracking screen: one card per confirmed, undelivered booking with its latest
 * milestone and only the single legal next one. Record needs a description; Advance (simulated)
 * runs the idempotent simulation step. Both ask for confirmation, and every button is disabled while
 * a change runs.
 */
public final class AdminTrackingView implements AdminTrackingPresenter.View {

    private final VBox root = new VBox(Theme.space(3));
    private final Label message = Theme.banner(Theme.Banner.SUCCESS);
    private final Label problem = Theme.banner(Theme.Banner.ERROR);
    private final VBox cards = new VBox(Theme.space(2));
    private final List<Button> buttons = new ArrayList<>();
    private BiConsumer<AdminTrackingRow, String> onRecord = (row, text) -> { };
    private Consumer<AdminTrackingRow> onAdvance = row -> { };
    private Runnable retry = () -> { };

    /** Builds the page. */
    public AdminTrackingView() {
        root.setId("adminTracking");
        message.setId("trackingMessage");
        problem.setId("trackingProblem");
        cards.setId("trackingCards");
    }

    /** @return page content */
    public VBox root() {
        return root;
    }

    /** @param action records the next milestone with a description */
    public void onRecord(BiConsumer<AdminTrackingRow, String> action) {
        onRecord = action;
    }

    /** @param action runs the simulated step */
    public void onAdvance(Consumer<AdminTrackingRow> action) {
        onAdvance = action;
    }

    /** @param action reloads after an error */
    public void onRetry(Runnable action) {
        retry = action;
    }

    @Override
    public void showLoading() {
        root.getChildren().setAll(PageStates.loading("Loading tracking…"));
    }

    @Override
    public void showRows(List<AdminTrackingRow> rows) {
        buttons.clear();
        cards.getChildren().clear();
        if (rows.isEmpty()) {
            VBox empty = Theme.card();
            empty.setId("trackingEmpty");
            empty.getChildren().addAll(Theme.cardTitle("Nothing to track"), Theme.muted("Confirmed bookings that are "
                    + "not yet delivered appear here. A booking is confirmed by its successful payment."));
            cards.getChildren().add(empty);
        }
        for (AdminTrackingRow row : rows) {
            cards.getChildren().add(card(row));
        }
        VBox intro = new VBox(2, Theme.muted("Only the single next milestone of each booking can be recorded. "
                + "Milestones cannot be skipped, repeated or undone."));
        root.getChildren().setAll(message, problem, intro, cards);
    }

    private VBox card(AdminTrackingRow row) {
        VBox card = Theme.card();
        card.setId("tracking-" + row.bookingId());
        HBox now = new HBox(Theme.space(1), Theme.fieldLabel("Now"), Theme.chip(row.current(), ChipKind.INFO),
                Theme.muted(row.updated()), Theme.spacer(), Theme.fieldLabel("Next"),
                Theme.chip(row.next(), ChipKind.WARNING));
        now.setAlignment(Pos.CENTER_LEFT);
        TextArea description = Theme.textArea("What happened? Required for Record, e.g. \"Collected from the "
                + "warehouse\".");
        description.setId("description-" + row.bookingId());
        Label error = Theme.fieldError();
        Button record = Theme.primaryButton("Record " + row.next());
        record.setId("record-" + row.bookingId());
        record.setMinWidth(Region.USE_PREF_SIZE);
        record.setOnAction(e -> {
            String text = description.getText() == null ? "" : description.getText().trim();
            if (text.isEmpty()) {
                Theme.setMessage(error, "A description is required.");
                return;
            }
            Theme.setMessage(error, null);
            if (Dialogs.confirm("Record " + row.next() + " for booking #" + row.bookingId() + "?", "The customer is "
                    + "notified. A recorded milestone cannot be undone.", "Record it", "Not now", root)) {
                onRecord.accept(row, text);
            }
        });
        Button advance = Theme.secondaryButton("Advance (simulated)");
        advance.setId("advance-" + row.bookingId());
        advance.setMinWidth(Region.USE_PREF_SIZE);
        advance.setOnAction(e -> {
            if (Dialogs.confirm("Advance booking #" + row.bookingId() + " to " + row.next() + "?", "A simulated "
                    + "update is recorded and the customer is notified.", "Advance", "Not now", root)) {
                onAdvance.accept(row);
            }
        });
        buttons.add(record);
        buttons.add(advance);
        HBox actions = new HBox(Theme.space(2), Theme.spacer(), advance, record);
        actions.setAlignment(Pos.CENTER_RIGHT);
        card.getChildren().addAll(new VBox(2, Theme.text("Booking #" + row.bookingId() + " · " + row.route(), 16,
                FontWeight.BOLD, Theme.TEXT), Theme.muted(row.routeName())), now,
                new VBox(Theme.space(1) / 2, Theme.fieldLabel("Description"), description, error), actions);
        return card;
    }

    @Override
    public void showError(String text) {
        root.getChildren().setAll(PageStates.error(text, "Try again", retry));
    }

    @Override
    public void showMessage(boolean success, String text) {
        Theme.setMessage(message, success ? text : null);
        Theme.setMessage(problem, success ? null : text);
    }

    @Override
    public void setBusy(boolean busy) {
        for (Button b : buttons) {
            b.setDisable(busy);
        }
    }
}

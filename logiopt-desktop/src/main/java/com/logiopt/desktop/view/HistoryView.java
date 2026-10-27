package com.logiopt.desktop.view;

import com.logiopt.desktop.presenter.HistoryPresenter;
import com.logiopt.desktop.presenter.Models.BookingRow;
import com.logiopt.desktop.presenter.Models.HistoryModel;
import com.logiopt.desktop.presenter.Models.ReportRow;
import com.logiopt.desktop.presenter.Models.ShipmentRow;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

/** History page: shipments, bookings and reports behind a segmented control, each a full-height table with its note. */
public final class HistoryView implements HistoryPresenter.View {

    private final VBox root = new VBox(Theme.space(3));
    private Runnable retry = () -> { };

    /** Builds the empty page. */
    public HistoryView() {
        root.setId("history");
    }

    /** @return page content */
    public VBox root() {
        return root;
    }

    /** @param action reloads after an error */
    public void onRetry(Runnable action) {
        retry = action;
    }

    @Override
    public void showLoading() {
        root.getChildren().setAll(PageStates.loading("Loading your history…"));
    }

    @Override
    public void showError(String message) {
        root.getChildren().setAll(PageStates.error(message, "Try again", retry));
    }

    @Override
    public void showHistory(HistoryModel model) {
        TableView<ShipmentRow> shipments = Tables.table("No shipments yet.");
        shipments.setId("historyShipments");
        shipments.getColumns().add(Tables.text("#", r -> String.valueOf(r.id()), 0.6, 40));
        shipments.getColumns().add(Tables.text("Route", ShipmentRow::route, 2.2, 100));
        shipments.getColumns().add(Tables.text("Cargo", ShipmentRow::cargo, 1.1, 84));
        shipments.getColumns().add(Tables.text("Weight", ShipmentRow::weight, 1.2, 76));
        shipments.getColumns().add(Tables.text("Dispatch date", ShipmentRow::dispatch, 1.3, 108));
        shipments.getColumns().add(Tables.chip("Status", ShipmentRow::status, ShipmentRow::chip, ShipmentRow::hint, 1.2, 104));
        Tables.show(shipments, model.shipments());

        TableView<BookingRow> bookings = Tables.table("No bookings yet.");
        bookings.setId("historyBookings");
        bookings.getColumns().add(Tables.text("Booking", r -> "#" + r.id(), 0.8, 56));
        bookings.getColumns().add(Tables.text("Shipment", r -> "#" + r.shipmentId(), 0.8, 64));
        bookings.getColumns().add(Tables.text("Route", BookingRow::route, 1.9, 100));
        bookings.getColumns().add(Tables.text("Route name", BookingRow::routeName, 1.6, 84));
        bookings.getColumns().add(Tables.text("Total", BookingRow::total, 1.3, 100));
        bookings.getColumns().add(Tables.chip("Status", BookingRow::status, BookingRow::chip, BookingRow::hint, 1.4, 128));
        bookings.getColumns().add(Tables.text("Booked", BookingRow::bookedAt, 1.4, 84));
        Tables.show(bookings, model.bookings());

        TableView<ReportRow> reports = Tables.table("No reports yet.");
        reports.setId("historyReports");
        reports.getColumns().add(Tables.text("Job", ReportRow::job, 1.0, 80));
        reports.getColumns().add(Tables.text("Shipment", r -> "#" + r.shipmentId(), 0.9, 64));
        reports.getColumns().add(Tables.text("Strategy", ReportRow::strategy, 1.1, 84));
        reports.getColumns().add(Tables.chip("Status", ReportRow::status, ReportRow::chip, ReportRow::hint, 1.0, 84));
        reports.getColumns().add(Tables.text("Created", ReportRow::created, 1.4, 84));
        Tables.show(reports, model.reports());

        StackPane content = new StackPane();
        HBox track = Theme.segmentTrack();
        track.setMinWidth(Region.USE_PREF_SIZE);
        track.setId("historyTabs");
        List<Button> segments = new ArrayList<>();
        List<Node> pages = List.of(tab(model.shipmentsNote(), shipments), tab(model.bookingsNote(), bookings),
                tab(model.reportsNote(), reports));
        String[] titles = {"Shipments (" + model.shipments().size() + ")", "Bookings (" + model.bookings().size() + ")",
                "Reports (" + model.reports().size() + ")"};
        for (int i = 0; i < titles.length; i++) {
            Button segment = Theme.segment(titles[i]);
            segment.setId("tab-" + i);
            int index = i;
            segment.setOnAction(e -> select(segments, pages, content, index));
            segments.add(segment);
            track.getChildren().add(segment);
        }
        select(segments, pages, content, 0);
        VBox card = Theme.card();
        HBox head = new HBox(Theme.space(2), new VBox(2, Theme.cardTitle("History"),
                Theme.muted("Your own shipments, bookings and reports.")), Theme.spacer(), track);
        head.setAlignment(Pos.CENTER_LEFT);
        card.getChildren().addAll(head, content);
        root.getChildren().setAll(card);
    }

    private static void select(List<Button> segments, List<Node> pages, StackPane content, int index) {
        for (int i = 0; i < segments.size(); i++) {
            Theme.setSegmentSelected(segments.get(i), i == index);
        }
        content.getChildren().setAll(pages.get(index));
    }

    private static VBox tab(String note, TableView<?> table) {
        return new VBox(Theme.space(1) + 4, Theme.muted(note), table);
    }
}

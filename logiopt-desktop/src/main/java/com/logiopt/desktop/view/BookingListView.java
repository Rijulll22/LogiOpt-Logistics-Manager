package com.logiopt.desktop.view;

import com.logiopt.desktop.presenter.BookingListPresenter;
import com.logiopt.desktop.presenter.Models.BookingRow;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import javafx.scene.text.FontWeight;

import java.util.List;
import java.util.function.Consumer;

/** "My bookings": status chips with tooltips and an Open action per booking. */
public final class BookingListView implements BookingListPresenter.View {

    private final VBox root = new VBox(Theme.space(3));
    private final TableView<BookingRow> table = Tables.table("No bookings yet. Compare a shipment's routes and choose "
            + "Book to make one.");
    private final VBox card = Theme.card();
    private Consumer<Long> onOpen = id -> { };
    private Runnable retry = () -> { };

    /** Builds the customer's page. */
    public BookingListView() {
        this(false);
    }

    /** @param all true for the administrator's read-only list of every booking */
    public BookingListView(boolean all) {
        root.setId("bookingList");
        table.setId("bookingsTable");
        table.getColumns().add(Tables.text("Booking", r -> "#" + r.id(), 0.6, 72));
        table.getColumns().add(Tables.node("Route", BookingListView::routeCell, 2.0, 150));
        table.getColumns().add(Tables.text("Total", BookingRow::total, 1.1, 110));
        table.getColumns().add(Tables.chip("Status", BookingRow::status, BookingRow::chip, BookingRow::hint, 1.1, 128));
        table.getColumns().add(Tables.node("Booked", BookingListView::bookedCell, 1.1, 88));
        table.getColumns().add(Tables.node("", this::openButton, 0.6, 60));
        card.getChildren().addAll(new VBox(2, Theme.cardTitle(all ? "All bookings" : "Bookings"),
                Theme.muted(all ? "Every customer's bookings, newest first. Read-only: only the customer can pay or "
                        + "cancel." : "Pay or cancel an unpaid booking from its page.")), table);
    }

    private static Node routeCell(BookingRow row) {
        return new VBox(1, Theme.text(row.route(), 13, FontWeight.BOLD, Theme.TEXT),
                Theme.text(row.routeName() + " · shipment #" + row.shipmentId(), 12, FontWeight.NORMAL, Theme.MUTED));
    }

    /** Date over time, so the column stays narrow without wrapping mid-date. */
    private static Node bookedCell(BookingRow row) {
        String at = row.bookedAt();
        int space = at.indexOf(' ');
        if (space < 0) {
            return Theme.text(at, 13, FontWeight.NORMAL, Theme.TEXT);
        }
        return new VBox(1, Theme.text(at.substring(0, space), 13, FontWeight.NORMAL, Theme.TEXT),
                Theme.text(at.substring(space + 1), 12, FontWeight.NORMAL, Theme.MUTED));
    }

    private Node openButton(BookingRow row) {
        Button open = Theme.linkButton("Open");
        open.setId("open-" + row.id());
        open.setOnAction(e -> onOpen.accept(row.id()));
        return open;
    }

    /** @return page content */
    public VBox root() {
        return root;
    }

    /** @param action opens a booking */
    public void onOpen(Consumer<Long> action) {
        onOpen = action;
    }

    /** @param action reloads after an error */
    public void onRetry(Runnable action) {
        retry = action;
    }

    @Override
    public void showLoading() {
        root.getChildren().setAll(PageStates.loading("Loading your bookings…"));
    }

    @Override
    public void showBookings(List<BookingRow> rows) {
        Tables.show(table, rows);
        root.getChildren().setAll(card);
    }

    @Override
    public void showError(String message) {
        root.getChildren().setAll(PageStates.error(message, "Try again", retry));
    }
}

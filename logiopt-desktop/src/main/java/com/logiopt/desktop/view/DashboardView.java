package com.logiopt.desktop.view;

import com.logiopt.desktop.presenter.DashboardPresenter;
import com.logiopt.desktop.presenter.Models.DashboardModel;
import com.logiopt.desktop.presenter.Models.StatCard;
import com.logiopt.desktop.presenter.Models.TrackingRow;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import javafx.scene.text.FontWeight;

import java.util.List;

/** Dashboard page: stat cards across the full width and, for customers, the tracking table. */
public final class DashboardView implements DashboardPresenter.View {

    /** Narrowest a stat card gets before the cards wrap to two per row. */
    static final double STAT_CARD_MIN_WIDTH = 200;

    private final VBox root = new VBox(Theme.space(3));
    private Runnable retry = () -> { };

    /** Builds the empty page. */
    public DashboardView() {
        root.setId("dashboard");
        root.setFillWidth(true);
        root.setMaxWidth(Double.MAX_VALUE);
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
        root.getChildren().setAll(PageStates.loading("Loading your dashboard…"));
    }

    @Override
    public void showError(String message) {
        root.getChildren().setAll(PageStates.error(message, "Try again", retry));
    }

    @Override
    public void showDashboard(DashboardModel model) {
        Label heading = Theme.cardTitle(model.heading());
        heading.setFont(Theme.font(FontWeight.BOLD, 24));
        heading.setId("dashboardHeading");
        VBox header = new VBox(Theme.space(1) / 2, heading, Theme.muted(model.subtitle()));
        root.getChildren().setAll(header, cards(model.cards()));
        if (model.showTracking()) {
            root.getChildren().add(tracking(model.tracking(), model.trackingNote()));
        }
    }

    private static CardGrid cards(List<StatCard> cards) {
        CardGrid grid = new CardGrid(STAT_CARD_MIN_WIDTH, Theme.space(2));
        grid.setId("statCards");
        for (StatCard c : cards) {
            grid.getChildren().add(Theme.statCard(c.label(), c.value(), c.caption()));
        }
        return grid;
    }

    private static VBox tracking(List<TrackingRow> rows, String note) {
        VBox card = Theme.card();
        card.setId("trackingCard");
        TableView<TrackingRow> table = Tables.table("No confirmed bookings yet. Bookings appear here with their latest "
                + "milestone once they are paid.");
        table.setId("trackingTable");
        table.getColumns().add(Tables.text("Booking", TrackingRow::booking, 0.9, 64));
        table.getColumns().add(Tables.text("Route", TrackingRow::route, 1.8, 110));
        table.getColumns().add(Tables.text("Route name", TrackingRow::routeName, 1.6, 100));
        table.getColumns().add(Tables.text("Latest milestone", TrackingRow::milestone, 1.5, 110));
        table.getColumns().add(Tables.text("Updated", TrackingRow::updated, 1.6, 90));
        Tables.show(table, rows);
        VBox head = new VBox(2, Theme.cardTitle("Tracking"), Theme.muted(note));
        head.setPadding(new Insets(0, 0, Theme.space(1) / 2, 0));
        card.getChildren().addAll(head, table);
        return card;
    }
}

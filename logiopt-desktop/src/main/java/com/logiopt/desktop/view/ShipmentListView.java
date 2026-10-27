package com.logiopt.desktop.view;

import com.logiopt.desktop.presenter.Models.ShipmentRow;
import com.logiopt.desktop.presenter.ShipmentListPresenter;
import com.logiopt.desktop.support.StatusHelp;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.FontWeight;

import java.util.List;
import java.util.function.Consumer;

/** "My shipments": status chips with tooltips, Compare on every row, Edit on drafts, and a status note. */
public final class ShipmentListView implements ShipmentListPresenter.View {

    private final VBox root = new VBox(Theme.space(3));
    private final Button create = Theme.primaryButton("New shipment");
    private final Label notice = Theme.banner(Theme.Banner.SUCCESS);
    private final TableView<ShipmentRow> table = Tables.table("No shipments yet. Choose New shipment to create one.");
    private final VBox card = Theme.card();
    private Consumer<Long> onEdit = id -> { };
    private Consumer<Long> onCompare = id -> { };
    private Runnable retry = () -> { };

    /** Builds the page. */
    public ShipmentListView() {
        root.setId("shipmentList");
        create.setId("newShipment");
        notice.setId("notice");
        table.setId("shipmentsTable");
        table.getColumns().add(Tables.text("#", r -> String.valueOf(r.id()), 0.5, 40));
        table.getColumns().add(Tables.node("Shipment", ShipmentListView::shipmentCell, 2.4, 140));
        table.getColumns().add(Tables.text("Dispatch date", ShipmentRow::dispatch, 1.1, 100));
        table.getColumns().add(Tables.text("Preference", ShipmentRow::preference, 1.0, 84));
        table.getColumns().add(Tables.chip("Status", ShipmentRow::status, ShipmentRow::chip, ShipmentRow::hint, 1.0, 104));
        table.getColumns().add(Tables.node("", this::actions, 1.1, 130));
        Label subtitle = Theme.muted("Your saved shipments. Drafts can be edited until they are booked.");
        HBox head = new HBox(Theme.space(2), new VBox(2, Theme.cardTitle("Shipments"), subtitle), Theme.spacer(), create);
        head.setAlignment(Pos.CENTER_LEFT);
        Label help = Theme.muted(StatusHelp.SHIPMENT_NOTE);
        help.setId("statusHelp");
        card.getChildren().addAll(head, table, help);
    }

    /** @return the route with cargo and weight underneath */
    private static Node shipmentCell(ShipmentRow row) {
        Label route = Theme.text(row.route(), 13, FontWeight.BOLD, Theme.TEXT);
        Label detail = Theme.text(row.cargo() + " \u00b7 " + row.weight(), 12, FontWeight.NORMAL, Theme.MUTED);
        return new VBox(1, route, detail);
    }

    /** @return "Compare" on every row and "Edit" on drafts */
    private Node actions(ShipmentRow row) {
        Button compare = Theme.linkButton("Compare");
        compare.setId("compare-" + row.id());
        compare.setOnAction(e -> onCompare.accept(row.id()));
        HBox box = new HBox(Theme.space(1), compare);
        if (row.editable()) {
            Button edit = Theme.linkButton("Edit");
            edit.setId("edit-" + row.id());
            edit.setOnAction(e -> onEdit.accept(row.id()));
            box.getChildren().add(edit);
        }
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    /** @return page content */
    public VBox root() {
        return root;
    }

    /** @param action opens the new-shipment form */
    public void onNew(Runnable action) {
        create.setOnAction(e -> action.run());
    }

    /** @param action opens the route comparison of a shipment */
    public void onCompare(Consumer<Long> action) {
        onCompare = action;
    }

    /** @param action opens the edit form of a draft */
    public void onEdit(Consumer<Long> action) {
        onEdit = action;
    }

    /** @param action reloads after an error */
    public void onRetry(Runnable action) {
        retry = action;
    }

    /** @param message success notice above the table, or null */
    public void showNotice(String message) {
        Theme.setMessage(notice, message);
    }

    @Override
    public void showLoading() {
        root.getChildren().setAll(notice, PageStates.loading("Loading your shipments…"));
    }

    @Override
    public void showShipments(List<ShipmentRow> rows) {
        Tables.show(table, rows);
        root.getChildren().setAll(notice, card);
    }

    @Override
    public void showError(String message) {
        root.getChildren().setAll(PageStates.error(message, "Try again", retry));
    }
}

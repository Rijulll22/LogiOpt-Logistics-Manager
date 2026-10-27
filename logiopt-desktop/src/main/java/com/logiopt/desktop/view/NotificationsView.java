package com.logiopt.desktop.view;

import com.logiopt.desktop.presenter.Models.NotificationRow;
import com.logiopt.desktop.presenter.Models.NotificationsModel;
import com.logiopt.desktop.presenter.NotificationsPresenter;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.FontWeight;

import java.util.function.BiConsumer;
import java.util.function.LongConsumer;

/**
 * The user's notifications, newest first: an unread dot and bold text until read, Mark as read per
 * row, Mark all as read, and Open to the related booking.
 */
public final class NotificationsView implements NotificationsPresenter.View {

    private final VBox root = new VBox(Theme.space(3));
    private final Label message = Theme.banner(Theme.Banner.NOTICE);
    private final TableView<NotificationRow> table = Tables.table("No notifications yet. Booking, payment and "
            + "tracking updates appear here.");
    private final Label summary = Theme.muted("");
    private final Button markAll = Theme.secondaryButton("Mark all as read");
    private final VBox card = Theme.card();
    private LongConsumer onMarkRead = id -> { };
    private Runnable onMarkAll = () -> { };
    private BiConsumer<Long, Long> onOpen = (id, booking) -> { };
    private Runnable retry = () -> { };

    /** Builds the page. */
    public NotificationsView() {
        root.setId("notifications");
        message.setId("notificationsMessage");
        table.setId("notificationsTable");
        summary.setId("notificationsSummary");
        markAll.setId("markAllRead");
        markAll.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        markAll.setOnAction(e -> onMarkAll.run());
        table.getColumns().add(Tables.node("", r -> r.unread() ? Theme.unreadDot() : Theme.readDot(), 0.25, 28));
        table.getColumns().add(Tables.node("Notification", NotificationsView::messageCell, 3.4, 220));
        table.getColumns().add(Tables.text("Received", NotificationRow::time, 1.1, 120));
        table.getColumns().add(Tables.node("", this::actions, 1.3, 150));
        HBox head = new HBox(Theme.space(2), new VBox(2, Theme.cardTitle("Notifications"), summary), Theme.spacer(),
                markAll);
        head.setAlignment(Pos.CENTER_LEFT);
        card.getChildren().addAll(head, table);
    }

    private static Node messageCell(NotificationRow row) {
        Label text = Theme.text(row.message(), 13, row.unread() ? FontWeight.BOLD : FontWeight.NORMAL, Theme.TEXT);
        Tooltip full = new Tooltip(row.message());
        full.setWrapText(true);
        full.setMaxWidth(420);
        text.setTooltip(full);
        return new VBox(1, Theme.text(row.type() + " · booking #" + row.bookingId(), 12, FontWeight.NORMAL,
                Theme.MUTED), text);
    }

    private Node actions(NotificationRow row) {
        Button open = Theme.linkButton("Open");
        open.setId("openNotification-" + row.id());
        open.setOnAction(e -> onOpen.accept(row.id(), row.bookingId()));
        HBox box = new HBox(Theme.space(1), open);
        box.setAlignment(Pos.CENTER_LEFT);
        if (row.unread()) {
            Button read = Theme.linkButton("Mark as read");
            read.setId("markRead-" + row.id());
            read.setOnAction(e -> onMarkRead.accept(row.id()));
            box.getChildren().add(read);
        }
        return box;
    }

    /** @return page content */
    public VBox root() {
        return root;
    }

    /** @param action marks one notification as read */
    public void onMarkRead(LongConsumer action) {
        onMarkRead = action;
    }

    /** @param action marks every notification as read */
    public void onMarkAllRead(Runnable action) {
        onMarkAll = action;
    }

    /** @param action receives the notification id and its booking id */
    public void onOpen(BiConsumer<Long, Long> action) {
        onOpen = action;
    }

    /** @param action reloads after an error */
    public void onRetry(Runnable action) {
        retry = action;
    }

    @Override
    public void showLoading() {
        root.getChildren().setAll(PageStates.loading("Loading your notifications…"));
    }

    @Override
    public void showNotifications(NotificationsModel model) {
        summary.setText(model.summary());
        markAll.setDisable(model.unread() == 0);
        Tables.show(table, model.rows());
        root.getChildren().setAll(message, card);
    }

    @Override
    public void showError(String text) {
        root.getChildren().setAll(PageStates.error(text, "Try again", retry));
    }

    @Override
    public void showMessage(String text) {
        Theme.setMessage(message, text);
    }

    @Override
    public void setBusy(boolean busy) {
        card.setDisable(busy);
    }
}

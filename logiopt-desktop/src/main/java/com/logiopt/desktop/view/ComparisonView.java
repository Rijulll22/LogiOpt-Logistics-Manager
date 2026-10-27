package com.logiopt.desktop.view;

import com.logiopt.desktop.presenter.ComparisonPresenter;
import com.logiopt.desktop.presenter.Models.ChartBar;
import com.logiopt.desktop.presenter.Models.ChartModel;
import com.logiopt.desktop.presenter.Models.ComparisonModel;
import com.logiopt.desktop.presenter.Models.ComparisonRow;
import com.logiopt.desktop.presenter.Models.ExcludedRow;
import com.logiopt.desktop.presenter.Models.OrderSummaryModel;
import com.logiopt.desktop.presenter.Models.RecommendationCard;
import com.logiopt.desktop.presenter.Models.StrategyCard;
import com.logiopt.desktop.support.ChipKind;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Route comparison: the preferred strategy's recommendation, the four strategy cards, every eligible
 * route with the best value per column marked (★ and bold, not colour alone), three bar charts with one
 * colour per route, and the excluded and rejected routes. Book opens "Review your order".
 */
public final class ComparisonView implements ComparisonPresenter.View {

    /** Narrowest a strategy card gets before the cards wrap. */
    static final double STRATEGY_CARD_MIN_WIDTH = 220;
    /** Narrowest a chart gets before the charts wrap. */
    static final double CHART_MIN_WIDTH = 250;

    private final VBox root = new VBox(Theme.space(3));
    private Consumer<Long> onBook = id -> { };
    private OrderSummaryView.Confirm onConfirm = (route, key) -> { };
    private Runnable retry = () -> { };
    private Stage orderStage;
    private OrderSummaryView order;

    /** Builds the empty page. */
    public ComparisonView() {
        root.setId("comparison");
    }

    /** @return page content */
    public VBox root() {
        return root;
    }

    /** @param action receives the route of a clicked Book button */
    public void onBook(Consumer<Long> action) {
        onBook = action;
    }

    /** @param action receives route and strategy when the order is confirmed */
    public void onConfirm(OrderSummaryView.Confirm action) {
        onConfirm = action;
    }

    /** @param action reloads after an error */
    public void onRetry(Runnable action) {
        retry = action;
    }

    @Override
    public void showLoading() {
        root.getChildren().setAll(PageStates.loading("Comparing routes…"));
    }

    @Override
    public void showError(String message) {
        root.getChildren().setAll(PageStates.error(message, "Try again", retry));
    }

    @Override
    public void showComparison(ComparisonModel model) {
        List<Node> parts = new ArrayList<>();
        parts.add(header(model));
        parts.add(recommendation(model));
        parts.add(strategies(model.strategies()));
        parts.add(table(model));
        parts.add(charts(model));
        if (!model.excluded().isEmpty()) {
            parts.add(excluded(model.excluded()));
        }
        root.getChildren().setAll(parts);
    }

    private static VBox header(ComparisonModel model) {
        VBox card = Theme.card();
        card.setId("comparisonHeader");
        Label title = Theme.cardTitle(model.title() + " · shipment #" + model.shipmentId());
        HBox preference = new HBox(Theme.space(1), Theme.fieldLabel("Optimisation preference"),
                Theme.chip(model.preference(), ChipKind.INFO,
                        "Its strategy's winner is marked \"Your choice\" and pre-selected when booking."));
        preference.setAlignment(Pos.CENTER_LEFT);
        card.getChildren().addAll(new VBox(2, title, Theme.muted(model.details())), preference);
        if (!model.bookable()) {
            Label blocked = Theme.banner(Theme.Banner.NOTICE);
            blocked.setId("bookBlocked");
            Theme.setMessage(blocked, model.bookBlockedReason());
            card.getChildren().add(blocked);
        }
        return card;
    }

    private VBox recommendation(ComparisonModel model) {
        RecommendationCard r = model.recommendation();
        VBox card = Theme.card();
        card.setId("recommendationCard");
        Theme.highlightCard(card);
        Label kicker = Theme.text("RECOMMENDED FOR YOUR PREFERENCE · " + r.strategy().toUpperCase(), 12,
                FontWeight.BOLD, Theme.TEAL_DARK);
        if (!r.recommended()) {
            Label reason = Theme.banner(Theme.Banner.NOTICE);
            reason.setId("noRecommendation");
            Theme.setMessage(reason, r.reason());
            card.getChildren().addAll(kicker, reason);
            return card;
        }
        Label route = Theme.text(r.route(), 22, FontWeight.BOLD, Theme.TEXT);
        route.setId("recommendedRoute");
        HBox top = new HBox(Theme.space(1), route, Theme.modeBadge(r.mode()), Theme.yourChoice());
        top.setAlignment(Pos.CENTER_LEFT);
        Label total = Theme.text(r.total(), 18, FontWeight.BOLD, Theme.TEXT);
        Label highlight = Theme.text(r.highlight(), 14, FontWeight.BOLD, Theme.TEAL_DARK);
        highlight.setId("recommendedScore");
        Label reason = Theme.text(r.reason(), 13, FontWeight.NORMAL, Theme.TEXT);
        reason.setId("recommendedReason");
        Button book = Theme.primaryButton("Book this route");
        book.setId("bookRecommended");
        book.setDisable(!model.bookable());
        book.setOnAction(e -> onBook.accept(r.routeId()));
        HBox actions = new HBox(Theme.spacer(), book);
        card.getChildren().addAll(kicker, top, new HBox(Theme.space(3), total, highlight), reason, actions);
        return card;
    }

    private static CardGrid strategies(List<StrategyCard> cards) {
        CardGrid grid = new CardGrid(STRATEGY_CARD_MIN_WIDTH, Theme.space(2));
        grid.setId("strategyCards");
        for (StrategyCard c : cards) {
            VBox card = Theme.card();
            card.setSpacing(6);
            card.setId("strategy-" + c.key());
            HBox name = new HBox(Theme.space(1), Theme.text(c.name(), 16, FontWeight.BOLD, Theme.TEXT));
            name.setAlignment(Pos.CENTER_LEFT);
            if (c.yourChoice()) {
                name.getChildren().add(Theme.yourChoice());
                Theme.highlightCard(card);
            }
            card.getChildren().addAll(name, Theme.muted(c.description()));
            if (c.winner() != null) {
                card.getChildren().addAll(Theme.text(c.winner(), 14, FontWeight.BOLD, Theme.TEXT), Theme.muted(c.value()));
            } else {
                Label none = Theme.text("No recommendation", 14, FontWeight.BOLD, Theme.MUTED);
                card.getChildren().addAll(none, Theme.muted(c.value()));
            }
            grid.getChildren().add(card);
        }
        return grid;
    }

    private VBox table(ComparisonModel model) {
        VBox card = Theme.card();
        card.setId("comparisonTableCard");
        TableView<ComparisonRow> table = Tables.table("No route is eligible for this shipment.");
        table.setId("comparisonTable");
        table.getColumns().add(Tables.node("Route", ComparisonView::routeCell, 2.0, 150));
        table.getColumns().add(Tables.node("Total cost", r -> Theme.bestValue(r.cost(), r.costBest(), "Lowest total cost"),
                1.3, 116));
        table.getColumns().add(Tables.node("Duration", r -> Theme.bestValue(r.duration(), r.durationBest(),
                "Shortest duration"), 1.0, 84));
        table.getColumns().add(Tables.node("Emissions", r -> Theme.bestValue(r.emissions(), r.emissionsBest(),
                "Lowest emissions"), 1.2, 100));
        table.getColumns().add(Tables.node("Reliability", r -> Theme.bestValue(r.reliability(), r.reliabilityBest(),
                "Highest reliability"), 0.9, 80));
        table.getColumns().add(Tables.node("", r -> bookButton(r, model), 0.8, 70));
        Tables.show(table, model.rows());
        Label legend = Theme.muted("★ and bold mark the best value of each column: lowest cost, duration and "
                + "emissions, highest reliability. \"not available\" means the route has no data for it.");
        legend.setId("bestLegend");
        card.getChildren().addAll(new VBox(2, Theme.cardTitle("All eligible routes"),
                Theme.muted("Every strategy ranks these same evaluations.")), table, legend);
        return card;
    }

    private static Node routeCell(ComparisonRow row) {
        HBox badges = new HBox(6, Theme.modeBadge(row.mode()));
        if (row.yourChoice()) {
            badges.getChildren().add(Theme.yourChoice());
        }
        badges.setAlignment(Pos.CENTER_LEFT);
        return new VBox(2, Theme.text(row.route(), 13, FontWeight.BOLD, Theme.TEXT), badges);
    }

    private Node bookButton(ComparisonRow row, ComparisonModel model) {
        Button book = Theme.linkButton("Book");
        book.setId("book-" + row.routeId());
        book.setDisable(!model.bookable());
        if (!model.bookable()) {
            Label wrapper = new Label(null, book);
            wrapper.setTooltip(new Tooltip(model.bookBlockedReason()));
            return wrapper;
        }
        book.setOnAction(e -> onBook.accept(row.routeId()));
        return book;
    }

    private static VBox charts(ComparisonModel model) {
        VBox card = Theme.card();
        card.setId("chartsCard");
        CardGrid grid = new CardGrid(CHART_MIN_WIDTH, Theme.space(2));
        grid.setId("charts");
        List<Long> order = new ArrayList<>(model.legend().keySet());
        for (ChartModel c : model.charts()) {
            grid.getChildren().add(chart(c, order));
        }
        FlowPane legend = new FlowPane(Theme.space(2), Theme.space(1));
        legend.setId("chartLegend");
        for (Map.Entry<Long, String> e : model.legend().entrySet()) {
            Region swatch = new Region();
            swatch.setMinSize(12, 12);
            swatch.setMaxSize(12, 12);
            swatch.setBackground(Theme.background(Theme.routeColor(order.indexOf(e.getKey())), 3));
            HBox item = new HBox(6, swatch, Theme.text(e.getValue(), 12, FontWeight.NORMAL, Theme.TEXT));
            item.setAlignment(Pos.CENTER_LEFT);
            legend.getChildren().add(item);
        }
        card.getChildren().addAll(new VBox(2, Theme.cardTitle("Cost, duration and emissions"),
                Theme.muted("One colour per route in every chart; values from the same evaluations as the table.")),
                grid, legend);
        return card;
    }

    private static VBox chart(ChartModel c, List<Long> order) {
        if (c.bars().isEmpty()) {
            VBox empty = new VBox(Theme.space(1), Theme.text(c.title(), 14, FontWeight.BOLD, Theme.TEXT),
                    Theme.muted("No route has this value, so there is nothing to chart."));
            if (c.missingNote() != null) {
                empty.getChildren().add(Theme.muted(c.missingNote()));
            }
            empty.setId("chart-" + c.title().toLowerCase().replace(' ', '-'));
            empty.setAlignment(Pos.CENTER);
            empty.setMinHeight(140);
            return empty;
        }
        CategoryAxis x = new CategoryAxis();
        x.setLabel("Route");
        NumberAxis y = new NumberAxis();
        y.setLabel(c.axis());
        y.setForceZeroInRange(true);
        BarChart<String, Number> chart = new BarChart<>(x, y);
        chart.setTitle(c.title());
        chart.setId("chart-" + c.title().toLowerCase().replace(' ', '-'));
        Theme.styleChart(chart);
        chart.setCategoryGap(Theme.space(2));
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        for (ChartBar bar : c.bars()) {
            XYChart.Data<String, Number> data = new XYChart.Data<>(bar.label(), bar.value());
            Label value = Theme.text(bar.text(), 10, FontWeight.BOLD, Color.WHITE);
            value.setWrapText(false);
            StackPane node = new StackPane(value);
            StackPane.setAlignment(value, Pos.TOP_CENTER);
            node.setBackground(Theme.background(Theme.routeColor(order.indexOf(bar.routeId())), 3));
            Tooltip.install(node, new Tooltip(bar.label() + ": " + bar.text()));
            data.setNode(node);
            series.getData().add(data);
        }
        chart.getData().add(series);
        VBox box = new VBox(4, chart);
        if (c.missingNote() != null) {
            Label note = Theme.muted(c.missingNote());
            note.setId("missing-" + c.title().toLowerCase().replace(' ', '-'));
            box.getChildren().add(note);
        }
        return box;
    }

    private static VBox excluded(List<ExcludedRow> rows) {
        VBox card = Theme.card();
        card.setId("excludedCard");
        Theme.mutedCard(card);
        VBox list = new VBox(Theme.space(1));
        for (ExcludedRow r : rows) {
            list.getChildren().add(new VBox(1, Theme.text(r.route(), 13, FontWeight.BOLD, Theme.MUTED),
                    Theme.muted(r.reason())));
        }
        card.getChildren().addAll(new VBox(2, Theme.text("Not compared", 16, FontWeight.BOLD, Theme.MUTED),
                Theme.muted("Routes left out by your preferred strategy or not eligible for this shipment.")), list);
        return card;
    }

    @Override
    public void showOrderSummary(OrderSummaryModel model) {
        order = new OrderSummaryView(model);
        order.onConfirm((route, key) -> onConfirm.accept(route, key));
        Window owner = root.getScene() == null ? null : root.getScene().getWindow();
        if (owner == null) {
            return;
        }
        orderStage = new Stage();
        orderStage.initOwner(owner);
        orderStage.initModality(Modality.WINDOW_MODAL);
        orderStage.setTitle("Review your order");
        orderStage.setScene(new Scene(order.root(), 640, Math.min(720, owner.getHeight() - 40)));
        order.onCancel(orderStage::close);
        orderStage.show();
    }

    /** Closes "Review your order" (after a successful booking). */
    public void closeOrderSummary() {
        if (orderStage != null) {
            orderStage.close();
            orderStage = null;
        }
    }

    @Override
    public void setBookingBusy(boolean busy) {
        if (order != null) {
            order.setBusy(busy);
        }
    }

    @Override
    public void showBookingError(String message) {
        if (order != null) {
            order.showError(message);
        }
    }
}

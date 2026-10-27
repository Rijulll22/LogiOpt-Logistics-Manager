package com.logiopt.desktop.view;

import com.logiopt.desktop.presenter.Models.CostLine;
import com.logiopt.desktop.presenter.Models.LegRow;
import com.logiopt.desktop.presenter.Models.RouteSummary;
import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.FontWeight;

import java.util.List;

/**
 * The route details shown in "Review your order" and on the booking detail: key facts, every leg and
 * the cost breakdown. Only displays the model's text.
 */
final class SummaryBlock {

    private SummaryBlock() {
    }

    /**
     * @param s     route details
     * @param extra additional label/value pairs shown after the key facts (strategy, reason, ...)
     * @return the block
     */
    static VBox of(RouteSummary s, List<String[]> extra) {
        HBox route = new HBox(Theme.space(1), Theme.text(s.route(), 16, FontWeight.BOLD, Theme.TEXT),
                Theme.modeBadge(s.mode()));
        route.setAlignment(Pos.CENTER_LEFT);
        route.setId("summaryRoute");
        GridPane facts = pairs();
        int row = 0;
        for (String[] pair : List.of(new String[] {"Dispatch date", s.dispatch()},
                new String[] {"Optimisation preference", s.preference()}, new String[] {"Duration", s.duration()},
                new String[] {"Estimated arrival", s.arrival()}, new String[] {"Emissions", s.emissions()},
                new String[] {"Reliability", s.reliability()})) {
            addPair(facts, row++, pair[0], pair[1]);
        }
        for (String[] pair : extra) {
            addPair(facts, row++, pair[0], pair[1]);
        }
        return new VBox(Theme.space(2), route, facts, section("Legs", legs(s.legs())),
                section("Cost breakdown", costs(s.costs(), s.total())));
    }

    private static GridPane pairs() {
        GridPane grid = new GridPane();
        grid.setHgap(Theme.space(3));
        grid.setVgap(Theme.space(1));
        ColumnConstraints label = new ColumnConstraints();
        label.setMinWidth(170);
        ColumnConstraints value = new ColumnConstraints();
        value.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(label, value);
        return grid;
    }

    private static void addPair(GridPane grid, int row, String label, String value) {
        Label v = Theme.text(value, 13, FontWeight.NORMAL, Theme.TEXT);
        grid.addRow(row, Theme.fieldLabel(label), v);
    }

    private static VBox section(String title, Node body) {
        return new VBox(Theme.space(1), Theme.text(title, 14, FontWeight.BOLD, Theme.TEXT), body);
    }

    private static GridPane legs(List<LegRow> legs) {
        GridPane grid = new GridPane();
        grid.setId("summaryLegs");
        grid.setHgap(Theme.space(3));
        grid.setVgap(6);
        String[] head = {"Leg", "Mode", "Distance", "Speed"};
        for (int c = 0; c < head.length; c++) {
            grid.add(Theme.text(head[c], 12, FontWeight.BOLD, Theme.MUTED), c, 0);
        }
        int r = 1;
        for (LegRow leg : legs) {
            grid.add(Theme.text(String.valueOf(leg.number()), 13, FontWeight.NORMAL, Theme.TEXT), 0, r);
            grid.add(Theme.modeBadge(leg.mode()), 1, r);
            grid.add(Theme.text(leg.distance(), 13, FontWeight.NORMAL, Theme.TEXT), 2, r);
            grid.add(Theme.text(leg.speed(), 13, FontWeight.NORMAL, Theme.TEXT), 3, r);
            r++;
        }
        return grid;
    }

    private static GridPane costs(List<CostLine> lines, String total) {
        GridPane grid = new GridPane();
        grid.setId("summaryCosts");
        grid.setHgap(Theme.space(4));
        grid.setVgap(6);
        ColumnConstraints label = new ColumnConstraints();
        label.setMinWidth(170);
        ColumnConstraints amount = new ColumnConstraints();
        amount.setHalignment(HPos.RIGHT);
        grid.getColumnConstraints().addAll(label, amount);
        int r = 0;
        for (CostLine line : lines) {
            grid.addRow(r++, Theme.text(line.label(), 13, FontWeight.NORMAL, Theme.TEXT),
                    Theme.text(line.amount(), 13, FontWeight.NORMAL, Theme.TEXT));
        }
        Label totalLabel = Theme.text(total, 15, FontWeight.BOLD, Theme.TEXT);
        totalLabel.setId("summaryTotal");
        grid.addRow(r, Theme.text("Total", 14, FontWeight.BOLD, Theme.TEXT), totalLabel);
        return grid;
    }
}

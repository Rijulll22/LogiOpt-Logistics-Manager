package com.logiopt.desktop.view;

import com.logiopt.desktop.support.ChipKind;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;

import java.util.List;
import java.util.function.Function;

/**
 * Builds the tables of the desktop pages from view-model rows. Column widths are shares of the table
 * width (set with {@link #weight}), so tables stretch with the window without horizontal scrolling.
 */
final class Tables {

    private static final String WEIGHT_KEY = "logiopt.column.weight";
    private static final String MIN_KEY = "logiopt.column.min";

    private Tables() {
    }

    /**
     * @param placeholder text shown while the table is empty
     * @return a styled, stretching table
     */
    static <S> TableView<S> table(String placeholder) {
        TableView<S> table = new TableView<>();
        Theme.styleTable(table);
        Label empty = Theme.muted(placeholder);
        empty.setMaxWidth(420);
        empty.setAlignment(Pos.CENTER);
        table.setPlaceholder(empty);
        table.setRowFactory(t -> {
            TableRow<S> row = new TableRow<>();
            row.setBorder(Theme.rowSeparator());
            return row;
        });
        fitHeight(table, 0);
        table.widthProperty().addListener((obs, old, width) -> fit(table));
        table.needsLayoutProperty().addListener((obs, was, needs) -> {
            if (!needs) {
                fit(table);
            }
        });
        table.getColumns().addListener((ListChangeListener<TableColumn<S, ?>>) c -> fit(table));
        return table;
    }

    /**
     * Gives each column its minimum width plus its share (by weight) of the rest of the table width;
     * the last column takes what rounding leaves. Room is kept for the vertical scroll bar only while
     * it is shown, and a little slack keeps display-scale rounding from causing horizontal scrolling.
     */
    private static void fit(TableView<?> table) {
        List<? extends TableColumn<?, ?>> columns = table.getColumns();
        double weights = 0;
        double minimums = 0;
        for (TableColumn<?, ?> column : columns) {
            weights += weightOf(column);
            minimums += minOf(column);
        }
        double available = table.getWidth() - table.snappedLeftInset() - table.snappedRightInset()
                - verticalBarWidth(table) - (columns.size() + 2);
        if (columns.isEmpty() || weights <= 0 || available <= 0) {
            return;
        }
        double extra = Math.max(0, available - minimums);
        double used = 0;
        for (int i = 0; i < columns.size(); i++) {
            TableColumn<?, ?> column = columns.get(i);
            double width = i == columns.size() - 1 ? Math.max(minOf(column), available - used)
                    : minOf(column) + Math.floor(extra * weightOf(column) / weights);
            used += width;
            if (Math.abs(column.getPrefWidth() - width) > 0.5) {
                column.setPrefWidth(width);
            }
        }
    }

    private static double verticalBarWidth(TableView<?> table) {
        for (Node node : table.lookupAll(".scroll-bar")) {
            if (node instanceof ScrollBar && ((ScrollBar) node).getOrientation() == Orientation.VERTICAL
                    && node.isVisible()) {
                return ((ScrollBar) node).getWidth();
            }
        }
        return 0;
    }

    private static double minOf(TableColumn<?, ?> column) {
        Object min = column.getProperties().get(MIN_KEY);
        return min instanceof Double ? (Double) min : 0.0;
    }

    private static double weightOf(TableColumn<?, ?> column) {
        Object weight = column.getProperties().get(WEIGHT_KEY);
        return weight instanceof Double ? (Double) weight : 1.0;
    }

    /**
     * Sets the share of the spare table width a column takes, relative to the others, and the width
     * it never goes below (chosen so its content fits at the minimum window size).
     */
    static <S, T> TableColumn<S, T> weight(TableColumn<S, T> column, double weight, double minWidth) {
        column.getProperties().put(WEIGHT_KEY, weight);
        column.getProperties().put(MIN_KEY, minWidth);
        column.setSortable(false);
        column.setReorderable(false);
        column.setResizable(false);
        return column;
    }

    /**
     * @param title  header
     * @param value  cell text of a row
     * @param weight   share of the spare table width
     * @param minWidth narrowest width
     * @return text column with vertically centred, padded cells; long text wraps onto a second line
     */
    static <S> TableColumn<S, String> text(String title, Function<S, String> value, double weight, double minWidth) {
        TableColumn<S, String> column = new TableColumn<>(title);
        column.setCellValueFactory(c -> new ReadOnlyStringWrapper(value.apply(c.getValue())));
        column.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                setWrapText(true);
                setAlignment(Pos.CENTER_LEFT);
                setPadding(Theme.cellPadding());
            }
        });
        return weight(column, weight, minWidth);
    }

    /**
     * @param title  header
     * @param label  chip text of a row
     * @param kind   chip colour of a row
     * @param weight   width share
     * @param minWidth narrowest width (fits the longest chip)
     * @return column showing a status chip
     */
    static <S> TableColumn<S, S> chip(String title, Function<S, String> label, Function<S, ChipKind> kind, double weight,
                                       double minWidth) {
        TableColumn<S, S> column = new TableColumn<>(title);
        column.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        column.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(S item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                setAlignment(Pos.CENTER_LEFT);
                setPadding(Theme.cellPadding());
                setGraphic(empty || item == null ? null : Theme.chip(label.apply(item), kind.apply(item)));
            }
        });
        return weight(column, weight, minWidth);
    }

    /**
     * @param title    header
     * @param label    chip text of a row
     * @param kind     chip colour of a row
     * @param hint     what the status means, shown as the chip's tooltip
     * @param weight   width share
     * @param minWidth narrowest width (fits the longest chip)
     * @return column showing a status chip with a tooltip
     */
    static <S> TableColumn<S, S> chip(String title, Function<S, String> label, Function<S, ChipKind> kind,
                                       Function<S, String> hint, double weight, double minWidth) {
        return node(title, row -> Theme.chip(label.apply(row), kind.apply(row), hint.apply(row)), weight, minWidth);
    }

    /**
     * @param title    header
     * @param graphic  builds the cell content of a row
     * @param weight   width share
     * @param minWidth narrowest width
     * @return column whose cells show a node (badges, best-value marks, action links)
     */
    static <S> TableColumn<S, S> node(String title, Function<S, Node> graphic, double weight, double minWidth) {
        TableColumn<S, S> column = new TableColumn<>(title);
        column.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        column.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(S item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                setAlignment(Pos.CENTER_LEFT);
                setPadding(Theme.cellPadding());
                setGraphic(empty || item == null ? null : graphic.apply(item));
            }
        });
        return weight(column, weight, minWidth);
    }

    /** Replaces the rows of a table and sizes it to them. */
    static <S> void show(TableView<S> table, List<S> rows) {
        table.setItems(FXCollections.observableArrayList(rows));
        fitHeight(table, rows.size());
    }

    /**
     * Makes the table exactly as tall as its header and rows (no empty filler rows), up to
     * {@link Theme#TABLE_MAX_ROWS} rows, after which it scrolls; an empty table is tall enough for
     * its empty-state message.
     */
    static void fitHeight(TableView<?> table, int rows) {
        double height = rows == 0 ? Theme.TABLE_EMPTY_HEIGHT
                : Theme.TABLE_HEADER_HEIGHT + Math.min(rows, Theme.TABLE_MAX_ROWS) * Theme.ROW_HEIGHT + 2;
        table.setMinHeight(height);
        table.setPrefHeight(height);
        table.setMaxHeight(height);
    }
}

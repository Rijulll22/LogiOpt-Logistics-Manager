package com.logiopt.desktop.view;

import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.layout.Pane;

import java.util.List;

/**
 * Lays its children out as equal-width cards in rows. As many columns as fit at
 * {@code minColumnWidth} are used, reduced so every row is full (4 cards become 2×2 when narrow);
 * each row is as tall as its tallest card at that width, so wrapping text is never cut off.
 */
final class CardGrid extends Pane {

    private final double minColumnWidth;
    private final double gap;

    /**
     * @param minColumnWidth narrowest a card may get
     * @param gap            space between cards
     */
    CardGrid(double minColumnWidth, double gap) {
        this.minColumnWidth = minColumnWidth;
        this.gap = gap;
        setMaxWidth(Double.MAX_VALUE);
    }

    /** @return the number of columns used at a content width */
    int columnsFor(double width) {
        int count = getManagedChildren().size();
        if (count == 0) {
            return 1;
        }
        int fit = (int) Math.max(1, Math.floor((width + gap) / (minColumnWidth + gap)));
        int columns = Math.min(count, fit);
        while (columns > 1 && count % columns != 0) {
            columns--;
        }
        return columns;
    }

    private double columnWidth(double width, int columns) {
        return Math.max(0, (width - gap * (columns - 1)) / columns);
    }

    @Override
    public Orientation getContentBias() {
        return Orientation.HORIZONTAL;
    }

    @Override
    protected double computePrefWidth(double height) {
        Insets in = getInsets();
        List<Node> children = getManagedChildren();
        return in.getLeft() + in.getRight() + children.size() * minColumnWidth + Math.max(0, children.size() - 1) * gap;
    }

    @Override
    protected double computeMinWidth(double height) {
        Insets in = getInsets();
        return in.getLeft() + in.getRight() + minColumnWidth;
    }

    @Override
    protected double computePrefHeight(double width) {
        Insets in = getInsets();
        double content = (width < 0 ? computePrefWidth(-1) : width) - in.getLeft() - in.getRight();
        return in.getTop() + in.getBottom() + totalHeight(content);
    }

    @Override
    protected double computeMinHeight(double width) {
        return computePrefHeight(width);
    }

    private double totalHeight(double contentWidth) {
        List<Node> children = getManagedChildren();
        int columns = columnsFor(contentWidth);
        double cardWidth = columnWidth(contentWidth, columns);
        double total = 0;
        for (int start = 0; start < children.size(); start += columns) {
            total += rowHeight(children, start, columns, cardWidth) + (start > 0 ? gap : 0);
        }
        return total;
    }

    private static double rowHeight(List<Node> children, int start, int columns, double cardWidth) {
        double height = 0;
        for (int i = start; i < Math.min(children.size(), start + columns); i++) {
            Node child = children.get(i);
            height = Math.max(height, Math.max(child.minHeight(cardWidth), child.prefHeight(cardWidth)));
        }
        return height;
    }

    @Override
    protected void layoutChildren() {
        Insets in = getInsets();
        double contentWidth = getWidth() - in.getLeft() - in.getRight();
        List<Node> children = getManagedChildren();
        int columns = columnsFor(contentWidth);
        double cardWidth = columnWidth(contentWidth, columns);
        double y = in.getTop();
        for (int start = 0; start < children.size(); start += columns) {
            double height = rowHeight(children, start, columns, cardWidth);
            for (int i = start; i < Math.min(children.size(), start + columns); i++) {
                double x = in.getLeft() + (i - start) * (cardWidth + gap);
                layoutInArea(children.get(i), x, y, cardWidth, height, 0, Insets.EMPTY, true, true, HPos.LEFT, VPos.TOP);
            }
            y += height + gap;
        }
    }
}

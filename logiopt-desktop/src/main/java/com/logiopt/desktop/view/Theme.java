package com.logiopt.desktop.view;

import com.logiopt.desktop.presenter.Models;
import com.logiopt.desktop.support.ChipKind;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.chart.XYChart;
import javafx.util.Duration;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Paint;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * The single source of the desktop client's look: colours, fonts, the 8-px spacing grid and factory
 * methods that return styled nodes. This is the only class allowed to use inline {@code setStyle}
 * strings (for the few looks the Java API cannot express, such as button hover colours); there are no
 * CSS files.
 */
public final class Theme {

    /** Deep navy of the sidebar. */
    public static final Color NAVY = Color.web("#0F1F3D");
    /** Selected sidebar item. */
    public static final Color NAVY_SELECTED = Color.web("#1C3462");
    /** Sidebar text. */
    public static final Color SIDEBAR_TEXT = Color.web("#B9C5DB");
    /** Light grey-blue page background. */
    public static final Color PAGE = Color.web("#EEF2F7");
    /** Cards and top bar. */
    public static final Color SURFACE = Color.WHITE;
    /** Card and input borders. */
    public static final Color BORDER = Color.web("#DCE3EC");
    /** Body text. */
    public static final Color TEXT = Color.web("#1B2433");
    /** Secondary text. */
    public static final Color MUTED = Color.web("#5B6678");
    /** Teal accent of primary buttons and highlights. */
    public static final Color TEAL = Color.web("#0F8B8D");
    /** Teal for hover and pressed states. */
    public static final Color TEAL_DARK = Color.web("#0B6F71");
    /** Error text. */
    public static final Color DANGER = Color.web("#B42318");

    /** Unit of the spacing grid in pixels. */
    public static final double GRID = 8;
    /** System font of the client. */
    public static final String FONT_FAMILY = "Segoe UI";
    /** Width of the sidebar. */
    public static final double SIDEBAR_WIDTH = 220;
    /** Widest the page content column grows; wider windows centre it. */
    public static final double CONTENT_MAX_WIDTH = 1100;
    /** Row height of every table. */
    public static final double ROW_HEIGHT = 44;
    /** Header height of every table. */
    public static final double TABLE_HEADER_HEIGHT = 40;
    /** Rows a table shows before it scrolls. */
    public static final int TABLE_MAX_ROWS = 10;
    /** Height of a table without rows, which shows its empty-state message. */
    public static final double TABLE_EMPTY_HEIGHT = 150;

    private static final double RADIUS = 12;
    private static final Color HEADER_FILL = Color.web("#F5F7FA");
    private static final String ROOT_STYLE = "-fx-font-family: 'Segoe UI'; -fx-font-size: 14px; "
            + "-fx-text-base-color: #1B2433; -fx-accent: #0F8B8D; -fx-focus-color: #0F8B8D; "
            + "-fx-faint-focus-color: #0F8B8D22; -fx-selection-bar: #D5EFEE; -fx-selection-bar-non-focused: #E4F3F2; "
            + "-fx-selection-bar-text: #1B2433;";
    private static final String PRIMARY = "-fx-background-color: #0F8B8D; -fx-text-fill: white; -fx-font-weight: bold; "
            + "-fx-background-radius: 8; -fx-padding: 9 20 9 20; -fx-cursor: hand;";
    private static final String PRIMARY_HOVER = PRIMARY.replace("#0F8B8D", "#0B6F71");
    private static final String SECONDARY = "-fx-background-color: white; -fx-text-fill: #1B2433; -fx-border-color: #C9D2DE; "
            + "-fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 8 18 8 18; -fx-cursor: hand;";
    private static final String SECONDARY_HOVER = SECONDARY.replace("-fx-background-color: white", "-fx-background-color: #F2F5F9");
    private static final String LINK = "-fx-background-color: transparent; -fx-text-fill: #0F8B8D; -fx-font-weight: bold; "
            + "-fx-padding: 2 4 2 4; -fx-cursor: hand;";
    private static final String LINK_HOVER = LINK + " -fx-underline: true;";
    private static final String MENU = "-fx-background-color: transparent; -fx-text-fill: #B9C5DB; -fx-font-size: 14px; "
            + "-fx-background-radius: 8; -fx-padding: 10 14 10 14; -fx-cursor: hand; -fx-border-width: 0 0 0 3; "
            + "-fx-border-color: transparent;";
    private static final String MENU_HOVER = MENU.replace("-fx-background-color: transparent", "-fx-background-color: #162A50")
            .replace("#B9C5DB", "white");
    private static final String MENU_SELECTED = "-fx-background-color: #1C3462; -fx-text-fill: white; -fx-font-size: 14px; "
            + "-fx-font-weight: bold; -fx-background-radius: 0 8 8 0; -fx-padding: 10 14 10 14; -fx-border-width: 0 0 0 3; "
            + "-fx-border-color: #2EC4B6;";
    private static final String INPUT = "-fx-background-radius: 8; -fx-text-box-border: #C9D2DE;";
    private static final String INPUT_PICKER = "-fx-background-radius: 8; -fx-body-color: white; -fx-inner-border: white; "
            + "-fx-outer-border: #C9D2DE; -fx-shadow-highlight-color: transparent; -fx-text-box-border: transparent;";
    private static final String TABLE = "-fx-background-color: transparent; -fx-background-insets: 0; -fx-padding: 0; "
            + "-fx-table-cell-border-color: transparent; -fx-box-border: #DCE3EC; -fx-control-inner-background-alt: white; "
            + "-fx-body-color: #F5F7FA; -fx-inner-border: #F5F7FA; -fx-outer-border: #DCE3EC; "
            + "-fx-focus-color: transparent; -fx-faint-focus-color: transparent; -fx-font-size: 13px;";
    private static final String SCROLL = "-fx-background-color: transparent; -fx-background: #EEF2F7; -fx-padding: 0;";
    private static final String SEGMENT = "-fx-background-color: transparent; -fx-text-fill: #5B6678; -fx-font-weight: bold; "
            + "-fx-background-radius: 8; -fx-padding: 7 16 7 16; -fx-cursor: hand;";
    private static final String SEGMENT_SELECTED = "-fx-background-color: white; -fx-text-fill: #0B6F71; -fx-font-weight: bold; "
            + "-fx-background-radius: 8; -fx-padding: 7 16 7 16; -fx-effect: dropshadow(gaussian, rgba(15,31,61,0.12), 6, 0, 0, 1);";
    private static final String SELECTED_KEY = "logiopt.menu.selected";
    private static final String CHOICE = "-fx-background-color: white; -fx-border-color: #C9D2DE; -fx-border-radius: 10; "
            + "-fx-background-radius: 10; -fx-padding: 10 14 10 14; -fx-cursor: hand;";
    private static final String CHOICE_SELECTED = "-fx-background-color: #F0FAF9; -fx-border-color: #0F8B8D; "
            + "-fx-border-width: 2; -fx-border-radius: 10; -fx-background-radius: 10; -fx-padding: 9 13 9 13;";
    private static final String CHART = "-fx-font-size: 11px; -fx-padding: 4;";
    private static final Color[] ROUTE_COLORS = {Color.web("#0F8B8D"), Color.web("#3B6FD8"), Color.web("#D97706"),
        Color.web("#7C3AED"), Color.web("#DB2777"), Color.web("#4D7C0F")};

    private Theme() {
    }

    /** @return n units of the 8-px grid */
    public static double space(int n) {
        return GRID * n;
    }

    /** @return Segoe UI at the given size */
    public static Font font(double size) {
        return Font.font(FONT_FAMILY, size);
    }

    /** @return Segoe UI at the given weight and size */
    public static Font font(FontWeight weight, double size) {
        return Font.font(FONT_FAMILY, weight, size);
    }

    /**
     * Sets the base font and accent colours on a scene root or dialog pane.
     *
     * @param root root node
     */
    public static void applyRoot(Region root) {
        root.setStyle(ROOT_STYLE);
    }

    /** @return flat background of one colour */
    public static Background background(Paint paint, double radius) {
        return new Background(new BackgroundFill(paint, new CornerRadii(radius), Insets.EMPTY));
    }

    /** @return the light grey-blue page background */
    public static Background pageBackground() {
        return background(PAGE, 0);
    }

    /** @return the navy-to-teal gradient behind the sign-in and registration cards */
    public static Background welcomeBackground() {
        return background(new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, NAVY), new Stop(0.55, Color.web("#163E6B")), new Stop(1, TEAL)), 0);
    }

    /** @return the soft shadow of cards */
    public static DropShadow cardShadow() {
        return new DropShadow(BlurType.GAUSSIAN, Color.rgb(15, 31, 61, 0.10), 18, 0, 0, 4);
    }

    /** @return a white rounded card with a subtle shadow and 24-px padding */
    public static VBox card() {
        VBox card = new VBox(space(2));
        card.setPadding(new Insets(space(3)));
        card.setBackground(background(SURFACE, RADIUS));
        card.setBorder(new Border(new BorderStroke(BORDER, BorderStrokeStyle.SOLID, new CornerRadii(RADIUS),
                new BorderWidths(1))));
        card.setEffect(cardShadow());
        return card;
    }

    /**
     * @param label   what is counted
     * @param value   number
     * @param caption breakdown
     * @return stat card
     */
    public static VBox statCard(String label, String value, String caption) {
        VBox card = card();
        card.setSpacing(6);
        card.setPadding(new Insets(space(2) + 4));
        Label name = text(label.toUpperCase(), 12, FontWeight.BOLD, MUTED);
        name.setWrapText(false);
        Label number = text(value, 30, FontWeight.BOLD, TEXT);
        Label detail = muted(caption);
        HBox accent = new HBox();
        accent.setMinHeight(4);
        accent.setMaxWidth(40);
        accent.setBackground(background(TEAL, 2));
        card.getChildren().addAll(accent, name, number, detail);
        return card;
    }

    /** @return a label with the given font and colour */
    public static Label text(String value, double size, FontWeight weight, Color color) {
        Label label = new Label(value);
        label.setFont(font(weight, size));
        label.setTextFill(color);
        label.setWrapText(true);
        return label;
    }

    /** @return large heading of the sign-in cards */
    public static Label displayHeading(String value) {
        return text(value, 28, FontWeight.BOLD, TEXT);
    }

    /** @return page title in the top bar */
    public static Label pageTitle(String value) {
        Label label = text(value, 22, FontWeight.BOLD, TEXT);
        label.setWrapText(false);
        return label;
    }

    /** @return heading of a card */
    public static Label cardTitle(String value) {
        return text(value, 18, FontWeight.BOLD, TEXT);
    }

    /** @return secondary text */
    public static Label muted(String value) {
        return text(value, 13, FontWeight.NORMAL, MUTED);
    }

    /** @return label above a form field */
    public static Label fieldLabel(String value) {
        return text(value, 13, FontWeight.BOLD, TEXT);
    }

    /** @return an inline field error, hidden until {@link #setMessage(Label, String)} gives it text */
    public static Label fieldError() {
        Label label = text("", 12, FontWeight.NORMAL, DANGER);
        setMessage(label, null);
        return label;
    }

    /** Kinds of message banner. */
    public enum Banner {
        /** Red: something failed. */
        ERROR("#FDECEA", "#F5C2BC", "#8E1B12"),
        /** Blue: information. */
        NOTICE("#E8F1FB", "#C3D9F2", "#1D4E89"),
        /** Green: done. */
        SUCCESS("#E4F5EA", "#BFE3CB", "#1E6B3A");

        private final Color fill;
        private final Color stroke;
        private final Color ink;

        Banner(String fill, String stroke, String ink) {
            this.fill = Color.web(fill);
            this.stroke = Color.web(stroke);
            this.ink = Color.web(ink);
        }
    }

    /** @return a full-width message banner, hidden until {@link #setMessage(Label, String)} gives it text */
    public static Label banner(Banner kind) {
        Label label = text("", 13, FontWeight.NORMAL, kind.ink);
        label.setPadding(new Insets(space(1) + 4, space(2), space(1) + 4, space(2)));
        label.setBackground(background(kind.fill, 8));
        label.setBorder(new Border(new BorderStroke(kind.stroke, BorderStrokeStyle.SOLID, new CornerRadii(8),
                new BorderWidths(1))));
        label.setMaxWidth(Double.MAX_VALUE);
        setMessage(label, null);
        return label;
    }

    /**
     * Shows or hides a field error or banner; a hidden one takes no space.
     *
     * @param label   field error or banner
     * @param message text, or null/blank to hide
     */
    public static void setMessage(Label label, String message) {
        boolean show = message != null && !message.isBlank();
        label.setText(show ? message : "");
        label.setVisible(show);
        label.setManaged(show);
    }

    /** @return the teal primary button */
    public static Button primaryButton(String label) {
        return button(label, PRIMARY, PRIMARY_HOVER);
    }

    /** @return the white outlined button */
    public static Button secondaryButton(String label) {
        return button(label, SECONDARY, SECONDARY_HOVER);
    }

    /** @return a text-only teal button */
    public static Button linkButton(String label) {
        return button(label, LINK, LINK_HOVER);
    }

    private static Button button(String label, String normal, String hover) {
        Button button = new Button(label);
        button.setStyle(normal);
        button.setMnemonicParsing(false);
        button.setOnMouseEntered(e -> button.setStyle(hover));
        button.setOnMouseExited(e -> button.setStyle(normal));
        return button;
    }

    /** @return a sidebar menu item */
    public static Button menuItem(String label) {
        Button item = new Button(label);
        item.setMnemonicParsing(false);
        item.setMaxWidth(Double.MAX_VALUE);
        item.setAlignment(Pos.CENTER_LEFT);
        item.setFocusTraversable(true);
        item.setOnMouseEntered(e -> {
            if (!isSelected(item)) {
                item.setStyle(MENU_HOVER);
            }
        });
        item.setOnMouseExited(e -> item.setStyle(isSelected(item) ? MENU_SELECTED : MENU));
        setMenuSelected(item, false);
        return item;
    }

    /** Marks a sidebar item as the current page (or not). */
    public static void setMenuSelected(Button item, boolean selected) {
        item.getProperties().put(SELECTED_KEY, selected);
        item.setStyle(selected ? MENU_SELECTED : MENU);
    }

    private static boolean isSelected(Button item) {
        return Boolean.TRUE.equals(item.getProperties().get(SELECTED_KEY));
    }

    /**
     * @param value text
     * @param kind  colour family
     * @return a rounded status chip
     */
    public static Label chip(String value, ChipKind kind) {
        Color fill;
        Color ink;
        switch (kind) {
            case INFO:
                fill = Color.web("#DCE8FB");
                ink = Color.web("#1D4E9E");
                break;
            case SUCCESS:
                fill = Color.web("#DDF3E4");
                ink = Color.web("#1E6B3A");
                break;
            case WARNING:
                fill = Color.web("#FFF1D6");
                ink = Color.web("#8A5A00");
                break;
            case DANGER:
                fill = Color.web("#FBE1DF");
                ink = Color.web("#9B2C22");
                break;
            default:
                fill = Color.web("#E4E7EC");
                ink = Color.web("#3F4856");
        }
        Label chip = text(value, 12, FontWeight.BOLD, ink);
        chip.setWrapText(false);
        chip.setPadding(new Insets(3, 10, 3, 10));
        chip.setBackground(background(fill, 999));
        chip.setMinWidth(Region.USE_PREF_SIZE);
        return chip;
    }

    /**
     * @param value text
     * @param kind  colour family
     * @param hint  plain-language meaning, shown as a tooltip (null for none)
     * @return a rounded status chip with a tooltip
     */
    public static Label chip(String value, ChipKind kind, String hint) {
        Label chip = chip(value, kind);
        if (hint != null && !hint.isBlank()) {
            Tooltip tip = new Tooltip(hint);
            tip.setWrapText(true);
            tip.setMaxWidth(320);
            tip.setShowDelay(Duration.millis(250));
            chip.setTooltip(tip);
        }
        return chip;
    }

    /** @return a small outlined badge for a transport mode (ROAD, RAIL, MULTIMODAL) */
    public static Label modeBadge(String mode) {
        Label badge = text(mode, 11, FontWeight.BOLD, Color.web("#334155"));
        badge.setWrapText(false);
        badge.setPadding(new Insets(1, 8, 1, 8));
        badge.setBackground(background(Color.web("#F1F5F9"), 6));
        badge.setBorder(new Border(new BorderStroke(Color.web("#CBD5E1"), BorderStrokeStyle.SOLID, new CornerRadii(6),
                new BorderWidths(1))));
        badge.setMinWidth(Region.USE_PREF_SIZE);
        return badge;
    }

    /**
     * A table value; the best value of its column is bold and carries a "★" mark (not colour alone).
     *
     * @param value text
     * @param best  true for the best value of the column
     * @param why   tooltip of the mark ("Lowest total cost")
     * @return the value node
     */
    public static Label bestValue(String value, boolean best, String why) {
        Label label = text(best ? "\u2605 " + value : value, 13, best ? FontWeight.BOLD : FontWeight.NORMAL,
                value.equals("not available") ? MUTED : TEXT);
        if (best) {
            label.setTooltip(new Tooltip(why));
        }
        return label;
    }

    /** @return the teal "Your choice" tag of the preferred strategy's winner */
    public static Label yourChoice() {
        Label tag = text("Your choice", 11, FontWeight.BOLD, Color.WHITE);
        tag.setWrapText(false);
        tag.setPadding(new Insets(2, 8, 2, 8));
        tag.setBackground(background(TEAL, 999));
        tag.setMinWidth(Region.USE_PREF_SIZE);
        return tag;
    }

    /** Gives a card the highlighted look of the recommendation (teal outline, light teal fill). */
    public static void highlightCard(Region card) {
        card.setBackground(background(Color.web("#F0FAF9"), RADIUS));
        card.setBorder(new Border(new BorderStroke(TEAL, BorderStrokeStyle.SOLID, new CornerRadii(RADIUS),
                new BorderWidths(2))));
    }

    /** Gives a card the muted look of the excluded-routes section. */
    public static void mutedCard(Region card) {
        card.setBackground(background(Color.web("#F8FAFC"), RADIUS));
        card.setEffect(null);
    }

    /** @return a selectable tile of the preference choice (title and description) */
    public static ToggleButton choiceTile(String title, String description) {
        Label head = text(title, 14, FontWeight.BOLD, TEXT);
        Label body = text(description, 12, FontWeight.NORMAL, MUTED);
        VBox box = new VBox(2, head, body);
        ToggleButton tile = new ToggleButton();
        tile.setGraphic(box);
        tile.setMaxWidth(Double.MAX_VALUE);
        tile.setAlignment(Pos.CENTER_LEFT);
        tile.setMnemonicParsing(false);
        tile.setStyle(CHOICE);
        tile.selectedProperty().addListener((o, was, now) -> tile.setStyle(now ? CHOICE_SELECTED : CHOICE));
        return tile;
    }

    /** @return the colour of a route in every chart (by its position in the comparison) */
    public static Color routeColor(int index) {
        return ROUTE_COLORS[Math.floorMod(index, ROUTE_COLORS.length)];
    }

    /** Styles a comparison chart: no legend, light plot, no animation. */
    public static void styleChart(XYChart<?, ?> chart) {
        chart.setLegendVisible(false);
        chart.setAnimated(false);
        chart.setStyle(CHART);
        chart.setVerticalGridLinesVisible(false);
        chart.setMinHeight(240);
        chart.setPrefHeight(260);
    }

    /** @return the pill-shaped unread badge of the top bar */
    public static Label badge(String value, boolean highlighted) {
        Label badge = chip(value, highlighted ? ChipKind.INFO : ChipKind.NEUTRAL);
        badge.setPadding(new Insets(5, 12, 5, 12));
        return badge;
    }

    /** Rounds the corners of text fields, combo boxes and date pickers and gives them a comfortable height. */
    public static void styleInput(Control input) {
        input.setStyle(input instanceof ComboBoxBase ? INPUT_PICKER : INPUT);
        input.setPrefHeight(38);
        input.setMaxWidth(Double.MAX_VALUE);
    }

    /**
     * Gives a table roomy rows, light separators, flat left-aligned headers and a stretching layout.
     * The header labels exist only once the table has a skin, so they are styled after each layout.
     */
    public static void styleTable(TableView<?> table) {
        table.setStyle(TABLE);
        table.setFixedCellSize(ROW_HEIGHT);
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        table.setMaxWidth(Double.MAX_VALUE);
        table.needsLayoutProperty().addListener((obs, was, needs) -> {
            if (!needs) {
                styleHeaders(table);
            }
        });
    }

    private static void styleHeaders(TableView<?> table) {
        Background flat = background(HEADER_FILL, 0);
        Border underline = new Border(new BorderStroke(Color.TRANSPARENT, Color.TRANSPARENT, BORDER, Color.TRANSPARENT,
                BorderStrokeStyle.NONE, BorderStrokeStyle.NONE, BorderStrokeStyle.SOLID, BorderStrokeStyle.NONE,
                CornerRadii.EMPTY, new BorderWidths(0, 0, 1, 0), Insets.EMPTY));
        for (String selector : new String[] {".column-header-background", ".column-header", ".filler"}) {
            for (Node node : table.lookupAll(selector)) {
                if (node instanceof Region) {
                    Region region = (Region) node;
                    region.setBackground(flat);
                    region.setBorder(selector.equals(".column-header-background") ? underline : Border.EMPTY);
                }
            }
        }
        for (Node node : table.lookupAll(".column-header")) {
            if (node instanceof Region) {
                Region header = (Region) node;
                header.setMinHeight(TABLE_HEADER_HEIGHT);
                header.setPrefHeight(TABLE_HEADER_HEIGHT);
                header.setMaxHeight(TABLE_HEADER_HEIGHT);
            }
        }
        for (Node node : table.lookupAll(".column-header .label")) {
            if (node instanceof Label) {
                Label label = (Label) node;
                label.setAlignment(Pos.CENTER_LEFT);
                label.setPadding(cellPadding());
                label.setFont(font(FontWeight.BOLD, 12));
                label.setTextFill(MUTED);
            }
        }
    }

    /** @return the thin line under each table row (rows have no vertical grid lines) */
    public static Border rowSeparator() {
        return new Border(new BorderStroke(Color.TRANSPARENT, Color.TRANSPARENT, Color.web("#EEF1F5"), Color.TRANSPARENT,
                BorderStrokeStyle.NONE, BorderStrokeStyle.NONE, BorderStrokeStyle.SOLID, BorderStrokeStyle.NONE,
                CornerRadii.EMPTY, new BorderWidths(0, 0, 1, 0), Insets.EMPTY));
    }

    /** @return padding of table cells, matching the header labels */
    public static Insets cellPadding() {
        return new Insets(0, 10, 0, 10);
    }

    /** @return a tab-like button of a segmented control */
    public static Button segment(String label) {
        Button button = new Button(label);
        button.setMnemonicParsing(false);
        setSegmentSelected(button, false);
        return button;
    }

    /** Marks a segment as selected (or not). */
    public static void setSegmentSelected(Button segment, boolean selected) {
        segment.setStyle(selected ? SEGMENT_SELECTED : SEGMENT);
    }

    /** @return the grey track that holds the segments */
    public static HBox segmentTrack() {
        HBox track = new HBox(4);
        track.setPadding(new Insets(4));
        track.setBackground(background(Color.web("#E9EDF3"), 10));
        track.setMaxWidth(Region.USE_PREF_SIZE);
        return track;
    }

    /** Styles a dialog button as the primary action. */
    public static void makePrimary(Button button) {
        button.setStyle(PRIMARY);
        button.setOnMouseEntered(e -> button.setStyle(PRIMARY_HOVER));
        button.setOnMouseExited(e -> button.setStyle(PRIMARY));
    }

    /** Styles a dialog button as a secondary action. */
    public static void makeSecondary(Button button) {
        button.setStyle(SECONDARY);
        button.setOnMouseEntered(e -> button.setStyle(SECONDARY_HOVER));
        button.setOnMouseExited(e -> button.setStyle(SECONDARY));
    }

    /** Makes a scroll pane blend into the page. */
    public static void styleScroll(ScrollPane scroll) {
        scroll.setStyle(SCROLL);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    }

    /** @return an empty region that takes the free space in a row */
    public static Region spacer() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    /** @return a thin horizontal line */
    public static Node divider() {
        Region line = new Region();
        line.setMinHeight(1);
        line.setMaxHeight(1);
        line.setBackground(background(BORDER, 0));
        return line;
    }
    /** Diameter of a timeline marker. */
    public static final double MARKER = 18;

    /**
     * @param state where the milestone stands
     * @return the round timeline marker: filled teal when done, a ringed teal dot when current, an
     *         amber ring when next, a grey ring later
     */
    public static Node timelineMarker(Models.StepState state) {
        Region ring = new Region();
        ring.setMinSize(MARKER, MARKER);
        ring.setMaxSize(MARKER, MARKER);
        Color fill;
        Color stroke;
        double width = 2;
        switch (state) {
            case DONE:
                fill = TEAL;
                stroke = TEAL;
                break;
            case CURRENT:
                fill = TEAL;
                stroke = Color.web("#9ED8D6");
                width = 4;
                break;
            case NEXT:
                fill = SURFACE;
                stroke = Color.web("#D99A1E");
                break;
            default:
                fill = SURFACE;
                stroke = Color.web("#C3CBD6");
        }
        ring.setBackground(background(fill, MARKER));
        ring.setBorder(new Border(new BorderStroke(stroke, BorderStrokeStyle.SOLID, new CornerRadii(MARKER),
                new BorderWidths(width))));
        return ring;
    }

    /**
     * @param done true if the line leads to a recorded milestone
     * @return the vertical line joining two timeline markers
     */
    public static Region timelineConnector(boolean done) {
        Region line = new Region();
        line.setMinWidth(2);
        line.setMaxWidth(2);
        line.setMinHeight(14);
        line.setBackground(background(done ? TEAL : Color.web("#D5DCE5"), 0));
        return line;
    }

    /** Gives the current timeline step its highlighted look. */
    public static void currentStep(Region step) {
        step.setBackground(background(Color.web("#F0FAF9"), 8));
        step.setBorder(new Border(new BorderStroke(Color.web("#9ED8D6"), BorderStrokeStyle.SOLID, new CornerRadii(8),
                new BorderWidths(1))));
    }

    /** @return the small blue dot of an unread notification */
    public static Node unreadDot() {
        Region dot = new Region();
        dot.setMinSize(10, 10);
        dot.setMaxSize(10, 10);
        dot.setBackground(background(Color.web("#2F6FD6"), 10));
        return dot;
    }

    /** @return a space holding the place of an unread dot, so rows line up */
    public static Node readDot() {
        Region dot = new Region();
        dot.setMinSize(10, 10);
        dot.setMaxSize(10, 10);
        return dot;
    }

    /** @return a multi-line text area for a short description */
    public static javafx.scene.control.TextArea textArea(String prompt) {
        javafx.scene.control.TextArea area = new javafx.scene.control.TextArea();
        area.setPromptText(prompt);
        area.setWrapText(true);
        area.setPrefRowCount(2);
        area.setFont(font(13));
        area.setStyle(INPUT);
        area.setMaxWidth(Double.MAX_VALUE);
        return area;
    }
}

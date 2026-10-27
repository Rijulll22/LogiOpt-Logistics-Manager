package com.logiopt.desktop.view;

import com.logiopt.desktop.presenter.ShellPresenter;
import com.logiopt.desktop.support.Screen;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.FontWeight;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The main window frame for a signed-in user: navy sidebar with the text menu, top bar with the page
 * title, the user, the unread badge and Sign out, and the page area on the light page background.
 */
public final class ShellView implements ShellPresenter.View {

    private final BorderPane root = new BorderPane();
    private final Label title = Theme.pageTitle("");
    private final Label userName = Theme.text("", 14, FontWeight.BOLD, Theme.TEXT);
    private final Label userEmail = Theme.muted("");
    private final HBox badgeHolder = new HBox();
    private final Button signOut = Theme.secondaryButton("Sign out");
    private final Map<Screen, Button> menu = new EnumMap<>(Screen.class);
    private final StackPane page = new StackPane();

    /** Widest the user's name and email grow in the top bar before they are shortened with "…". */
    static final double USER_MAX_WIDTH = 240;

    /** Builds the frame with the customer's menu. */
    public ShellView() {
        this(false);
    }

    /** @param admin true for the administrator's menu */
    public ShellView(boolean admin) {
        Theme.applyRoot(root);
        root.setBackground(Theme.pageBackground());
        root.setLeft(sidebar(admin));
        VBox.setVgrow(page, Priority.ALWAYS);
        root.setCenter(new VBox(topBar(), page));
    }

    private VBox sidebar(boolean admin) {
        VBox bar = new VBox(Theme.space(1) / 2);
        bar.setPrefWidth(Theme.SIDEBAR_WIDTH);
        bar.setMinWidth(Theme.SIDEBAR_WIDTH);
        bar.setPadding(new Insets(Theme.space(3), Theme.space(2), Theme.space(2), Theme.space(2)));
        bar.setBackground(Theme.background(Theme.NAVY, 0));
        Label brand = Theme.text("LogiOpt", 24, FontWeight.BOLD, Color.WHITE);
        brand.setPadding(new Insets(0, 0, 0, Theme.space(1) + 6));
        Label tagline = Theme.text("Smart shipment planning", 12, FontWeight.NORMAL, Theme.SIDEBAR_TEXT);
        tagline.setPadding(new Insets(0, 0, Theme.space(4), Theme.space(1) + 6));
        bar.getChildren().addAll(brand, tagline);
        Label section = Theme.text("MENU", 11, FontWeight.BOLD, Color.web("#7F8DA8"));
        section.setPadding(new Insets(0, 0, Theme.space(1), Theme.space(1) + 6));
        bar.getChildren().add(section);
        for (Screen screen : Screen.menu(admin)) {
            Button item = Theme.menuItem(screen.getTitle());
            item.setId("menu-" + screen.name());
            menu.put(screen, item);
            bar.getChildren().add(item);
        }
        VBox filler = new VBox();
        VBox.setVgrow(filler, Priority.ALWAYS);
        Label footer = Theme.text("Simulated payments", 11, FontWeight.NORMAL,
                Color.web("#7F8DA8"));
        footer.setPadding(new Insets(0, 0, 0, Theme.space(1) + 6));
        bar.getChildren().addAll(filler, footer);
        return bar;
    }

    private HBox topBar() {
        HBox bar = new HBox(Theme.space(2));
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setMinHeight(72);
        bar.setPadding(new Insets(Theme.space(2), Theme.space(3), Theme.space(2), Theme.space(3)));
        bar.setBackground(Theme.background(Theme.SURFACE, 0));
        bar.setBorder(new Border(new BorderStroke(Theme.BORDER, BorderStrokeStyle.SOLID, CornerRadii.EMPTY,
                new BorderWidths(0, 0, 1, 0))));
        VBox user = new VBox(2, userName, userEmail);
        user.setAlignment(Pos.CENTER_RIGHT);
        user.setMinWidth(0);
        userName.setWrapText(false);
        userEmail.setWrapText(false);
        userName.setMaxWidth(USER_MAX_WIDTH);
        userEmail.setMaxWidth(USER_MAX_WIDTH);
        title.setMinWidth(Region.USE_PREF_SIZE);
        title.setId("pageTitle");
        userName.setId("userName");
        userEmail.setId("userEmail");
        badgeHolder.setAlignment(Pos.CENTER);
        badgeHolder.setMinWidth(Region.USE_PREF_SIZE);
        signOut.setMinWidth(Region.USE_PREF_SIZE);
        signOut.setId("signOut");
        bar.getChildren().addAll(title, Theme.spacer(), badgeHolder, user, signOut);
        return bar;
    }

    /** @return the window content */
    public BorderPane root() {
        return root;
    }

    /** @param value page title */
    public void setTitle(String value) {
        title.setText(value);
    }

    /** Marks the sidebar item of the page shown (null for none). */
    public void selectMenu(Screen screen) {
        for (Map.Entry<Screen, Button> e : menu.entrySet()) {
            Theme.setMenuSelected(e.getValue(), e.getKey() == screen);
        }
    }

    /**
     * Shows a page in a centred column at most {@link Theme#CONTENT_MAX_WIDTH} wide. The page scrolls
     * vertically inside the window when it is taller; it never scrolls horizontally.
     *
     * @param content page content
     */
    public void setContent(Node content) {
        VBox column = new VBox(content);
        column.setId("contentColumn");
        column.setMaxWidth(Theme.CONTENT_MAX_WIDTH);
        column.setFillWidth(true);
        StackPane centred = new StackPane(column);
        StackPane.setAlignment(column, Pos.TOP_CENTER);
        centred.setPadding(new Insets(Theme.space(3), Theme.space(4), Theme.space(4), Theme.space(4)));
        ScrollPane scroll = new ScrollPane(centred);
        scroll.setId("pageScroll");
        Theme.styleScroll(scroll);
        page.getChildren().setAll(scroll);
    }

    /** @param action receives the menu entry chosen */
    public void onMenu(Consumer<Screen> action) {
        for (Map.Entry<Screen, Button> e : menu.entrySet()) {
            e.getValue().setOnAction(ev -> action.accept(e.getKey()));
        }
    }

    /** @param action sign-out action */
    public void onSignOut(Runnable action) {
        signOut.setOnAction(e -> action.run());
    }

    @Override
    public void showUser(String name, String email) {
        userName.setText(name);
        userEmail.setText(email);
    }

    @Override
    public void showUnread(String text) {
        if (text == null) {
            badgeHolder.getChildren().clear();
            return;
        }
        Label badge = Theme.badge(text, !text.startsWith("No "));
        badge.setId("unreadBadge");
        badgeHolder.getChildren().setAll(badge);
    }
}

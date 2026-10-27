package com.logiopt.desktop.tools;

import com.logiopt.app.SchemaStatus;
import com.logiopt.desktop.presenter.AccountPresenter;
import com.logiopt.desktop.presenter.AdminTrackingPresenter;
import com.logiopt.desktop.presenter.BookingPresenter;
import com.logiopt.desktop.presenter.DashboardPresenter;
import com.logiopt.desktop.presenter.LoginPresenter;
import com.logiopt.desktop.presenter.Models.AccountModel;
import com.logiopt.desktop.presenter.Models.BookingDetailModel;
import com.logiopt.desktop.presenter.Models.BookingRow;
import com.logiopt.desktop.presenter.Models.ComparisonModel;
import com.logiopt.desktop.presenter.Models.DashboardModel;
import com.logiopt.desktop.presenter.Models.NotificationsModel;
import com.logiopt.desktop.presenter.Models.ReportProgress;
import com.logiopt.desktop.presenter.Models.ReportsModel;
import com.logiopt.desktop.presenter.Models.ShipmentFormModel;
import com.logiopt.desktop.presenter.Models.ShipmentRow;
import com.logiopt.desktop.presenter.RegisterPresenter;
import com.logiopt.desktop.support.Navigator;
import com.logiopt.desktop.support.Screen;
import com.logiopt.desktop.support.WindowSizing;
import com.logiopt.desktop.view.AccountView;
import com.logiopt.desktop.view.AdminTrackingView;
import com.logiopt.desktop.view.AdminUsersView;
import com.logiopt.desktop.view.BookingDetailView;
import com.logiopt.desktop.view.BookingListView;
import com.logiopt.desktop.view.ComparisonView;
import com.logiopt.desktop.view.DashboardView;
import com.logiopt.desktop.view.Dialogs;
import com.logiopt.desktop.view.HistoryView;
import com.logiopt.desktop.view.LoginView;
import com.logiopt.desktop.view.NotificationsView;
import com.logiopt.desktop.view.OrderSummaryView;
import com.logiopt.desktop.view.PaymentView;
import com.logiopt.desktop.view.RegisterView;
import com.logiopt.desktop.view.ReportsView;
import com.logiopt.desktop.view.ShellView;
import com.logiopt.desktop.view.ShipmentFormView;
import com.logiopt.desktop.view.ShipmentListView;
import com.logiopt.desktop.view.Theme;
import com.logiopt.model.OptimisationPreference;
import com.logiopt.report.ReportStatus;
import com.logiopt.service.AuthService;
import javafx.geometry.Insets;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.DialogPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Every desktop screen built so far, filled with {@link SampleData} exactly as the presenters would
 * fill it, at the window sizes {@link WindowSizing} chooses for typical screens. Used by the
 * {@link ScreenshotTool} and by the view smoke tests. Must be called on the JavaFX thread.
 */
public final class ScreenGallery {

    /** Height of the Windows taskbar assumed for the typical screens, in logical pixels. */
    static final double TASKBAR = 48;
    /** Height of the tall window used for the full-page review shots. */
    static final double FULL_PAGE_HEIGHT = 2600;
    /** Height of the window title bar, which the scene does not include, in logical pixels. */
    static final double TITLE_BAR = 32;

    /**
     * One screen to render.
     *
     * @param name   file name without extension; "size/screen"
     * @param width  scene width
     * @param height scene height
     * @param build  builds the root node (on the JavaFX thread)
     */
    public record Shot(String name, double width, double height, Supplier<Parent> build) {
    }

    /**
     * A window size to render at.
     *
     * @param name   folder name
     * @param width  scene width (the window's client area)
     * @param height scene height
     */
    public record Size(String name, double width, double height) {
    }

    private record Page(String name, Supplier<Parent> build) {
    }

    private ScreenGallery() {
    }

    /**
     * @return the client area of the window WindowSizing places on a screen of the given logical
     *         size with a taskbar at the bottom
     */
    static Size forScreen(String name, double screenWidth, double screenHeight) {
        WindowSizing.Placement p = WindowSizing.place(new WindowSizing.Bounds(0, 0, screenWidth, screenHeight - TASKBAR));
        return new Size(name, p.width(), p.height() - TITLE_BAR);
    }

    /**
     * @return the typical screens (logical size), this machine's primary screen, and the minimum
     *         window
     */
    public static List<Size> sizes() {
        List<Size> sizes = new ArrayList<>();
        sizes.add(forScreen("screen-1366x768", 1366, 768));
        sizes.add(forScreen("screen-1536x864", 1536, 864));
        sizes.add(forScreen("screen-1920x1080", 1920, 1080));
        // javafx.stage.Screen is written out because com.logiopt.desktop.support.Screen is imported.
        Rectangle2D here = javafx.stage.Screen.getPrimary().getVisualBounds();
        WindowSizing.Placement p = WindowSizing.place(new WindowSizing.Bounds(here.getMinX(), here.getMinY(),
                here.getWidth(), here.getHeight()));
        sizes.add(new Size("this-screen-" + (int) here.getWidth() + "x" + (int) here.getHeight(), p.width(),
                p.height() - TITLE_BAR));
        sizes.add(new Size("minimum-window", WindowSizing.MIN_WIDTH, WindowSizing.MIN_HEIGHT - TITLE_BAR));
        return sizes;
    }

    /** @return the two sizes of the phase D4 shots: this laptop (1280x720 at 150 %, 1280x672 usable) and the minimum window */
    public static List<Size> d4Sizes() {
        List<Size> all = sizes();
        return List.of(forScreen("laptop-1280x672", 1280, 720), all.get(all.size() - 1));
    }

    private static List<Page> d4Pages() {
        return List.of(
                new Page("d4-01-timeline-booked", () -> booking(SampleData.confirmedBooking(SampleData.timeline(1, false)),
                        false)),
                new Page("d4-02-timeline-in-transit", () -> booking(SampleData.confirmedBooking(
                        SampleData.timeline(3, false)), false)),
                new Page("d4-03-timeline-at-transfer", () -> booking(SampleData.confirmedBooking(
                        SampleData.timeline(4, true)), false)),
                new Page("d4-04-timeline-delivered", () -> booking(SampleData.confirmedBooking(
                        SampleData.timeline(5, false)), false)),
                new Page("d4-05-timeline-unpaid", () -> booking(SampleData.bookingDetail(), false)),
                new Page("d4-06-notifications-unread", () -> notifications(SampleData.notifications(false), "2 unread")),
                new Page("d4-07-notifications-empty", () -> notifications(SampleData.notifications(true), "No unread")),
                new Page("d4-08-reports-running", () -> reports(SampleData.reports(false),
                        SampleData.progress(ReportStatus.RUNNING), true)),
                new Page("d4-09-reports-done", () -> reports(SampleData.reports(false),
                        SampleData.progress(ReportStatus.DONE), false)),
                new Page("d4-10-reports-failed", () -> reports(SampleData.reports(false),
                        SampleData.progress(ReportStatus.FAILED), false)),
                new Page("d4-11-reports-empty", () -> reports(SampleData.reports(true), null, false)),
                new Page("d4-12-admin-dashboard", () -> dashboard(SampleData.adminDashboard(), true, null)),
                new Page("d4-13-admin-users", ScreenGallery::adminUsers),
                new Page("d4-14-admin-bookings", ScreenGallery::adminBookings),
                new Page("d4-15-admin-booking-read-only", () -> booking(SampleData.confirmedBooking(
                        SampleData.timeline(3, false)), true)),
                new Page("d4-16-admin-tracking", () -> adminTracking(false)),
                new Page("d4-17-admin-tracking-stale", () -> adminTracking(true)),
                new Page("d4-18-admin-tracking-empty", ScreenGallery::adminTrackingEmpty));
    }

    /** @return the phase D4 screens at {@link #d4Sizes()}, the booking pages also in a tall window */
    public static List<Shot> d4Shots() {
        List<Shot> shots = new ArrayList<>();
        for (Size size : d4Sizes()) {
            for (Page page : d4Pages()) {
                shots.add(new Shot(size.name() + "/" + page.name(), size.width(), size.height(), page.build()));
                if (page.name().contains("timeline") || page.name().contains("booking-read-only")) {
                    shots.add(new Shot(size.name() + "/full-page-" + page.name(), size.width(), FULL_PAGE_HEIGHT,
                            page.build()));
                }
            }
        }
        return shots;
    }

    private static List<Page> pages() {
        return List.of(
                new Page("01-login", () -> login(null, null)),
                new Page("02-login-signed-out-error", () -> login(Navigator.SIGNED_OUT, LoginPresenter.INVALID_MESSAGE)),
                new Page("03-register-errors", ScreenGallery::registerWithErrors),
                new Page("04-dashboard-customer", () -> dashboard(SampleData.customerDashboard(), false, "3 unread")),
                new Page("05-dashboard-new-customer", () -> dashboard(SampleData.emptyDashboard(), false, "No unread")),
                new Page("06-dashboard-admin", () -> dashboard(SampleData.adminDashboard(), true, null)),
                new Page("07-dashboard-error", ScreenGallery::dashboardError),
                new Page("08-shipment-new", () -> form(SampleData.newForm(), Map.of(), Screen.NEW_SHIPMENT)),
                new Page("09-shipment-new-errors", () -> form(SampleData.invalidForm(), SampleData.formErrors(),
                        Screen.NEW_SHIPMENT)),
                new Page("10-shipment-edit", () -> form(SampleData.editForm(), Map.of(), Screen.EDIT_SHIPMENT)),
                new Page("11-shipments", () -> shipments(SampleData.shipments(), "Shipment #21 saved.")),
                new Page("12-shipments-empty", () -> shipments(List.of(), null)),
                new Page("13-history", ScreenGallery::history),
                new Page("14-account", () -> account(SampleData.account(), false, false)),
                new Page("15-account-errors", () -> account(SampleData.account(), false, true)),
                new Page("16-account-admin", () -> account(SampleData.adminAccount(), true, false)),
                new Page("17-comparison-balanced", () -> comparison(SampleData.comparison(
                        OptimisationPreference.BALANCED, SampleData.Variant.STANDARD))),
                new Page("18-comparison-greenest-choice", () -> comparison(SampleData.comparison(
                        OptimisationPreference.GREENEST, SampleData.Variant.STANDARD))),
                new Page("19-comparison-excluded-route", () -> comparison(SampleData.comparison(
                        OptimisationPreference.CHEAPEST, SampleData.Variant.EXCLUDED_ROUTE))),
                new Page("20-comparison-missing-metric", () -> comparison(SampleData.comparison(
                        OptimisationPreference.BALANCED, SampleData.Variant.MISSING_METRIC))),
                new Page("21-comparison-past-dated", () -> comparison(SampleData.comparison(
                        OptimisationPreference.FASTEST, SampleData.Variant.PAST_DATED))),
                new Page("22-comparison-greenest-cannot-rank", () -> comparison(SampleData.comparison(
                        OptimisationPreference.GREENEST, SampleData.Variant.NO_GREENEST))),
                new Page("23-booking-detail", ScreenGallery::bookingDetail),
                new Page("24-my-bookings", () -> bookings(SampleData.bookings())),
                new Page("25-my-bookings-empty", () -> bookings(List.of())));
    }

    /** @return every page at every size, then the two start-up dialogs */
    public static List<Shot> shots() {
        List<Shot> shots = new ArrayList<>();
        for (Size size : sizes()) {
            for (Page page : pages()) {
                shots.add(new Shot(size.name() + "/" + page.name(), size.width(), size.height(), page.build()));
            }
        }
        // The long pages once more in a tall window, so the whole page (table, charts, excluded routes) can be reviewed.
        List<Size> all = sizes();
        for (Size size : List.of(all.get(3), all.get(all.size() - 1))) {
            for (Page page : pages()) {
                if (page.name().contains("comparison") || page.name().contains("booking-detail")
                        || page.name().contains("shipment-new")) {
                    shots.add(new Shot("full-page-" + size.name() + "/" + page.name(), size.width(), FULL_PAGE_HEIGHT,
                            page.build()));
                }
            }
        }
        shots.add(new Shot("dialogs/startup-schema-behind", 640, 400,
                () -> schema(new SchemaStatus(SchemaStatus.State.BEHIND, 9, 10))));
        shots.add(new Shot("dialogs/startup-database-unreachable", 640, 400,
                () -> schema(new SchemaStatus(SchemaStatus.State.UNREACHABLE, null, 10))));
        shots.add(new Shot("dialogs/order-summary", 640, 680, () -> new OrderSummaryView(SampleData.orderSummary()).root()));
        shots.add(new Shot("dialogs/order-summary-small-window", 640, 520,
                () -> new OrderSummaryView(SampleData.orderSummary()).root()));
        shots.add(new Shot("dialogs/payment", 520, 470, () -> new PaymentView(12, "INR 22,800.00").root()));
        shots.add(new Shot("dialogs/payment-failed", 520, 470, () -> {
            PaymentView view = new PaymentView(12, "INR 22,800.00");
            view.showResult(false, BookingPresenter.FAILED);
            return view.root();
        }));
        shots.add(new Shot("dialogs/payment-succeeded", 520, 470, () -> {
            PaymentView view = new PaymentView(12, "INR 22,800.00");
            view.showResult(true, BookingPresenter.PAID);
            return view.root();
        }));
        return shots;
    }

    /** @return the sign-in card with an optional notice and error */
    public static Parent login(String notice, String error) {
        LoginView view = new LoginView();
        view.showNotice(notice);
        view.showError(error);
        if (error != null) {
            view.setEmail(SampleData.EMAIL);
        }
        return view.root();
    }

    /** @return the registration card with field errors */
    public static Parent registerWithErrors() {
        RegisterView view = new RegisterView();
        view.showErrors(Map.of(RegisterPresenter.EMAIL, List.of("Enter a valid email address."),
                RegisterPresenter.PASSWORD, List.of("Password must be at least 8 characters.")));
        return view.root();
    }

    /** @return the shell around a page */
    public static ShellView shell(Screen screen, String title, boolean admin, String unread) {
        ShellView shell = new ShellView(admin);
        shell.setTitle(title);
        shell.selectMenu(screen == Screen.EDIT_SHIPMENT || screen == Screen.COMPARISON ? Screen.SHIPMENTS
                : screen == Screen.BOOKING ? (admin ? Screen.ADMIN_BOOKINGS : Screen.BOOKINGS) : screen);
        shell.showUser(admin ? "LogiOpt Administrator" : SampleData.NAME, admin ? AuthService.ADMIN_EMAIL : SampleData.EMAIL);
        shell.showUnread(unread);
        return shell;
    }

    /** @return the dashboard page in the shell */
    public static Parent dashboard(DashboardModel model, boolean admin, String unread) {
        DashboardView view = new DashboardView();
        view.showDashboard(model);
        ShellView shell = shell(Screen.DASHBOARD, Screen.DASHBOARD.getTitle(), admin, unread);
        shell.setContent(view.root());
        return shell.root();
    }

    /** @return the dashboard error state in the shell */
    public static Parent dashboardError() {
        DashboardView view = new DashboardView();
        view.showError(DashboardPresenter.UNAVAILABLE);
        ShellView shell = shell(Screen.DASHBOARD, Screen.DASHBOARD.getTitle(), false, null);
        shell.setContent(view.root());
        return shell.root();
    }

    /** @return the shipment form in the shell */
    public static Parent form(ShipmentFormModel model, Map<String, List<String>> errors, Screen screen) {
        ShipmentFormView view = new ShipmentFormView();
        view.showForm(model);
        view.showFieldErrors(errors);
        String title = screen == Screen.EDIT_SHIPMENT ? model.heading() : screen.getTitle();
        ShellView shell = shell(screen, title, false, "3 unread");
        shell.setContent(view.root());
        return shell.root();
    }

    /** @return the shipments page in the shell */
    public static Parent shipments(List<ShipmentRow> rows, String notice) {
        ShipmentListView view = new ShipmentListView();
        view.showShipments(rows);
        view.showNotice(notice);
        ShellView shell = shell(Screen.SHIPMENTS, Screen.SHIPMENTS.getTitle(), false, "3 unread");
        shell.setContent(view.root());
        return shell.root();
    }

    /** @return the history page in the shell */
    public static Parent history() {
        HistoryView view = new HistoryView();
        view.showHistory(SampleData.history());
        ShellView shell = shell(Screen.HISTORY, Screen.HISTORY.getTitle(), false, "3 unread");
        shell.setContent(view.root());
        return shell.root();
    }

    /** @return the account page in the shell, optionally with sample errors */
    public static Parent account(AccountModel model, boolean admin, boolean withErrors) {
        AccountView view = new AccountView();
        view.showAccount(model);
        if (withErrors) {
            view.showNameErrors(List.of("Name is required."));
            view.showPasswordErrors(Map.of(AccountPresenter.CURRENT, List.of(AuthService.WRONG_CURRENT_PASSWORD),
                    AccountPresenter.NEW, List.of("Password must be at least 8 characters.")));
        }
        ShellView shell = shell(Screen.ACCOUNT, Screen.ACCOUNT.getTitle(), admin, admin ? null : "3 unread");
        shell.setContent(view.root());
        return shell.root();
    }

    /** @return the comparison page in the shell */
    public static Parent comparison(ComparisonModel model) {
        ComparisonView view = new ComparisonView();
        view.showComparison(model);
        ShellView shell = shell(Screen.COMPARISON, "Compare routes \u00b7 shipment #" + model.shipmentId(), false,
                "3 unread");
        shell.setContent(view.root());
        return shell.root();
    }

    /** @return the booking detail page in the shell */
    public static Parent bookingDetail() {
        BookingDetailView view = new BookingDetailView();
        view.showNotice("Booked. Pay now to confirm the booking, or cancel it while it is unpaid.");
        view.showBooking(SampleData.bookingDetail());
        ShellView shell = shell(Screen.BOOKING, "Booking #12", false, "4 unread");
        shell.setContent(view.root());
        return shell.root();
    }

    /** @return the My bookings page in the shell */
    public static Parent bookings(List<BookingRow> rows) {
        BookingListView view = new BookingListView();
        view.showBookings(rows);
        ShellView shell = shell(Screen.BOOKINGS, Screen.BOOKINGS.getTitle(), false, "3 unread");
        shell.setContent(view.root());
        return shell.root();
    }

    /** @return a booking page in the shell (read-only for the administrator) */
    public static Parent booking(BookingDetailModel model, boolean admin) {
        BookingDetailView view = new BookingDetailView();
        view.showBooking(model);
        ShellView shell = shell(Screen.BOOKING, "Booking #" + model.bookingId(), admin, admin ? null : "2 unread");
        shell.setContent(view.root());
        return shell.root();
    }

    /** @return the notifications page in the shell */
    public static Parent notifications(NotificationsModel model, String unread) {
        NotificationsView view = new NotificationsView();
        view.showNotifications(model);
        ShellView shell = shell(Screen.NOTIFICATIONS, Screen.NOTIFICATIONS.getTitle(), false, unread);
        shell.setContent(view.root());
        return shell.root();
    }

    /** @return the reports page in the shell with an optional status */
    public static Parent reports(ReportsModel model, ReportProgress progress, boolean busy) {
        ReportsView view = new ReportsView();
        view.showReports(model);
        if (progress != null) {
            view.showProgress(progress);
        }
        view.setBusy(busy);
        ShellView shell = shell(Screen.REPORTS, Screen.REPORTS.getTitle(), false, "2 unread");
        shell.setContent(view.root());
        return shell.root();
    }

    /** @return the administrator's users page */
    public static Parent adminUsers() {
        AdminUsersView view = new AdminUsersView();
        view.showUsers(SampleData.users());
        ShellView shell = shell(Screen.ADMIN_USERS, Screen.ADMIN_USERS.getTitle(), true, null);
        shell.setContent(view.root());
        return shell.root();
    }

    /** @return the administrator's read-only list of every booking */
    public static Parent adminBookings() {
        BookingListView view = new BookingListView(true);
        view.showBookings(SampleData.bookings());
        ShellView shell = shell(Screen.ADMIN_BOOKINGS, Screen.ADMIN_BOOKINGS.getTitle(), true, null);
        shell.setContent(view.root());
        return shell.root();
    }

    /** @return the administrator's tracking page, optionally after a stale click */
    public static Parent adminTracking(boolean stale) {
        AdminTrackingView view = new AdminTrackingView();
        view.showRows(SampleData.adminTracking());
        if (stale) {
            view.showMessage(false, AdminTrackingPresenter.STALE);
        } else {
            view.showMessage(true, "Booking #12: In transit recorded.");
        }
        ShellView shell = shell(Screen.ADMIN_TRACKING, Screen.ADMIN_TRACKING.getTitle(), true, null);
        shell.setContent(view.root());
        return shell.root();
    }

    /** @return the administrator's tracking page with nothing to track */
    public static Parent adminTrackingEmpty() {
        AdminTrackingView view = new AdminTrackingView();
        view.showRows(List.of());
        ShellView shell = shell(Screen.ADMIN_TRACKING, Screen.ADMIN_TRACKING.getTitle(), true, null);
        shell.setContent(view.root());
        return shell.root();
    }

    /** @return the start-up dialog content on a plain background */
    public static Parent schema(SchemaStatus status) {
        DialogPane pane = Dialogs.schemaPane(status);
        pane.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        StackPane frame = new StackPane((Node) pane);
        frame.setPadding(new Insets(Theme.space(2)));
        frame.setBackground(Theme.background(Color.web("#D9DEE6"), 0));
        return frame;
    }
}

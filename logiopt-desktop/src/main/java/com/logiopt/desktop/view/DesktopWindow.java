package com.logiopt.desktop.view;

import com.logiopt.app.AppServices;
import com.logiopt.desktop.presenter.AccountPresenter;
import com.logiopt.desktop.presenter.AdminTrackingPresenter;
import com.logiopt.desktop.presenter.AdminUsersPresenter;
import com.logiopt.desktop.presenter.BookingListPresenter;
import com.logiopt.desktop.presenter.BookingPresenter;
import com.logiopt.desktop.presenter.ComparisonPresenter;
import com.logiopt.desktop.presenter.DashboardPresenter;
import com.logiopt.desktop.presenter.HistoryPresenter;
import com.logiopt.desktop.presenter.LoginPresenter;
import com.logiopt.desktop.presenter.NotificationsPresenter;
import com.logiopt.desktop.presenter.RegisterPresenter;
import com.logiopt.desktop.presenter.ReportsPresenter;
import com.logiopt.desktop.presenter.ShellPresenter;
import com.logiopt.desktop.presenter.ShipmentFormPresenter;
import com.logiopt.desktop.presenter.ShipmentListPresenter;
import com.logiopt.desktop.presenter.UnreadPoller;
import com.logiopt.desktop.support.BackgroundRunner;
import com.logiopt.desktop.support.Navigator;
import com.logiopt.desktop.support.Screen;
import com.logiopt.desktop.support.Session;
import com.logiopt.desktop.support.WindowSizing;
import com.logiopt.model.User;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/**
 * The main window. Implements the navigator's {@link Navigator.Shell}: public screens fill the window,
 * pages appear inside the {@link ShellView} frame. Every page is wired to its presenter here; the
 * presenters reach the database only through the background runner. While a customer is signed in,
 * the {@link UnreadPoller} keeps the badge current; it stops on sign-out and in {@link #close()}. A
 * page that polls (Reports) is closed when another page opens.
 */
public final class DesktopWindow implements Navigator.Shell {

    private final Stage stage;
    private final AppServices services;
    private final BackgroundRunner runner;
    private final Session session;
    private final Scene scene;
    private Navigator navigator;
    private ShellView shell;
    private ShellPresenter shellPresenter;
    private User shellUser;
    private UnreadPoller unread;
    private Runnable closePage = () -> { };

    /**
     * @param stage    primary stage
     * @param services shared services
     * @param runner   background runner
     * @param session  signed-in user holder
     */
    public DesktopWindow(Stage stage, AppServices services, BackgroundRunner runner, Session session) {
        this.stage = stage;
        this.services = services;
        this.runner = runner;
        this.session = session;
        this.scene = new Scene(new StackPane());
        scene.setFill(Theme.PAGE);
        stage.setTitle("LogiOpt");
        // javafx.stage.Screen is written out because com.logiopt.desktop.support.Screen is imported.
        Rectangle2D usable = javafx.stage.Screen.getPrimary().getVisualBounds();
        WindowSizing.Placement place = WindowSizing.place(new WindowSizing.Bounds(usable.getMinX(), usable.getMinY(),
                usable.getWidth(), usable.getHeight()));
        stage.setMinWidth(place.minWidth());
        stage.setMinHeight(place.minHeight());
        stage.setWidth(place.width());
        stage.setHeight(place.height());
        stage.setX(place.x());
        stage.setY(place.y());
        stage.setScene(scene);
    }

    /** @param value the navigator that drives this window */
    public void setNavigator(Navigator value) {
        this.navigator = value;
    }

    /** Shows the window. */
    public void show() {
        stage.show();
    }

    @Override
    public void showPublic(Screen screen, String notice) {
        closeCurrentPage();
        stopUnread();
        shell = null;
        shellUser = null;
        if (screen == Screen.REGISTER) {
            RegisterView view = new RegisterView();
            RegisterPresenter presenter = new RegisterPresenter(services.getAuthService(), runner,
                    message -> navigator.go(Screen.LOGIN, message), view);
            view.onRegister(presenter::register);
            view.onBack(() -> navigator.go(Screen.LOGIN));
            setRoot(view.root(), "LogiOpt – Create an account");
            view.focus();
            return;
        }
        LoginView view = new LoginView();
        LoginPresenter presenter = new LoginPresenter(services.getAuthService(), runner, navigator::signedIn, view);
        view.onSignIn(presenter::signIn);
        view.onCreateAccount(() -> navigator.go(Screen.REGISTER));
        view.showNotice(notice);
        setRoot(view.root(), "LogiOpt – Sign in");
        view.focus();
    }

    @Override
    public void showPage(Screen screen, Long shipmentId, String notice) {
        User user = session.require();
        closeCurrentPage();
        if (shell == null || shellUser != user) {
            shell = new ShellView(user.isAdmin());
            shellUser = user;
            shell.onMenu(navigator::go);
            shell.onSignOut(navigator::signOut);
            setRoot(shell.root(), "LogiOpt");
            startUnread(user, shell);
        }
        shell.setTitle(title(screen, shipmentId));
        shell.selectMenu(screen == Screen.EDIT_SHIPMENT || screen == Screen.COMPARISON ? Screen.SHIPMENTS
                : screen == Screen.BOOKING ? (user.isAdmin() ? Screen.ADMIN_BOOKINGS : Screen.BOOKINGS) : screen);
        stage.setTitle("LogiOpt – " + screen.getTitle());
        switch (screen) {
            case SHIPMENTS:
                showShipments(user, notice);
                break;
            case NEW_SHIPMENT:
            case EDIT_SHIPMENT:
                showForm(user, screen == Screen.EDIT_SHIPMENT ? shipmentId : null);
                break;
            case HISTORY:
                showHistory(user);
                break;
            case ACCOUNT:
                showAccount(user);
                break;
            case BOOKINGS:
                showBookings(user);
                break;
            case COMPARISON:
                showComparison(user, shipmentId);
                break;
            case BOOKING:
                showBooking(user, shipmentId, notice);
                break;
            case NOTIFICATIONS:
                showNotifications(user);
                break;
            case REPORTS:
                showReports(user);
                break;
            case ADMIN_USERS:
                showUsers(user);
                break;
            case ADMIN_BOOKINGS:
                showAllBookings(user);
                break;
            case ADMIN_TRACKING:
                showTracking(user);
                break;
            default:
                showDashboard(user);
        }
        shellPresenter = new ShellPresenter(services.getNotificationService(), runner, user, shell);
        shellPresenter.refresh();
    }

    private static String title(Screen screen, Long id) {
        if (id == null) {
            return screen.getTitle();
        }
        switch (screen) {
            case EDIT_SHIPMENT:
                return "Edit shipment #" + id;
            case COMPARISON:
                return "Compare routes \u00b7 shipment #" + id;
            case BOOKING:
                return "Booking #" + id;
            default:
                return screen.getTitle();
        }
    }

    private void showComparison(User user, long shipmentId) {
        ComparisonView view = new ComparisonView();
        ComparisonPresenter presenter = new ComparisonPresenter(services, runner, user, bookingId -> {
            view.closeOrderSummary();
            navigator.openBooking(bookingId, "Booked. Pay now to confirm the booking, or cancel it while it is unpaid.");
        }, view);
        view.onBook(presenter::review);
        view.onConfirm(presenter::confirm);
        view.onRetry(() -> presenter.load(shipmentId));
        shell.setContent(view.root());
        presenter.load(shipmentId);
    }

    private void showBooking(User user, long bookingId, String notice) {
        BookingDetailView view = new BookingDetailView();
        BookingPresenter presenter = new BookingPresenter(services, runner, user,
                () -> shellPresenter.refreshAfterChange(), view);
        view.onPay(presenter::pay);
        view.onCancel(presenter::cancel);
        view.onRetry(() -> presenter.load(bookingId));
        view.showNotice(notice);
        shell.setContent(view.root());
        presenter.load(bookingId);
    }

    private void showBookings(User user) {
        BookingListView view = new BookingListView();
        BookingListPresenter presenter = new BookingListPresenter(services.getBookingService(), runner, user, view);
        view.onOpen(id -> navigator.openBooking(id, null));
        view.onRetry(presenter::load);
        shell.setContent(view.root());
        presenter.load();
    }

    private void showDashboard(User user) {
        DashboardView view = new DashboardView();
        DashboardPresenter presenter = new DashboardPresenter(services.getDashboardService(), runner, user, view);
        view.onRetry(presenter::load);
        shell.setContent(view.root());
        presenter.load();
    }

    private void showShipments(User user, String notice) {
        ShipmentListView view = new ShipmentListView();
        ShipmentListPresenter presenter = new ShipmentListPresenter(services.getShipmentDao(), runner, user, view);
        view.onNew(() -> navigator.go(Screen.NEW_SHIPMENT));
        view.onEdit(navigator::editShipment);
        view.onCompare(navigator::compare);
        view.onRetry(presenter::load);
        view.showNotice(notice);
        shell.setContent(view.root());
        presenter.load();
    }

    private void showForm(User user, Long shipmentId) {
        ShipmentFormView view = new ShipmentFormView();
        ShipmentFormPresenter presenter = new ShipmentFormPresenter(services, runner, user,
                id -> navigator.go(Screen.SHIPMENTS, "Shipment #" + id + " saved."), view);
        presenter.onSavedAndCompare(navigator::compare);
        view.onSubmit(presenter::submit);
        view.onSubmitAndCompare(presenter::submitAndCompare);
        view.onCancel(() -> navigator.go(Screen.SHIPMENTS));
        shell.setContent(view.root());
        if (shipmentId == null) {
            presenter.loadNew();
        } else {
            presenter.loadEdit(shipmentId);
        }
    }

    private void showHistory(User user) {
        HistoryView view = new HistoryView();
        HistoryPresenter presenter = new HistoryPresenter(services.getHistoryService(), runner, user, view);
        view.onRetry(presenter::load);
        shell.setContent(view.root());
        presenter.load();
    }

    private void showAccount(User user) {
        AccountView view = new AccountView();
        AccountPresenter presenter = new AccountPresenter(services.getAuthService(), runner, user, renamed -> {
            session.signIn(renamed);
            shellUser = renamed;
            shell.showUser(renamed.getName(), renamed.getEmail());
        }, view);
        view.onSaveName(presenter::saveName);
        view.onChangePassword(presenter::changePassword);
        view.onRetry(presenter::load);
        shell.setContent(view.root());
        presenter.load();
    }

    private void showNotifications(User user) {
        NotificationsView view = new NotificationsView();
        NotificationsPresenter presenter = new NotificationsPresenter(services.getNotificationService(), runner, user,
                () -> shellPresenter.refresh(), id -> navigator.openBooking(id, null), view);
        view.onMarkRead(presenter::markRead);
        view.onMarkAllRead(presenter::markAllRead);
        view.onOpen(presenter::open);
        view.onRetry(presenter::load);
        shell.setContent(view.root());
        presenter.load();
    }

    private void showReports(User user) {
        ReportsView view = new ReportsView();
        ReportsPresenter presenter = new ReportsPresenter(services, runner, user, Platform::runLater,
                ReportsPresenter.STATUS_PERIOD, view);
        view.onGenerate(presenter::generate);
        view.onSaveCopy(presenter::saveCopy);
        view.fileNames(ReportsPresenter::suggestedName);
        view.onRetry(presenter::load);
        closePage = presenter::close;
        shell.setContent(view.root());
        presenter.load();
    }

    private void showUsers(User user) {
        AdminUsersView view = new AdminUsersView();
        AdminUsersPresenter presenter = new AdminUsersPresenter(services.getAuthService(), runner, user, view);
        view.onRetry(presenter::load);
        shell.setContent(view.root());
        presenter.load();
    }

    private void showAllBookings(User user) {
        BookingListView view = new BookingListView(true);
        BookingListPresenter presenter = BookingListPresenter.allBookings(services.getBookingService(), runner, user,
                view);
        view.onOpen(id -> navigator.openBooking(id, null));
        view.onRetry(presenter::load);
        shell.setContent(view.root());
        presenter.load();
    }

    private void showTracking(User user) {
        AdminTrackingView view = new AdminTrackingView();
        AdminTrackingPresenter presenter = new AdminTrackingPresenter(services.getTrackingService(), runner, user, view);
        view.onRecord(presenter::record);
        view.onAdvance(presenter::advance);
        view.onRetry(presenter::load);
        shell.setContent(view.root());
        presenter.load();
    }

    private void startUnread(User user, ShellView frame) {
        stopUnread();
        if (user.isAdmin()) {
            return;
        }
        unread = new UnreadPoller(services.getNotificationService(), user, Platform::runLater, text -> {
            if (shell == frame) {
                frame.showUnread(text);
            }
        }, UnreadPoller.PERIOD);
        unread.start();
    }

    private void stopUnread() {
        if (unread != null) {
            unread.stop();
            unread = null;
        }
    }

    private void closeCurrentPage() {
        Runnable close = closePage;
        closePage = () -> { };
        close.run();
    }

    /** Stops the unread poller and any polling page; called on exit. */
    public void close() {
        closeCurrentPage();
        stopUnread();
    }

    private void setRoot(Parent root, String title) {
        scene.setRoot(root);
        stage.setTitle(title);
    }
}

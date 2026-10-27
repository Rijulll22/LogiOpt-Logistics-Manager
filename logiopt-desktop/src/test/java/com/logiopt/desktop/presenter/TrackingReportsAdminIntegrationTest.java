package com.logiopt.desktop.presenter;

import com.logiopt.app.AppServices;
import com.logiopt.app.CoreRuntime;
import com.logiopt.app.ShipmentFormInput;
import com.logiopt.dao.DBConnection;
import com.logiopt.dao.MigrationRunner;
import com.logiopt.dao.TestDatabase;
import com.logiopt.desktop.presenter.Models.AdminTrackingRow;
import com.logiopt.desktop.presenter.Models.NotificationRow;
import com.logiopt.desktop.presenter.Models.NotificationsModel;
import com.logiopt.desktop.presenter.Models.ReportJobRow;
import com.logiopt.desktop.presenter.Models.ReportProgress;
import com.logiopt.desktop.presenter.Models.ReportsModel;
import com.logiopt.desktop.presenter.Models.StepState;
import com.logiopt.desktop.presenter.Models.TimelineModel;
import com.logiopt.desktop.presenter.Models.TimelineStep;
import com.logiopt.desktop.presenter.Models.UserRow;
import com.logiopt.desktop.support.ErrorMessages;
import com.logiopt.model.Milestone;
import com.logiopt.model.PaymentMethod;
import com.logiopt.model.Route;
import com.logiopt.model.User;
import com.logiopt.report.ReportDownload;
import com.logiopt.service.AuthService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase D4 through the desktop presenters with fake views and the real shared services on the
 * disposable test database (LOGIOPT_TEST_DB_URL, name ending in _test): the tracking timeline, the
 * administrator's tracking, users and bookings screens and their refusal for customers,
 * notifications, the unread poller and reports. Waits use latches, never sleeps.
 */
class TrackingReportsAdminIntegrationTest {

    private static final String PASSWORD = "d4-tests-pass-1";
    private static final String ADMIN_PASSWORD = "d4-admin-pass-1";
    private static final Duration WAIT = Duration.ofSeconds(20);

    private static DBConnection db;
    private static CoreRuntime runtime;
    private static AppServices services;
    private static User alice;
    private static User bob;
    private static User admin;
    private static long singleLegRoute;
    private static long multiLegRoute;

    private final Fakes.CheckingRunner runner = new Fakes.CheckingRunner();

    @BeforeAll
    static void setUp() throws Exception {
        db = TestDatabase.connect();
        TestDatabase.dropAllTables(db);
        new MigrationRunner(db).migrate();
        Map<String, String> env = Map.of(AuthService.ENV_ADMIN_PASSWORD, ADMIN_PASSWORD);
        runtime = new CoreRuntime(db, env::get, Files.createTempDirectory("logiopt-d4"), "cp");
        services = runtime.services();
        runtime.onSchemaReady();
        AuthService auth = services.getAuthService();
        auth.register("alice.d4@example.test", "Alice", PASSWORD.toCharArray());
        auth.register("bob.d4@example.test", "Bob", PASSWORD.toCharArray());
        alice = auth.login("alice.d4@example.test", PASSWORD.toCharArray(), "test").getUser();
        bob = auth.login("bob.d4@example.test", PASSWORD.toCharArray(), "test").getUser();
        admin = auth.login(AuthService.ADMIN_EMAIL, ADMIN_PASSWORD.toCharArray(), "test").getUser();
        for (Route r : services.getRouteDao().findBySourceAndDestination("Mumbai", "Delhi")) {
            if (r.getLegs().size() > 1 && multiLegRoute == 0) {
                multiLegRoute = r.getRouteId();
            }
        }
        singleLegRoute = 2;
        assertTrue(multiLegRoute > 0, "a seeded Mumbai -> Delhi route has more than one leg");
    }

    @AfterAll
    static void stop() {
        if (runtime != null) {
            runtime.shutdown();
        }
    }

    // ---------------------------------------------------------------- helpers

    private long shipment(User owner, String preference) {
        Map<String, String> values = new HashMap<>();
        values.put(ShipmentFormInput.SOURCE, "Mumbai");
        values.put(ShipmentFormInput.DESTINATION, "Delhi");
        values.put(ShipmentFormInput.CARGO_TYPE, "GENERAL");
        values.put(ShipmentFormInput.WEIGHT_KG, "5000");
        values.put(ShipmentFormInput.DIMENSIONS, "");
        values.put(ShipmentFormInput.DISPATCH_DATE, LocalDate.now().plusDays(7).toString());
        values.put(ShipmentFormInput.PREFERENCE, preference);
        List<Long> saved = new ArrayList<>();
        Fakes.FormView form = new Fakes.FormView();
        ShipmentFormPresenter p = new ShipmentFormPresenter(services, runner, owner, saved::add, form);
        p.loadNew();
        p.submit(values);
        assertEquals(1, saved.size(), form.calls.toString());
        return saved.get(0);
    }

    private long booking(User owner, long routeId, boolean paid) throws Exception {
        long id = services.getBookingService().book(owner, shipment(owner, "BALANCED"), routeId, "BALANCED");
        if (paid) {
            services.getPaymentService().simulate(owner, id, PaymentMethod.UPI, true);
        }
        services.getNotificationService().awaitIdle(WAIT);
        return id;
    }

    private Models.BookingDetailModel detail(User user, long bookingId) {
        Fakes.BookingView view = new Fakes.BookingView();
        new BookingPresenter(services, runner, user, () -> { }, view).load(bookingId);
        assertNotNull(view.model, view.calls.toString());
        return view.model;
    }

    private static List<String> labels(TimelineModel t) {
        List<String> labels = new ArrayList<>();
        for (TimelineStep s : t.steps()) {
            labels.add(s.label());
        }
        return labels;
    }

    private AdminTrackingRow row(TrackingView view, long bookingId) {
        for (AdminTrackingRow r : view.rows) {
            if (r.bookingId() == bookingId) {
                return r;
            }
        }
        return null;
    }

    private long eventCount(long bookingId) throws Exception {
        return TestDatabase.queryLong(db, "SELECT COUNT(*) FROM tracking WHERE booking_id = " + bookingId);
    }

    // ---------------------------------------------------------------- timeline and admin tracking

    @Test
    void unpaidBookingShowsAMessageInsteadOfATimeline() throws Exception {
        long id = booking(alice, singleLegRoute, false);
        TimelineModel t = detail(alice, id).timeline();
        assertFalse(t.tracked());
        assertEquals(ViewModels.UNPAID_TIMELINE, t.message());
        assertTrue(t.steps().isEmpty());
    }

    @Test
    void milestonesFollowTheRouteWithAtTransferOnlyOnMultiLegRoutes() throws Exception {
        long single = booking(alice, singleLegRoute, true);
        long multi = booking(alice, multiLegRoute, true);
        TimelineModel booked = detail(alice, single).timeline();
        assertEquals(List.of("Booked", "Picked up", "In transit", "Out for delivery", "Delivered"), labels(booked));
        assertEquals(List.of(StepState.CURRENT, StepState.NEXT, StepState.LATER, StepState.LATER, StepState.LATER),
                booked.steps().stream().map(TimelineStep::state).toList());
        assertEquals("System", booked.steps().get(0).source());
        assertEquals(List.of("Booked", "Picked up", "In transit", "At transfer", "Out for delivery", "Delivered"),
                labels(detail(alice, multi).timeline()));

        TrackingView view = new TrackingView();
        AdminTrackingPresenter p = new AdminTrackingPresenter(services.getTrackingService(), runner, admin, view);
        p.load();
        assertEquals("Picked up", row(view, multi).next());
        p.record(row(view, multi), "Collected from the Mumbai warehouse.");
        assertTrue(view.success, view.calls.toString());
        List<String> order = new ArrayList<>(List.of("Booked", "Picked up"));
        while (row(view, multi) != null) {
            order.add(row(view, multi).next());
            p.advance(row(view, multi));
        }
        assertEquals(labels(detail(alice, multi).timeline()), order, "one milestone at a time, in route order");
        TimelineModel done = detail(alice, multi).timeline();
        assertEquals("Delivered. Tracking is complete.", done.message());
        assertEquals(StepState.CURRENT, done.steps().get(5).state());
        assertEquals("Administrator", done.steps().get(1).source());
        assertEquals("Collected from the Mumbai warehouse.", done.steps().get(1).description());
        assertEquals("Simulated", done.steps().get(3).source());
        assertEquals(6, eventCount(multi));
    }

    @Test
    void repeatedAdvanceRecordsOneEventAndAStaleScreenSaysSo() throws Exception {
        long id = booking(alice, singleLegRoute, true);
        TrackingView view = new TrackingView();
        AdminTrackingPresenter p = new AdminTrackingPresenter(services.getTrackingService(), runner, admin, view);
        p.load();
        AdminTrackingRow shown = row(view, id);
        p.advance(shown);
        assertTrue(view.success);
        p.advance(shown);
        p.record(shown, "A second click from the same screen.");
        assertFalse(view.success);
        assertEquals(AdminTrackingPresenter.STALE, view.message);
        assertEquals(2, eventCount(id), "Booked plus one Picked up");
        assertEquals("In transit", row(view, id).next(), "the list shows the current state");
    }

    @Test
    void recordingNeedsADescription() throws Exception {
        long id = booking(alice, singleLegRoute, true);
        TrackingView view = new TrackingView();
        AdminTrackingPresenter p = new AdminTrackingPresenter(services.getTrackingService(), runner, admin, view);
        p.load();
        p.record(row(view, id), "   ");
        assertFalse(view.success);
        assertTrue(view.message.contains("description"), view.message);
        assertEquals(1, eventCount(id));
    }

    @Test
    void aCustomerCannotAdvanceAddTrackingOrListUsers() throws Exception {
        long id = booking(alice, singleLegRoute, true);
        int before = runner.tasks;
        assertThrows(SecurityException.class, () -> services.getTrackingService().advance(alice, id, Milestone.BOOKED));
        assertThrows(SecurityException.class, () -> services.getTrackingService().record(alice, id, Milestone.BOOKED,
                Milestone.PICKED_UP, "no"));
        assertThrows(SecurityException.class, () -> services.getAuthService().listUsers(alice));
        assertThrows(SecurityException.class, () -> services.getBookingService().allBookings(alice));

        TrackingView tracking = new TrackingView();
        AdminTrackingPresenter p = new AdminTrackingPresenter(services.getTrackingService(), runner, alice, tracking);
        p.load();
        assertEquals("error " + ErrorMessages.NOT_FOUND, tracking.last());
        p.advance(new AdminTrackingRow(id, "", "", "Booked", "BOOKED", "Picked up", "PICKED_UP", ""));
        p.record(new AdminTrackingRow(id, "", "", "Booked", "BOOKED", "Picked up", "PICKED_UP", ""), "no");
        assertEquals(ErrorMessages.NOT_FOUND, tracking.message);
        UsersView users = new UsersView();
        new AdminUsersPresenter(services.getAuthService(), runner, alice, users).load();
        assertEquals("error " + ErrorMessages.NOT_FOUND, users.last());
        Fakes.BookingListView all = new Fakes.BookingListView();
        BookingListPresenter.allBookings(services.getBookingService(), runner, bob, all).load();
        assertEquals("error " + ErrorMessages.NOT_FOUND, all.last());
        assertEquals(before, runner.tasks, "nothing reached the services");
        assertEquals(1, eventCount(id));
    }

    @Test
    void theAdministratorSeesUsersWithoutPasswordDataAndEveryBooking() throws Exception {
        long id = booking(bob, singleLegRoute, false);
        UsersView users = new UsersView();
        new AdminUsersPresenter(services.getAuthService(), runner, admin, users).load();
        assertTrue(users.rows.stream().anyMatch(r -> r.email().equals(AuthService.ADMIN_EMAIL) && r.role().equals("Admin")));
        assertTrue(users.rows.stream().anyMatch(r -> r.email().equals("bob.d4@example.test")));
        for (RecordComponent c : UserRow.class.getRecordComponents()) {
            assertFalse(c.getName().toLowerCase().contains("pass") || c.getName().toLowerCase().contains("hash"),
                    c.getName());
        }
        Fakes.BookingListView all = new Fakes.BookingListView();
        BookingListPresenter.allBookings(services.getBookingService(), runner, admin, all).load();
        assertTrue(all.rows.stream().anyMatch(r -> r.id() == id));
        Models.BookingDetailModel m = detail(admin, id);
        assertFalse(m.canPay() || m.canCancel(), "read-only for the administrator");
    }

    @Test
    void anotherUsersBookingLooksMissing() throws Exception {
        long id = booking(alice, singleLegRoute, true);
        Fakes.BookingView view = new Fakes.BookingView();
        new BookingPresenter(services, runner, bob, () -> { }, view).load(id);
        assertEquals(ErrorMessages.NOT_FOUND, view.error);
        assertEquals(null, view.model);
    }

    // ---------------------------------------------------------------- notifications

    @Test
    void notificationsAreOwnerOnlyAndKeepTheirReadState() throws Exception {
        User carol = newCustomer("carol.d4@example.test");
        long id = booking(carol, singleLegRoute, true);
        NotesView view = new NotesView();
        List<Long> opened = new ArrayList<>();
        int[] changed = {0};
        NotificationsPresenter p = new NotificationsPresenter(services.getNotificationService(), runner, carol,
                () -> changed[0]++, opened::add, view);
        p.load();
        NotificationsModel m = view.model;
        assertTrue(m.rows().size() >= 2, "booking and payment: " + m.rows());
        assertEquals(m.rows().size(), m.unread());
        NotificationRow first = m.rows().get(0);

        NotesView bobView = new NotesView();
        NotificationsPresenter other = new NotificationsPresenter(services.getNotificationService(), runner, bob,
                () -> { }, opened::add, bobView);
        other.markRead(first.id());
        assertEquals(ErrorMessages.NOT_FOUND, bobView.message);
        other.open(first.id(), id);
        assertTrue(opened.isEmpty(), "another user's notification opens nothing");
        assertEquals(m.unread(), services.getNotificationService().unreadCount(carol));

        p.markRead(first.id());
        assertEquals(m.unread() - 1, view.model.unread());
        assertTrue(view.model.rows().stream().anyMatch(r -> r.id() == first.id() && !r.unread()));
        NotificationRow second = view.model.rows().stream().filter(NotificationRow::unread).findFirst().orElseThrow();
        p.open(second.id(), second.bookingId());
        assertEquals(List.of(id), opened);
        p.markAllRead();
        assertEquals(0, view.model.unread());
        assertEquals(NotificationsPresenter.ALL_READ, view.message);
        assertEquals(0, services.getNotificationService().unreadCount(carol));
        assertEquals(3, changed[0]);
    }

    @Test
    void unreadPollerCountsOnOneNamedDaemonThreadAndStopsCleanly() throws Exception {
        User dave = newCustomer("dave.d4@example.test");
        booking(dave, singleLegRoute, false);
        CountDownLatch twice = new CountDownLatch(2);
        AtomicReference<String> badge = new AtomicReference<>();
        AtomicReference<Thread> thread = new AtomicReference<>();
        UnreadPoller poller = new UnreadPoller(services.getNotificationService(), dave, Runnable::run, text -> {
            badge.set(text);
            thread.set(Thread.currentThread());
            twice.countDown();
        }, Duration.ofMillis(20));
        poller.start();
        assertTrue(twice.await(WAIT.toSeconds(), TimeUnit.SECONDS), "two polls");
        assertEquals("1 unread", badge.get());
        assertEquals(UnreadPoller.THREAD_NAME, thread.get().getName());
        assertTrue(thread.get().isDaemon());
        assertTrue(poller.isRunning());
        poller.stop();
        assertTrue(poller.awaitStopped(WAIT));
        assertFalse(poller.isRunning());
        assertFalse(thread.get().isAlive());
        poller.stop();
    }

    private static User newCustomer(String email) throws Exception {
        services.getAuthService().register(email, "Customer", PASSWORD.toCharArray());
        return services.getAuthService().login(email, PASSWORD.toCharArray(), "test").getUser();
    }

    // ---------------------------------------------------------------- reports

    private ReportsView generate(AppServices s, User user, long shipmentId, String strategy, ExecutorService ui)
            throws Exception {
        ReportsView view = new ReportsView();
        ReportsPresenter p = new ReportsPresenter(s, runner, user, ui, Duration.ofMillis(20), view);
        view.presenter = p;
        ui.submit(p::load).get();
        ui.submit(() -> p.generate(shipmentId, strategy)).get();
        assertTrue(view.finished.await(WAIT.toSeconds(), TimeUnit.SECONDS), "the report finished");
        ui.submit(() -> { }).get();
        assertFalse(p.isFollowing(), "the status poll stops when the job is finished");
        return view;
    }

    @Test
    void aReportIsGeneratedPersistedAndSavedAsAByteIdenticalCopy() throws Exception {
        long id = shipment(alice, "CHEAPEST");
        ExecutorService ui = Executors.newSingleThreadExecutor();
        try {
            ReportsView view = generate(services, alice, id, "FASTEST", ui);
            assertTrue(view.copy().stream().anyMatch(c -> c.equals("busy true")));
            assertTrue(view.progress.done(), view.copy().toString());
            assertEquals("Done", view.progress.status());
            assertTrue(view.model.shipments().stream().anyMatch(c -> c.id() == id && c.preferredKey().equals("CHEAPEST")),
                    "the strategy defaults to the shipment's preference");
            ReportJobRow row = view.model.reports().get(0);
            assertEquals(view.progress.jobId(), row.jobId());
            assertTrue(row.canSave());
            assertEquals("DONE", TestDatabase.queryString(db, "SELECT status FROM reports WHERE job_id = '"
                    + row.jobId() + "'"));

            Path copy = Files.createTempDirectory("logiopt-copy").resolve(ReportsPresenter.suggestedName(row));
            view.presenter.saveCopy(row.jobId(), copy);
            ReportDownload original = services.getReportService().download(row.jobId(), alice.getUserId()).orElseThrow();
            assertArrayEquals(Files.readAllBytes(original.getFile()), Files.readAllBytes(copy));
            assertEquals("Saved a copy as " + copy.getFileName() + ".", view.message);
            assertTrue(new String(Files.readAllBytes(copy), java.nio.charset.StandardCharsets.UTF_8)
                    .contains("Mumbai"));

            ReportsView bobView = new ReportsView();
            Path stolen = copy.resolveSibling("stolen.txt");
            new ReportsPresenter(services, runner, bob, ui, Duration.ofMillis(20), bobView).saveCopy(row.jobId(), stolen);
            assertEquals(ErrorMessages.NOT_FOUND, bobView.message);
            assertFalse(Files.exists(stolen));
        } finally {
            ui.shutdownNow();
        }
    }

    @Test
    void aPastDatedShipmentStillProducesAReport() throws Exception {
        long id = shipment(alice, "BALANCED");
        TestDatabase.execute(db, "UPDATE shipments SET dispatch_date = '2025-01-15' WHERE shipment_id = " + id);
        ExecutorService ui = Executors.newSingleThreadExecutor();
        try {
            ReportsView view = generate(services, alice, id, "BALANCED", ui);
            assertTrue(view.progress.done(), view.copy().toString());
        } finally {
            ui.shutdownNow();
        }
    }

    @Test
    void aFailedReportShowsAGenericMessageWithoutDetails() throws Exception {
        Path notADirectory = Files.createTempFile("logiopt-reports", ".blocked");
        CoreRuntime broken = new CoreRuntime(db, name -> null, notADirectory, "cp");
        ExecutorService ui = Executors.newSingleThreadExecutor();
        try {
            long id = shipment(alice, "BALANCED");
            ReportsView view = generate(broken.services(), alice, id, "BALANCED", ui);
            assertTrue(view.progress.finished());
            assertFalse(view.progress.done());
            assertEquals(ViewModels.REPORT_FAILED, view.progress.message());
            assertFalse(view.copy().toString().contains(notADirectory.toString()), "no path is shown");
            assertFalse(view.copy().toString().contains("Exception"), "no stack trace is shown");
            assertFalse(view.model.reports().get(0).canSave());
        } finally {
            ui.shutdownNow();
            broken.shutdown();
        }
    }

    @Test
    void closingTheReportsScreenStopsItsStatusPoll() throws Exception {
        ReportsView view = new ReportsView();
        ReportsPresenter p = new ReportsPresenter(services, runner, alice, Runnable::run, Duration.ofMillis(20), view);
        p.close();
        p.generate(shipment(alice, "BALANCED"), "BALANCED");
        assertFalse(p.isFollowing());
        assertFalse(view.copy().contains("busy true"), "a closed screen starts nothing");
    }

    // ---------------------------------------------------------------- fake views

    static final class TrackingView extends Fakes.Recorder implements AdminTrackingPresenter.View {
        List<AdminTrackingRow> rows = List.of();
        boolean success;
        String message;

        @Override
        public void showLoading() {
            record("loading");
        }

        @Override
        public void showRows(List<AdminTrackingRow> value) {
            record("rows " + value.size());
            rows = value;
        }

        @Override
        public void showError(String text) {
            record("error " + text);
        }

        @Override
        public void showMessage(boolean ok, String text) {
            record("message " + text);
            success = ok;
            message = text;
        }

        @Override
        public void setBusy(boolean busy) {
            record("busy " + busy);
        }
    }

    static final class UsersView extends Fakes.Recorder implements AdminUsersPresenter.View {
        List<UserRow> rows = List.of();

        @Override
        public void showLoading() {
            record("loading");
        }

        @Override
        public void showUsers(List<UserRow> value) {
            record("users " + value.size());
            rows = value;
        }

        @Override
        public void showError(String text) {
            record("error " + text);
        }
    }

    static final class NotesView extends Fakes.Recorder implements NotificationsPresenter.View {
        NotificationsModel model;
        String message;

        @Override
        public void showLoading() {
            record("loading");
        }

        @Override
        public void showNotifications(NotificationsModel value) {
            record("notifications " + value.rows().size());
            model = value;
        }

        @Override
        public void showError(String text) {
            record("error " + text);
        }

        @Override
        public void showMessage(String text) {
            record("message " + text);
            message = text;
        }

        @Override
        public void setBusy(boolean busy) {
            record("busy " + busy);
        }
    }

    static final class ReportsView extends Fakes.Recorder implements ReportsPresenter.View {
        final CountDownLatch finished = new CountDownLatch(1);
        volatile ReportsModel model;
        volatile ReportProgress progress;
        volatile String message;
        ReportsPresenter presenter;

        @Override
        synchronized void record(String call) {
            super.record(call);
        }

        synchronized List<String> copy() {
            return new ArrayList<>(calls);
        }

        @Override
        public void showLoading() {
            record("loading");
        }

        @Override
        public void showReports(ReportsModel value) {
            record("reports " + value.reports().size());
            model = value;
            if (progress != null && progress.finished()) {
                finished.countDown();
            }
        }

        @Override
        public void showError(String text) {
            record("error " + text);
        }

        @Override
        public void showProgress(ReportProgress value) {
            record("progress " + value.status() + " " + value.message());
            progress = value;
        }

        @Override
        public void showMessage(String text) {
            record("message " + text);
            message = text;
        }

        @Override
        public void setBusy(boolean busy) {
            record("busy " + busy);
        }
    }
}

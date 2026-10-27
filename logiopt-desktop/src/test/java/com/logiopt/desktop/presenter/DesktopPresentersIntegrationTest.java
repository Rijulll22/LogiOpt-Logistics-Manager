package com.logiopt.desktop.presenter;

import com.logiopt.app.AppServices;
import com.logiopt.app.CoreRuntime;
import com.logiopt.app.ShipmentFormInput;
import com.logiopt.dao.DBConnection;
import com.logiopt.dao.MigrationRunner;
import com.logiopt.dao.TestDatabase;
import com.logiopt.desktop.presenter.Models.ShipmentRow;
import com.logiopt.desktop.support.ErrorMessages;
import com.logiopt.model.User;
import com.logiopt.service.AuthService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The desktop presenters with fake views and the real shared services against the disposable test
 * database (LOGIOPT_TEST_DB_URL, name ending in _test), rebuilt from the migrations first. Proves the
 * desktop client reuses the services' throttling, validation and owner checks. Never uses
 * LOGIOPT_DB_URL.
 */
class DesktopPresentersIntegrationTest {

    private static final String PASSWORD = "desktop-pass-1";
    private static final String ADMIN_PASSWORD = "desktop-admin-pass";

    private static DBConnection db;
    private static CoreRuntime runtime;
    private static AppServices services;
    private static User alice;
    private static User bob;
    private static User admin;

    private final Fakes.CheckingRunner runner = new Fakes.CheckingRunner();

    @BeforeAll
    static void setUp() throws Exception {
        db = TestDatabase.connect();
        TestDatabase.dropAllTables(db);
        new MigrationRunner(db).migrate();
        Map<String, String> env = Map.of(AuthService.ENV_ADMIN_PASSWORD, ADMIN_PASSWORD);
        runtime = new CoreRuntime(db, env::get, Files.createTempDirectory("logiopt-desktop-reports"), "cp");
        services = runtime.services();
        runtime.onSchemaReady();
        AuthService auth = services.getAuthService();
        auth.register("alice@example.test", "Alice Archer", PASSWORD.toCharArray());
        auth.register("bob@example.test", "Bob Baker", PASSWORD.toCharArray());
        alice = signIn("alice@example.test", PASSWORD);
        bob = signIn("bob@example.test", PASSWORD);
        admin = signIn(AuthService.ADMIN_EMAIL, ADMIN_PASSWORD);
    }

    @AfterAll
    static void stop() {
        if (runtime != null) {
            runtime.shutdown();
        }
    }

    private static User signIn(String email, String password) {
        AtomicReference<User> user = new AtomicReference<>();
        Fakes.LoginView view = new Fakes.LoginView();
        new LoginPresenter(services.getAuthService(), new Fakes.CheckingRunner(), user::set, view)
                .signIn(email, password.toCharArray());
        assertNotNull(user.get(), view.calls.toString());
        return user.get();
    }

    private static Map<String, String> form(String source, String destination, String weight, LocalDate date) {
        Map<String, String> values = new HashMap<>();
        values.put(ShipmentFormInput.SOURCE, source);
        values.put(ShipmentFormInput.DESTINATION, destination);
        values.put(ShipmentFormInput.CARGO_TYPE, "GENERAL");
        values.put(ShipmentFormInput.WEIGHT_KG, weight);
        values.put(ShipmentFormInput.DIMENSIONS, "");
        values.put(ShipmentFormInput.DISPATCH_DATE, date.toString());
        return values;
    }

    private long create(User owner, Map<String, String> values) {
        AtomicReference<Long> saved = new AtomicReference<>();
        Fakes.FormView view = new Fakes.FormView();
        ShipmentFormPresenter presenter = new ShipmentFormPresenter(services, runner, owner, saved::set, view);
        presenter.loadNew();
        presenter.submit(values);
        assertNotNull(saved.get(), view.calls.toString());
        return saved.get();
    }

    @Test
    void signInSucceedsFailsGenericallyAndIsThrottled() {
        assertEquals("alice@example.test", alice.getEmail());
        assertTrue(admin.isAdmin());
        Fakes.LoginView view = new Fakes.LoginView();
        AtomicReference<User> user = new AtomicReference<>();
        LoginPresenter presenter = new LoginPresenter(services.getAuthService(), runner, user::set, view);
        presenter.signIn("alice@example.test", "wrong-password".toCharArray());
        assertEquals(List.of("error null", "busy true", "busy false", "error " + LoginPresenter.INVALID_MESSAGE), view.calls);
        presenter.signIn("nobody@example.test", "wrong-password".toCharArray());
        assertEquals("error " + LoginPresenter.INVALID_MESSAGE, view.last(), "unknown email looks the same");
        for (int i = 0; i < 5; i++) {
            presenter.signIn("carol@example.test", "wrong-password".toCharArray());
        }
        assertEquals("error " + LoginPresenter.LOCKED_MESSAGE, view.last());
        assertNull(user.get());
    }

    @Test
    void registrationShowsFieldErrorsAndDuplicatesLookLikeSuccess() throws Exception {
        Fakes.RegisterView view = new Fakes.RegisterView();
        List<String> notices = new ArrayList<>();
        RegisterPresenter presenter = new RegisterPresenter(services.getAuthService(), runner, notices::add, view);
        presenter.register("not-an-email", "", "short".toCharArray());
        assertEquals(List.of("Enter a valid email address."), view.errors.get(RegisterPresenter.EMAIL));
        assertEquals(List.of("Name is required."), view.errors.get(RegisterPresenter.NAME));
        assertEquals(List.of("Password must be at least 8 characters."), view.errors.get(RegisterPresenter.PASSWORD));
        assertTrue(notices.isEmpty());
        long before = TestDatabase.queryLong(db, "SELECT COUNT(*) FROM users");
        presenter.register("dora@example.test", "Dora", PASSWORD.toCharArray());
        presenter.register("ALICE@example.test", "Someone else", "another-pass".toCharArray());
        assertEquals(List.of(RegisterPresenter.REGISTERED_NOTICE, RegisterPresenter.REGISTERED_NOTICE), notices,
                "a duplicate looks exactly like a new registration");
        assertEquals(before + 1, TestDatabase.queryLong(db, "SELECT COUNT(*) FROM users"));
    }

    @Test
    void shipmentFormValidatesLikeTheWebAndSavesForTheSignedInUserOnly() throws Exception {
        Fakes.FormView view = new Fakes.FormView();
        List<Long> saved = new ArrayList<>();
        ShipmentFormPresenter presenter = new ShipmentFormPresenter(services, runner, alice, saved::add, view);
        presenter.loadNew();
        assertEquals(List.of("Ahmedabad", "Bengaluru", "Chennai", "Delhi", "Kolkata", "Mumbai"), view.model.cities());
        assertNull(view.model.editId());
        long before = TestDatabase.queryLong(db, "SELECT COUNT(*) FROM shipments");
        presenter.submit(form("Mumbai", "mumbai ", "0.0001", LocalDate.now().minusDays(1)));
        assertEquals(List.of("Source and destination must be different."), view.errors.get(ShipmentFormInput.DESTINATION));
        assertEquals(List.of("Weight must be at least 0.001 kg."), view.errors.get(ShipmentFormInput.WEIGHT_KG));
        assertEquals(List.of("Dispatch date cannot be in the past."), view.errors.get(ShipmentFormInput.DISPATCH_DATE));
        presenter.submit(form("Atlantis", "Delhi", "1.2345", LocalDate.now().plusDays(3)));
        assertTrue(view.errors.get(ShipmentFormInput.SOURCE).get(0).startsWith("Unknown source city"), view.errors.toString());
        assertEquals(List.of("Weight can have at most 3 decimal places."), view.errors.get(ShipmentFormInput.WEIGHT_KG));
        assertEquals(before, TestDatabase.queryLong(db, "SELECT COUNT(*) FROM shipments"), "nothing saved");
        assertTrue(saved.isEmpty());

        Map<String, String> valid = form("Mumbai", "Delhi", "5000", LocalDate.now().plusDays(7));
        valid.put(ShipmentFormInput.CARGO_TYPE, "FRAGILE");
        presenter.submit(valid);
        assertEquals(1, saved.size(), view.calls.toString());
        long id = saved.get(0);
        assertEquals(alice.getUserId(), TestDatabase.queryLong(db, "SELECT user_id FROM shipments WHERE shipment_id = " + id));
        assertEquals("FRAGILE", TestDatabase.queryString(db, "SELECT cargo_type FROM shipments WHERE shipment_id = " + id));
        assertTrue(runner.tasks >= 4, "every database call ran as background work");
    }

    @Test
    void draftsCanBeEditedByTheirOwnerOnlyAndBookedOnesNot() throws Exception {
        long id = create(alice, form("Chennai", "Kolkata", "320", LocalDate.now().plusDays(5)));
        Fakes.FormView view = new Fakes.FormView();
        List<Long> saved = new ArrayList<>();
        ShipmentFormPresenter presenter = new ShipmentFormPresenter(services, runner, alice, saved::add, view);
        presenter.loadEdit(id);
        assertEquals(id, view.model.editId());
        assertEquals("320", view.model.values().get(ShipmentFormInput.WEIGHT_KG));
        assertEquals("Chennai", view.model.values().get(ShipmentFormInput.SOURCE));
        presenter.submit(form("Chennai", "Kolkata", "1234.567", LocalDate.now().plusDays(6)));
        assertEquals(List.of(id), saved);
        assertEquals("1234.567", TestDatabase.queryString(db, "SELECT weight_kg FROM shipments WHERE shipment_id = " + id));

        Fakes.FormView foreign = new Fakes.FormView();
        new ShipmentFormPresenter(services, runner, bob, x -> { }, foreign).loadEdit(id);
        assertEquals(ErrorMessages.NOT_FOUND, foreign.unavailable, "another user's shipment looks missing");
        new ShipmentFormPresenter(services, runner, alice, x -> { }, foreign).loadEdit(99_999_999L);
        assertEquals(ErrorMessages.NOT_FOUND, foreign.unavailable);

        services.getBookingService().book(alice, id, 6, "BALANCED");
        Fakes.FormView booked = new Fakes.FormView();
        ShipmentFormPresenter late = new ShipmentFormPresenter(services, runner, alice, x -> { }, booked);
        late.loadEdit(id);
        assertNotNull(booked.unavailable);
        assertNotEquals(ErrorMessages.NOT_FOUND, booked.unavailable);
        assertFalse(booked.calls.contains("form"));
        presenter.submit(form("Chennai", "Kolkata", "1", LocalDate.now().plusDays(6)));
        assertEquals(booked.unavailable, view.unavailable, "a draft booked meanwhile is refused on save too");
        assertEquals("1234.567", TestDatabase.queryString(db, "SELECT weight_kg FROM shipments WHERE shipment_id = " + id));
    }

    @Test
    void listsHistoryAndDashboardsShowOnlyTheUsersOwnData() throws Exception {
        long mine = create(alice, form("Delhi", "Mumbai", "750.5", LocalDate.now().plusDays(4)));
        long theirs = create(bob, form("Bengaluru", "Chennai", "10", LocalDate.now().plusDays(4)));
        services.getBookingService().book(alice, mine, 1461, "BALANCED");

        Fakes.ListView list = new Fakes.ListView();
        new ShipmentListPresenter(services.getShipmentDao(), runner, alice, list).load();
        List<Long> ids = list.rows.stream().map(ShipmentRow::id).toList();
        assertTrue(ids.contains(mine));
        assertFalse(ids.contains(theirs));
        ShipmentRow booked = list.rows.stream().filter(r -> r.id() == mine).findFirst().orElseThrow();
        assertEquals("Booked", booked.status());
        assertFalse(booked.editable());
        assertEquals("750.5 kg", booked.weight());

        Fakes.HistoryView history = new Fakes.HistoryView();
        new HistoryPresenter(services.getHistoryService(), runner, bob, history).load();
        assertTrue(history.model.bookings().isEmpty(), "bob sees none of alice's bookings");
        assertEquals(List.of(theirs), history.model.shipments().stream().map(ShipmentRow::id).toList());
        new HistoryPresenter(services.getHistoryService(), runner, alice, history).load();
        assertTrue(history.model.bookings().stream().anyMatch(b -> b.shipmentId() == mine
                && b.routeName().equals("Road direct") && b.status().equals("Pending payment")));

        Fakes.DashboardView dashboard = new Fakes.DashboardView();
        new DashboardPresenter(services.getDashboardService(), runner, bob, dashboard).load();
        assertEquals("1", dashboard.model.cards().get(0).value(), "bob's own shipment count");
        assertEquals("0", dashboard.model.cards().get(1).value());
        new DashboardPresenter(services.getDashboardService(), runner, admin, dashboard).load();
        assertFalse(dashboard.model.showTracking());
        assertEquals(String.valueOf(TestDatabase.queryLong(db, "SELECT COUNT(*) FROM users WHERE enabled = TRUE")),
                dashboard.model.cards().get(0).value(), "enabled users only; the demo user is disabled");

        assertTrue(services.getNotificationService().awaitIdle(Duration.ofSeconds(10)));
        Fakes.ShellView shell = new Fakes.ShellView();
        new ShellPresenter(services.getNotificationService(), runner, alice, shell).refresh();
        assertTrue(shell.unread.endsWith(" unread"), shell.unread);
        int tasks = runner.tasks;
        new ShellPresenter(services.getNotificationService(), runner, admin, shell).refresh();
        assertNull(shell.unread, "the administrator gets no notifications");
        assertEquals(tasks, runner.tasks);
    }
}

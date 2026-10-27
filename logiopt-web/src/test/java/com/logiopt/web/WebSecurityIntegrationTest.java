package com.logiopt.web;

import com.logiopt.app.AppServices;
import com.logiopt.app.SchemaGate;
import com.logiopt.dao.BookingDAO;
import com.logiopt.dao.DBConnection;
import com.logiopt.dao.DashboardDAO;
import com.logiopt.dao.MigrationRunner;
import com.logiopt.dao.NotificationDAO;
import com.logiopt.dao.PaymentDAO;
import com.logiopt.dao.ReportDAO;
import com.logiopt.dao.RouteDAO;
import com.logiopt.dao.ShipmentDAO;
import com.logiopt.dao.TestDatabase;
import com.logiopt.dao.TrackingDAO;
import com.logiopt.dao.UserDAO;
import com.logiopt.model.AdminDashboard;
import com.logiopt.model.Booking;
import com.logiopt.model.BookingStatus;
import com.logiopt.model.CustomerDashboard;
import com.logiopt.model.Milestone;
import com.logiopt.model.Notification;
import com.logiopt.model.Payment;
import com.logiopt.model.PaymentMethod;
import com.logiopt.model.Role;
import com.logiopt.model.Shipment;
import com.logiopt.model.TrackingEvent;
import com.logiopt.model.User;
import com.logiopt.report.ReportRecord;
import com.logiopt.report.ReportService;
import com.logiopt.report.ReportStatus;
import com.logiopt.service.AuthService;
import com.logiopt.service.BookingService;
import com.logiopt.service.CostService;
import com.logiopt.service.DashboardService;
import com.logiopt.service.DurationService;
import com.logiopt.service.EligibilityService;
import com.logiopt.service.EmissionService;
import com.logiopt.service.HistoryService;
import com.logiopt.service.LoginThrottle;
import com.logiopt.service.NotificationService;
import com.logiopt.service.PasswordHasher;
import com.logiopt.service.PaymentService;
import com.logiopt.service.PlanningService;
import com.logiopt.service.RecommendationService;
import com.logiopt.service.RouteEvaluator;
import com.logiopt.service.ShipmentService;
import com.logiopt.service.ShipmentValidator;
import com.logiopt.service.TrackingService;
import jakarta.servlet.Filter;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServlet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sign-in, sessions, CSRF, ownership (404 for other users' resources) and admin-only access through
 * the real filter chain (in web.xml order) and Servlets, with real services on the disposable test
 * database (LOGIOPT_TEST_DB_URL, name ending in _test). JSPs are not rendered; forwards are checked.
 */
class WebSecurityIntegrationTest {

    private static final String PASSWORD = "s3cret-pass";
    private static final String NOT_FOUND = "The page you requested does not exist.";

    @TempDir
    static Path reportsDir;

    private static DBConnection db;
    private static AppServices services;
    private static NotificationService notifications;
    private static List<Filter> filters;
    private static final Map<String, HttpServlet> SERVLETS = new HashMap<>();

    private final Map<String, WebFakes.Session> sessionStore = new HashMap<>();

    @BeforeAll
    static void setUp() throws Exception {
        db = TestDatabase.connect();
        TestDatabase.dropAllTables(db);
        MigrationRunner migrations = new MigrationRunner(db);
        migrations.migrate();
        ShipmentValidator validator = new ShipmentValidator(Clock.systemDefaultZone());
        RecommendationService recommendations = new RecommendationService();
        PlanningService planning = new PlanningService(validator, new EligibilityService(),
                new RouteEvaluator(new CostService(), new DurationService(), new EmissionService()), recommendations);
        AuthService auth = new AuthService(new UserDAO(db),
                new PasswordHasher(PasswordHasher.MIN_ITERATIONS, new SecureRandom()),
                new LoginThrottle(Clock.systemUTC(), 5, Duration.ofMinutes(5), Duration.ofMinutes(5)));
        SchemaGate gate = new SchemaGate(migrations::appliedVersion, migrations.requiredVersion(), db.getDatabaseName(),
                Clock.systemUTC(), Duration.ofSeconds(10), () -> { });
        ShipmentDAO shipmentDao = new ShipmentDAO(db);
        RouteDAO routeDao = new RouteDAO(db);
        BookingDAO bookingDao = new BookingDAO(db);
        notifications = new NotificationService(new NotificationDAO(db), new UserDAO(db), 1, 100);
        ReportService reports = new ReportService(reportsDir, Clock.systemDefaultZone(), 1, new ReportDAO(db));
        services = new AppServices(shipmentDao, routeDao, validator, recommendations, planning, reports, auth, gate,
                new BookingService(shipmentDao, routeDao, bookingDao, planning, Clock.systemDefaultZone(), notifications),
                new PaymentService(bookingDao, new PaymentDAO(db), notifications),
                new TrackingService(bookingDao, new TrackingDAO(db), notifications), notifications,
                new ShipmentService(shipmentDao, validator), new DashboardService(new DashboardDAO(db), notifications),
                new HistoryService(shipmentDao, bookingDao, reports));
        ServletContext context = WebFakes.context(services);
        filters = List.of(WebFakes.init(new SchemaGateFilter(), context), WebFakes.init(new AuthFilter(), context),
                WebFakes.init(new CsrfFilter(), context));
        SERVLETS.put("/login", WebFakes.init(new LoginServlet(), context));
        SERVLETS.put("/register", WebFakes.init(new RegisterServlet(), context));
        SERVLETS.put("/logout", WebFakes.init(new LogoutServlet(), context));
        SERVLETS.put("/shipments", WebFakes.init(new ShipmentListServlet(), context));
        SERVLETS.put("/shipments/new", WebFakes.init(new ShipmentFormServlet(), context));
        SERVLETS.put("/shipments/create", WebFakes.init(new ShipmentCreateServlet(), context));
        SERVLETS.put("/compare", WebFakes.init(new ComparisonServlet(), context));
        SERVLETS.put("/reports", WebFakes.init(new ReportServlet(), context));
        SERVLETS.put("/admin/users", WebFakes.init(new AdminUsersServlet(), context));
        SERVLETS.put("/bookings", WebFakes.init(new BookingServlet(), context));
        SERVLETS.put("/bookings/view", WebFakes.init(new BookingViewServlet(), context));
        SERVLETS.put("/bookings/cancel", WebFakes.init(new BookingCancelServlet(), context));
        SERVLETS.put("/payments", WebFakes.init(new PaymentServlet(), context));
        SERVLETS.put("/admin/bookings", WebFakes.init(new AdminBookingsServlet(), context));
        SERVLETS.put("/admin/tracking", WebFakes.init(new AdminTrackingServlet(), context));
        SERVLETS.put("/notifications", WebFakes.init(new NotificationServlet(), context));
        SERVLETS.put("/dashboard", WebFakes.init(new DashboardServlet(), context));
        SERVLETS.put("/admin/dashboard", WebFakes.init(new AdminDashboardServlet(), context));
        SERVLETS.put("/history", WebFakes.init(new HistoryServlet(), context));
        SERVLETS.put("/shipments/edit", WebFakes.init(new ShipmentEditServlet(), context));
        SERVLETS.put("/reports/download", WebFakes.init(new ReportDownloadServlet(), context));
    }

    @AfterAll
    static void stop() {
        if (services != null) {
            services.getNotificationService().shutdown();
            services.getReportService().shutdown();
        }
    }

    @BeforeEach
    void cleanUsers() throws Exception {
        if (notifications != null) {
            assertTrue(notifications.awaitIdle(Duration.ofSeconds(10)));
        }
        TestDatabase.execute(db, "DELETE FROM reports", "DELETE FROM notifications", "DELETE FROM tracking",
                "DELETE FROM payments",
                "DELETE FROM booking_legs", "DELETE FROM bookings",
                "DELETE FROM shipments WHERE user_id <> 1", "DELETE FROM users WHERE user_id <> 1");
    }

    /** Sends a request from the browser through the filter chain to the mapped Servlet. */
    private static WebFakes.Request send(WebFakes.Browser browser, String method, String path, String... params)
            throws Exception {
        WebFakes.Request r = browser.request(method, path);
        for (int i = 0; i < params.length; i += 2) {
            r.param(params[i], params[i + 1]);
        }
        WebFakes.run(r, filters, SERVLETS.get(path));
        browser.afterResponse(r);
        return r;
    }

    private static String token(WebFakes.Browser browser) {
        return (String) browser.session().attributes.get(CsrfFilter.SESSION_TOKEN);
    }

    private WebFakes.Browser browser() {
        return new WebFakes.Browser(sessionStore);
    }

    /** Registers through the services and signs in through the Servlet; returns the browser. */
    private WebFakes.Browser signedIn(String email) throws Exception {
        services.getAuthService().register(email, email.substring(0, email.indexOf('@')), PASSWORD.toCharArray());
        WebFakes.Browser b = browser();
        send(b, "GET", "/login");
        WebFakes.Request r = send(b, "POST", "/login", "csrfToken", token(b), "email", email, "password", PASSWORD);
        assertEquals("/logiopt/dashboard", r.response.redirect, "sign-in of " + email);
        return b;
    }

    private static User user(WebFakes.Browser b) {
        return (User) b.session().attributes.get(WebSupport.SESSION_USER);
    }

    private static long createShipment(WebFakes.Browser b, String... extra) throws Exception {
        String[] base = {"csrfToken", token(b), "source", "Mumbai", "destination", "Delhi", "cargoType", "GENERAL",
                "weightKg", "5000", "dispatchDate", LocalDate.now().plusDays(7).toString()};
        String[] params = new String[base.length + extra.length];
        System.arraycopy(base, 0, params, 0, base.length);
        System.arraycopy(extra, 0, params, base.length, extra.length);
        WebFakes.Request r = send(b, "POST", "/shipments/create", params);
        String location = r.response.redirect;
        assertNotNull(location, "create failed: " + r.attributes.get("errors"));
        return Long.parseLong(location.replaceAll(".*shipmentId=(\\d+).*", "$1"));
    }

    @Test
    void registrationSignInSessionRotationAndSignOut() throws Exception {
        WebFakes.Browser b = browser();
        WebFakes.Request form = send(b, "GET", "/register");
        assertEquals("/WEB-INF/jsp/register.jsp", form.response.forwardedTo);
        WebFakes.Request created = send(b, "POST", "/register", "csrfToken", token(b), "email", "gina@example.test",
                "name", "Gina", "password", PASSWORD);
        WebFakes.Request duplicate = send(b, "POST", "/register", "csrfToken", token(b), "email", "GINA@example.test",
                "name", "Someone else", "password", "another-pass");
        assertEquals("/logiopt/login?registered=1", created.response.redirect);
        assertEquals(created.response.redirect, duplicate.response.redirect, "duplicate looks exactly like success");
        assertEquals(created.response.status, duplicate.response.status);

        String anonymousSessionId = b.cookie;
        String anonymousToken = token(b);
        WebFakes.Request login = send(b, "POST", "/login", "csrfToken", anonymousToken,
                "email", "gina@example.test", "password", PASSWORD);
        assertEquals("/logiopt/dashboard", login.response.redirect, "the dashboard is the landing page");
        assertNotEquals(anonymousSessionId, b.cookie, "session id rotated on sign-in");
        assertEquals("gina@example.test", user(b).getEmail());
        assertNotEquals(anonymousToken, token(b), "CSRF token rotated on sign-in");

        WebFakes.Request list = send(b, "GET", "/shipments");
        assertEquals("/WEB-INF/jsp/shipment-list.jsp", list.response.forwardedTo);

        WebFakes.Request forged = send(b, "POST", "/logout");
        assertEquals(403, forged.response.status);
        assertNotNull(b.session(), "a logout without token does nothing");

        WebFakes.Request logout = send(b, "POST", "/logout", "csrfToken", token(b));
        assertEquals("/logiopt/login?loggedOut=1", logout.response.redirect);
        assertNull(b.session());
        WebFakes.Request after = send(b, "GET", "/shipments");
        assertEquals("/logiopt/login?expired=1", after.response.redirect, "old session id no longer works");
        assertNull(after.response.forwardedTo);
    }

    @Test
    void failedSignInIsGenericThrottledAndNeedsCsrf() throws Exception {
        services.getAuthService().register("hank@example.test", "Hank", PASSWORD.toCharArray());
        WebFakes.Browser b = browser();
        send(b, "GET", "/login");
        WebFakes.Request wrong = send(b, "POST", "/login", "csrfToken", token(b), "email", "hank@example.test",
                "password", "wrong-password");
        WebFakes.Request unknown = send(b, "POST", "/login", "csrfToken", token(b), "email", "nobody@example.test",
                "password", PASSWORD);
        assertEquals(401, wrong.response.status);
        assertEquals(401, unknown.response.status);
        assertEquals(LoginServlet.INVALID_MESSAGE, wrong.attributes.get("error"));
        assertEquals(wrong.attributes.get("error"), unknown.attributes.get("error"));
        assertNull(b.session().attributes.get(WebSupport.SESSION_USER));

        WebFakes.Request noToken = send(b, "POST", "/login", "email", "hank@example.test", "password", PASSWORD);
        assertEquals(403, noToken.response.status);
        assertNull(b.session().attributes.get(WebSupport.SESSION_USER));

        for (int i = 0; i < 4; i++) {
            send(b, "POST", "/login", "csrfToken", token(b), "email", "hank@example.test", "password", "wrong-password");
        }
        WebFakes.Request locked = send(b, "POST", "/login", "csrfToken", token(b), "email", "hank@example.test",
                "password", PASSWORD);
        assertEquals(429, locked.response.status);
        assertEquals(LoginServlet.LOCKED_MESSAGE, locked.attributes.get("error"));
        assertNull(b.session().attributes.get(WebSupport.SESSION_USER));
    }

    @Test
    void shipmentsAndReportsOfOtherUsersAre404() throws Exception {
        WebFakes.Browser alice = signedIn("alice@example.test");
        WebFakes.Browser bob = signedIn("bob@example.test");
        long bobId = user(bob).getUserId();

        long shipmentId = createShipment(alice, "userId", String.valueOf(bobId));
        Shipment saved = services.getShipmentDao().findByIdForUser(shipmentId, user(alice).getUserId()).orElseThrow();
        assertEquals(user(alice).getUserId(), saved.getUserId(), "owner comes from the session, not the userId field");
        assertTrue(services.getShipmentDao().findByIdForUser(shipmentId, bobId).isEmpty());

        WebFakes.Request own = send(alice, "GET", "/compare", "shipmentId", String.valueOf(shipmentId), "strategy", "BALANCED");
        assertEquals("/WEB-INF/jsp/comparison.jsp", own.response.forwardedTo);

        WebFakes.Request foreign = send(bob, "GET", "/compare", "shipmentId", String.valueOf(shipmentId), "strategy", "BALANCED");
        WebFakes.Request missing = send(bob, "GET", "/compare", "shipmentId", "999999999", "strategy", "BALANCED");
        assertEquals(404, foreign.response.status);
        assertEquals(NOT_FOUND, foreign.attributes.get("errorMessage"));
        assertEquals(missing.response.status, foreign.response.status, "foreign and missing look the same");
        assertEquals(missing.attributes.get("errorMessage"), foreign.attributes.get("errorMessage"));

        WebFakes.Request foreignReport = send(bob, "POST", "/reports", "csrfToken", token(bob),
                "shipmentId", String.valueOf(shipmentId), "strategy", "BALANCED");
        assertEquals(404, foreignReport.response.status);

        WebFakes.Request ownReport = send(alice, "POST", "/reports", "csrfToken", token(alice),
                "shipmentId", String.valueOf(shipmentId), "strategy", "BALANCED");
        String jobId = ownReport.response.redirect.replaceAll(".*jobId=", "");
        assertTrue(services.getReportService().awaitFinished(jobId, Duration.ofSeconds(10)));
        assertTrue(services.getReportService().getJob(jobId, user(alice).getUserId()).isPresent());
        assertEquals("/WEB-INF/jsp/report-status.jsp", send(alice, "GET", "/reports", "jobId", jobId).response.forwardedTo);
        WebFakes.Request foreignJob = send(bob, "GET", "/reports", "jobId", jobId);
        assertEquals(404, foreignJob.response.status);
        assertEquals(NOT_FOUND, foreignJob.attributes.get("errorMessage"));

        @SuppressWarnings("unchecked")
        List<Shipment> bobsList = (List<Shipment>) send(bob, "GET", "/shipments").attributes.get("shipments");
        assertTrue(bobsList.isEmpty());
        @SuppressWarnings("unchecked")
        List<Shipment> alicesList = (List<Shipment>) send(alice, "GET", "/shipments").attributes.get("shipments");
        assertEquals(1, alicesList.size());
    }

    @Test
    void demoUserShipmentsAreNotVisibleToSignedInUsers() throws Exception {
        TestDatabase.execute(db, "INSERT INTO shipments (user_id, source, destination, cargo_type, weight_kg, "
                + "dispatch_date, status) VALUES (1, 'Mumbai', 'Delhi', 'GENERAL', 5, '2026-01-01', 'DRAFT')");
        long demoShipment = TestDatabase.queryLong(db, "SELECT MAX(shipment_id) FROM shipments WHERE user_id = 1");
        WebFakes.Browser carol = signedIn("carol@example.test");
        assertEquals(404, send(carol, "GET", "/compare", "shipmentId", String.valueOf(demoShipment),
                "strategy", "BALANCED").response.status);
    }

    @Test
    void adminPagesAreOnlyForTheAdministrator() throws Exception {
        WebFakes.Browser anonymous = browser();
        assertEquals("/logiopt/login", send(anonymous, "GET", "/admin/users").response.redirect);

        WebFakes.Browser customer = signedIn("dora@example.test");
        WebFakes.Request denied = send(customer, "GET", "/admin/users");
        assertEquals(404, denied.response.status);
        assertNull(denied.attributes.get("users"), "the admin Servlet did not run");

        services.getAuthService().seedAdmin("admin-pass-1");
        WebFakes.Browser admin = browser();
        send(admin, "GET", "/login");
        send(admin, "POST", "/login", "csrfToken", token(admin), "email", "admin@logiopt.local", "password", "admin-pass-1");
        WebFakes.Request allowed = send(admin, "GET", "/admin/users");
        assertEquals("/WEB-INF/jsp/admin-users.jsp", allowed.response.forwardedTo);
        @SuppressWarnings("unchecked")
        List<User> listed = (List<User>) allowed.attributes.get("users");
        assertTrue(listed.stream().anyMatch(User::isAdmin));
        assertTrue(listed.stream().anyMatch(u -> u.getEmail().equals("dora@example.test")));
    }

    @Test
    void protectedPagesNeedSignIn() throws Exception {
        WebFakes.Browser anonymous = browser();
        for (String path : List.of("/shipments", "/shipments/new", "/compare", "/reports")) {
            WebFakes.Request r = send(anonymous, "GET", path);
            assertTrue(r.response.redirect.startsWith("/logiopt/login"), path);
            assertNull(r.response.forwardedTo, path);
        }
        WebFakes.Request post = send(anonymous, "POST", "/shipments/create", "source", "Mumbai");
        assertTrue(post.response.redirect.startsWith("/logiopt/login"));
        assertFalse(TestDatabase.queryLong(db, "SELECT COUNT(*) FROM shipments WHERE user_id <> 1") > 0);
        assertTrue(send(anonymous, "POST", "/shipments/edit", "shipmentId", "1", "source", "Mumbai")
                .response.redirect.startsWith("/logiopt/login"));
        for (String path : List.of("/bookings", "/bookings/view", "/payments", "/admin/bookings", "/dashboard",
                "/admin/dashboard", "/history", "/shipments/edit", "/reports/download")) {
            assertTrue(send(anonymous, "GET", path).response.redirect.startsWith("/logiopt/login"), path);
        }
    }

    private static long book(WebFakes.Browser b, long shipmentId, String routeId, String... extra) throws Exception {
        String[] base = {"csrfToken", token(b), "shipmentId", String.valueOf(shipmentId), "routeId", routeId,
                "strategy", "BALANCED"};
        String[] params = new String[base.length + extra.length];
        System.arraycopy(base, 0, params, 0, base.length);
        System.arraycopy(extra, 0, params, base.length, extra.length);
        WebFakes.Request r = send(b, "POST", "/bookings", params);
        assertNotNull(r.response.redirect, "booking failed: " + r.attributes.get("errorMessage"));
        return Long.parseLong(r.response.redirect.replaceAll(".*id=(\\d+).*", "$1"));
    }

    private WebFakes.Browser signedInAdmin() throws Exception {
        services.getAuthService().seedAdmin("admin-pass-1");
        WebFakes.Browser admin = browser();
        send(admin, "GET", "/login");
        send(admin, "POST", "/login", "csrfToken", token(admin), "email", "admin@logiopt.local", "password", "admin-pass-1");
        assertTrue(user(admin).isAdmin());
        return admin;
    }

    @Test
    void bookPayFailRetryAndConfirmThroughTheWebLayer() throws Exception {
        WebFakes.Browser alice = signedIn("alice@example.test");
        long shipmentId = createShipment(alice);
        WebFakes.Request compare = send(alice, "GET", "/compare", "shipmentId", String.valueOf(shipmentId), "strategy", "BALANCED");
        assertEquals(Boolean.TRUE, compare.attributes.get("dateBookable"));
        assertNull(compare.attributes.get("activeBooking"));

        long bookingId = book(alice, shipmentId, "2", "totalCost", "1.00", "routeName", "Forged", "transportCost", "0");
        WebFakes.Request view = send(alice, "GET", "/bookings/view", "id", String.valueOf(bookingId));
        assertEquals("/WEB-INF/jsp/booking.jsp", view.response.forwardedTo);
        Booking booking = (Booking) view.attributes.get("booking");
        assertEquals("Rail direct", booking.getSnapshot().getRouteName(), "posted route name ignored");
        assertEquals(new BigDecimal("22800.00"), booking.getSnapshot().getTotalCost(), "posted price ignored");
        assertEquals(Boolean.TRUE, view.attributes.get("canAct"));

        WebFakes.Request again = send(alice, "POST", "/bookings", "csrfToken", token(alice),
                "shipmentId", String.valueOf(shipmentId), "routeId", "1", "strategy", "BALANCED");
        assertEquals(409, again.response.status);
        assertEquals("This shipment already has an active booking.", again.attributes.get("errorMessage"));
        assertNotNull(send(alice, "GET", "/compare", "shipmentId", String.valueOf(shipmentId), "strategy", "BALANCED")
                .attributes.get("activeBooking"));

        WebFakes.Request payPage = send(alice, "GET", "/payments", "bookingId", String.valueOf(bookingId));
        assertEquals("/WEB-INF/jsp/payment.jsp", payPage.response.forwardedTo);
        assertEquals(List.of(PaymentMethod.values()), payPage.attributes.get("methods"));

        WebFakes.Request failed = send(alice, "POST", "/payments", "csrfToken", token(alice),
                "bookingId", String.valueOf(bookingId), "method", "UPI", "outcome", "FAILURE");
        assertEquals("/logiopt/bookings/view?id=" + bookingId + "&payment=failed", failed.response.redirect);
        WebFakes.Request paid = send(alice, "POST", "/payments", "csrfToken", token(alice),
                "bookingId", String.valueOf(bookingId), "method", "CARD", "outcome", "SUCCESS");
        assertEquals("/logiopt/bookings/view?id=" + bookingId + "&payment=paid", paid.response.redirect);

        WebFakes.Request confirmed = send(alice, "GET", "/bookings/view", "id", String.valueOf(bookingId));
        assertEquals(BookingStatus.CONFIRMED,
                ((Booking) confirmed.attributes.get("booking")).getStatus());
        assertEquals(Boolean.FALSE, confirmed.attributes.get("canAct"));
        @SuppressWarnings("unchecked")
        List<Payment> attempts = (List<Payment>) confirmed.attributes.get("payments");
        assertEquals(2, attempts.size());

        WebFakes.Request cancelPaid = send(alice, "POST", "/bookings/cancel", "csrfToken", token(alice),
                "id", String.valueOf(bookingId));
        assertEquals(409, cancelPaid.response.status);
        assertEquals("Paid bookings cannot be cancelled.", cancelPaid.attributes.get("errorMessage"));
        assertEquals(409, send(alice, "GET", "/payments", "bookingId", String.valueOf(bookingId)).response.status);
        assertEquals(409, send(alice, "POST", "/payments", "csrfToken", token(alice), "bookingId",
                String.valueOf(bookingId), "method", "UPI", "outcome", "SUCCESS").response.status);
    }

    @Test
    void bookingsAndPaymentsOfOtherUsersAre404AndAdminIsReadOnly() throws Exception {
        WebFakes.Browser alice = signedIn("alice@example.test");
        WebFakes.Browser bob = signedIn("bob@example.test");
        long bookingId = book(alice, createShipment(alice), "2");
        String id = String.valueOf(bookingId);

        for (WebFakes.Request r : List.of(
                send(bob, "GET", "/bookings/view", "id", id),
                send(bob, "GET", "/bookings/view", "id", "999999999"),
                send(bob, "GET", "/payments", "bookingId", id),
                send(bob, "POST", "/payments", "csrfToken", token(bob), "bookingId", id, "method", "UPI", "outcome", "SUCCESS"),
                send(bob, "POST", "/bookings/cancel", "csrfToken", token(bob), "id", id))) {
            assertEquals(404, r.response.status);
            assertEquals(NOT_FOUND, r.attributes.get("errorMessage"));
        }
        @SuppressWarnings("unchecked")
        List<Booking> bobs = (List<Booking>) send(bob, "GET", "/bookings")
                .attributes.get("bookings");
        assertTrue(bobs.isEmpty());

        WebFakes.Browser admin = signedInAdmin();
        WebFakes.Request adminView = send(admin, "GET", "/bookings/view", "id", id);
        assertEquals("/WEB-INF/jsp/booking.jsp", adminView.response.forwardedTo);
        assertEquals(Boolean.TRUE, adminView.attributes.get("readOnlyAdmin"));
        assertEquals(Boolean.FALSE, adminView.attributes.get("canAct"));
        assertEquals(404, send(admin, "GET", "/payments", "bookingId", id).response.status);
        assertEquals(404, send(admin, "POST", "/payments", "csrfToken", token(admin), "bookingId", id,
                "method", "UPI", "outcome", "SUCCESS").response.status);
        assertEquals(404, send(admin, "POST", "/bookings/cancel", "csrfToken", token(admin), "id", id).response.status);
        assertEquals("/WEB-INF/jsp/admin-bookings.jsp", send(admin, "GET", "/admin/bookings").response.forwardedTo);
        assertEquals(404, send(bob, "GET", "/admin/bookings").response.status);
        assertEquals(0, TestDatabase.queryLong(db, "SELECT COUNT(*) FROM payments"), "nobody else could pay");
        assertEquals("PENDING_PAYMENT", TestDatabase.queryString(db, "SELECT status FROM bookings WHERE booking_id = " + id));
    }

    @Test
    void bookingFormsNeedTheCsrfTokenAndValidInput() throws Exception {
        WebFakes.Browser alice = signedIn("alice@example.test");
        long shipmentId = createShipment(alice);
        assertEquals(403, send(alice, "POST", "/bookings", "shipmentId", String.valueOf(shipmentId), "routeId", "2",
                "strategy", "BALANCED").response.status);
        assertEquals(0, TestDatabase.queryLong(db, "SELECT COUNT(*) FROM bookings"));
        assertEquals(400, send(alice, "POST", "/bookings", "csrfToken", token(alice), "shipmentId", "x", "routeId", "2")
                .response.status);
        WebFakes.Request notEligible = send(alice, "POST", "/bookings", "csrfToken", token(alice),
                "shipmentId", String.valueOf(shipmentId), "routeId", "5", "strategy", "BALANCED");
        assertEquals(409, notEligible.response.status, "route 5 serves Chennai -> Kolkata");
        long bookingId = book(alice, shipmentId, "2");
        assertEquals(403, send(alice, "POST", "/bookings/cancel", "id", String.valueOf(bookingId)).response.status);
        assertEquals(403, send(alice, "POST", "/payments", "bookingId", String.valueOf(bookingId), "method", "UPI",
                "outcome", "SUCCESS").response.status);
        assertEquals(400, send(alice, "POST", "/payments", "csrfToken", token(alice), "bookingId",
                String.valueOf(bookingId), "method", "UPI", "outcome", "MAYBE").response.status);
        assertEquals(409, send(alice, "POST", "/payments", "csrfToken", token(alice), "bookingId",
                String.valueOf(bookingId), "method", "BITCOIN", "outcome", "SUCCESS").response.status);
        WebFakes.Request cancelled = send(alice, "POST", "/bookings/cancel", "csrfToken", token(alice),
                "id", String.valueOf(bookingId));
        assertEquals("/logiopt/bookings/view?id=" + bookingId + "&cancelled=1", cancelled.response.redirect);
        assertEquals("DRAFT", TestDatabase.queryString(db, "SELECT status FROM shipments WHERE shipment_id = " + shipmentId));
        book(alice, shipmentId, "1");
    }

    private long paidBooking(WebFakes.Browser customer) throws Exception {
        long bookingId = book(customer, createShipment(customer), "2");
        send(customer, "POST", "/payments", "csrfToken", token(customer), "bookingId", String.valueOf(bookingId),
                "method", "UPI", "outcome", "SUCCESS");
        return bookingId;
    }

    @Test
    void adminTrackingIsAdminOnlyCsrfProtectedAndIdempotent() throws Exception {
        WebFakes.Browser alice = signedIn("alice@example.test");
        long bookingId = paidBooking(alice);
        String id = String.valueOf(bookingId);

        assertEquals("/logiopt/login", send(browser(), "GET", "/admin/tracking").response.redirect);
        assertEquals(404, send(alice, "GET", "/admin/tracking").response.status);
        assertEquals(404, send(alice, "POST", "/admin/tracking", "csrfToken", token(alice), "action", "advance",
                "bookingId", id, "current", "BOOKED").response.status);

        WebFakes.Browser admin = signedInAdmin();
        WebFakes.Request page = send(admin, "GET", "/admin/tracking");
        assertEquals("/WEB-INF/jsp/admin-tracking.jsp", page.response.forwardedTo);
        @SuppressWarnings("unchecked")
        List<TrackingRow> rows = (List<TrackingRow>) page.attributes.get("rows");
        assertEquals(1, rows.size());
        assertEquals(Milestone.BOOKED, rows.get(0).getCurrent());
        assertEquals(Milestone.PICKED_UP, rows.get(0).getNext(), "the form offers only the legal next milestone");

        assertEquals(403, send(admin, "POST", "/admin/tracking", "action", "advance", "bookingId", id,
                "current", "BOOKED").response.status);
        WebFakes.Request skip = send(admin, "POST", "/admin/tracking", "csrfToken", token(admin), "action", "record",
                "bookingId", id, "current", "BOOKED", "milestone", "IN_TRANSIT", "description", "skipping");
        assertEquals(409, skip.response.status);
        String[] record = {"csrfToken", token(admin), "action", "record", "bookingId", id, "current", "BOOKED",
                "milestone", "PICKED_UP", "description", "Collected at the depot"};
        assertEquals("/logiopt/admin/tracking?result=recorded", send(admin, "POST", "/admin/tracking", record).response.redirect);
        assertEquals("/logiopt/admin/tracking?result=unchanged", send(admin, "POST", "/admin/tracking", record).response.redirect,
                "a double submission records nothing twice");
        assertEquals("/logiopt/admin/tracking?result=recorded", send(admin, "POST", "/admin/tracking", "csrfToken",
                token(admin), "action", "advance", "bookingId", id, "current", "PICKED_UP").response.redirect);
        assertEquals(3, TestDatabase.queryLong(db, "SELECT COUNT(*) FROM tracking WHERE booking_id = " + id));
        assertEquals(404, send(admin, "POST", "/admin/tracking", "csrfToken", token(admin), "action", "advance",
                "bookingId", "999999999", "current", "BOOKED").response.status);
    }

    @Test
    void customersSeeTheTimelineOfTheirOwnBookingsOnly() throws Exception {
        WebFakes.Browser alice = signedIn("alice@example.test");
        WebFakes.Browser bob = signedIn("bob@example.test");
        long bookingId = paidBooking(alice);
        services.getTrackingService().advance(bookingId, Milestone.BOOKED);
        WebFakes.Request view = send(alice, "GET", "/bookings/view", "id", String.valueOf(bookingId));
        @SuppressWarnings("unchecked")
        List<TrackingEvent> timeline = (List<TrackingEvent>) view.attributes.get("timeline");
        assertEquals(List.of(Milestone.BOOKED, Milestone.PICKED_UP),
                List.of(timeline.get(0).getMilestone(), timeline.get(1).getMilestone()));
        assertEquals(Milestone.IN_TRANSIT, view.attributes.get("nextMilestone"));
        WebFakes.Request foreign = send(bob, "GET", "/bookings/view", "id", String.valueOf(bookingId));
        assertEquals(404, foreign.response.status);
        assertNull(foreign.attributes.get("timeline"));
    }

    @Test
    void notificationsAreOwnerOnlyAndCanBeMarkedRead() throws Exception {
        WebFakes.Browser alice = signedIn("alice@example.test");
        WebFakes.Browser bob = signedIn("bob@example.test");
        paidBooking(alice);
        assertTrue(notifications.awaitIdle(Duration.ofSeconds(10)));

        WebFakes.Request list = send(alice, "GET", "/notifications");
        assertEquals("/WEB-INF/jsp/notifications.jsp", list.response.forwardedTo);
        @SuppressWarnings("unchecked")
        List<Notification> mine = (List<Notification>) list.attributes.get("notifications");
        assertEquals(3, mine.size(), "booking created, payment succeeded, tracking BOOKED");
        assertEquals(3, list.attributes.get(AuthFilter.UNREAD_COUNT));
        @SuppressWarnings("unchecked")
        List<Notification> bobs = (List<Notification>) send(bob, "GET", "/notifications").attributes.get("notifications");
        assertTrue(bobs.isEmpty());

        String aliceId = String.valueOf(mine.get(0).getNotificationId());
        assertEquals(404, send(bob, "POST", "/notifications", "csrfToken", token(bob), "action", "read",
                "id", aliceId).response.status);
        assertEquals(403, send(alice, "POST", "/notifications", "action", "read", "id", aliceId).response.status);
        assertEquals("/logiopt/notifications", send(alice, "POST", "/notifications", "csrfToken", token(alice),
                "action", "read", "id", aliceId).response.redirect);
        assertEquals(2, send(alice, "GET", "/shipments").attributes.get(AuthFilter.UNREAD_COUNT));
        send(bob, "POST", "/notifications", "csrfToken", token(bob), "action", "readAll");
        assertEquals(2, send(alice, "GET", "/shipments").attributes.get(AuthFilter.UNREAD_COUNT), "bob's readAll is his own");
        send(alice, "POST", "/notifications", "csrfToken", token(alice), "action", "readAll");
        assertEquals(0, send(alice, "GET", "/shipments").attributes.get(AuthFilter.UNREAD_COUNT));

        WebFakes.Browser admin = signedInAdmin();
        assertNull(send(admin, "GET", "/shipments").attributes.get(AuthFilter.UNREAD_COUNT), "admin gets no notifications");
        assertEquals("/logiopt/login", send(browser(), "GET", "/notifications").response.redirect);
    }

    @Test
    void pastDispatchDateCannotBeBookedButCanStillBeCompared() throws Exception {
        WebFakes.Browser alice = signedIn("alice@example.test");
        TestDatabase.execute(db, "INSERT INTO shipments (user_id, source, destination, cargo_type, weight_kg, dispatch_date, "
                + "status) VALUES (" + user(alice).getUserId() + ", 'Mumbai', 'Delhi', 'GENERAL', 5000, '2025-01-15', 'DRAFT')");
        long past = TestDatabase.queryLong(db, "SELECT MAX(shipment_id) FROM shipments");
        WebFakes.Request compare = send(alice, "GET", "/compare", "shipmentId", String.valueOf(past), "strategy", "BALANCED");
        assertEquals("/WEB-INF/jsp/comparison.jsp", compare.response.forwardedTo);
        assertEquals(Boolean.FALSE, compare.attributes.get("dateBookable"));
        WebFakes.Request refused = send(alice, "POST", "/bookings", "csrfToken", token(alice),
                "shipmentId", String.valueOf(past), "routeId", "2", "strategy", "BALANCED");
        assertEquals(409, refused.response.status);
        assertTrue(((String) refused.attributes.get("errorMessage")).startsWith("Booking needs a dispatch date of today or later"));
    }

    @Test
    void dashboardIsTheLandingPageWithOwnCountsAndTheAdminSeesSystemCounts() throws Exception {
        WebFakes.Browser alice = signedIn("alice@example.test");
        WebFakes.Browser bob = signedIn("bob@example.test");
        createShipment(alice);
        createShipment(alice);
        paidBooking(alice);
        assertTrue(notifications.awaitIdle(Duration.ofSeconds(10)));

        WebFakes.Request mine = send(alice, "GET", "/dashboard");
        assertEquals("/WEB-INF/jsp/dashboard.jsp", mine.response.forwardedTo);
        CustomerDashboard a = (CustomerDashboard) mine.attributes.get("dashboard");
        assertEquals(3, a.getShipmentTotal());
        assertEquals(1, a.getBookingsByStatus().get(BookingStatus.CONFIRMED));
        assertEquals(3, a.getUnreadNotifications());
        assertEquals(1, a.getTracking().size());
        assertEquals(Milestone.BOOKED, a.getTracking().get(0).getLatest());
        CustomerDashboard b = (CustomerDashboard) send(bob, "GET", "/dashboard").attributes.get("dashboard");
        assertEquals(0, b.getShipmentTotal(), "bob's dashboard has none of alice's rows");
        assertEquals(0, b.getBookingTotal());
        assertTrue(b.getTracking().isEmpty());

        assertEquals(404, send(alice, "GET", "/admin/dashboard").response.status);
        assertNull(send(bob, "GET", "/admin/dashboard").attributes.get("dashboard"), "the admin Servlet did not run");
        WebFakes.Browser admin = signedInAdmin();
        assertEquals("/logiopt/admin/dashboard", send(admin, "GET", "/dashboard").response.redirect);
        WebFakes.Request system = send(admin, "GET", "/admin/dashboard");
        assertEquals("/WEB-INF/jsp/admin-dashboard.jsp", system.response.forwardedTo);
        AdminDashboard d = (AdminDashboard) system.attributes.get("dashboard");
        assertEquals(TestDatabase.queryLong(db, "SELECT COUNT(*) FROM users WHERE enabled = TRUE"), d.getUserTotal());
        assertEquals(TestDatabase.queryLong(db, "SELECT COUNT(*) FROM users") - 1, d.getUserTotal(), "not the demo user");
        assertEquals(1, d.getUsersByRole().get(Role.ADMIN));
        assertEquals(1, d.getBookingTotal());
        assertEquals(1, d.getConfirmedAwaitingTracking());
    }

    @Test
    void historyShowsOnlyTheSignedInUsersEntries() throws Exception {
        WebFakes.Browser alice = signedIn("alice@example.test");
        WebFakes.Browser bob = signedIn("bob@example.test");
        long shipmentId = createShipment(alice);
        book(alice, shipmentId, "2");
        String jobId = report(alice, shipmentId);

        WebFakes.Request mine = send(alice, "GET", "/history");
        assertEquals("/WEB-INF/jsp/history.jsp", mine.response.forwardedTo);
        HistoryService.History a = (HistoryService.History) mine.attributes.get("history");
        assertEquals(List.of(shipmentId), a.getShipments().stream().map(Shipment::getShipmentId).toList());
        assertEquals(1, a.getBookings().size());
        assertEquals(jobId, a.getReports().get(0).getJobId());
        assertEquals(HistoryService.LIMIT, a.getLimit());
        HistoryService.History b = (HistoryService.History) send(bob, "GET", "/history").attributes.get("history");
        assertTrue(b.getShipments().isEmpty() && b.getBookings().isEmpty() && b.getReports().isEmpty());
    }

    @Test
    void draftEditIsOwnerOnlyCsrfProtectedAndRefusedOnceBooked() throws Exception {
        WebFakes.Browser alice = signedIn("alice@example.test");
        WebFakes.Browser bob = signedIn("bob@example.test");
        long shipmentId = createShipment(alice);
        String id = String.valueOf(shipmentId);
        String before = row(shipmentId);

        WebFakes.Request form = send(alice, "GET", "/shipments/edit", "shipmentId", id);
        assertEquals("/WEB-INF/jsp/shipment-form.jsp", form.response.forwardedTo);
        assertEquals(shipmentId, form.attributes.get("editShipmentId"));
        @SuppressWarnings("unchecked")
        Map<String, String> values = (Map<String, String>) form.attributes.get("form");
        assertEquals("5000", values.get("weightKg"));
        assertEquals("Mumbai", values.get("source"));
        assertNotNull(form.attributes.get("cities"), "cities come from the known routes, for the selects");

        String date = LocalDate.now().plusDays(9).toString();
        String[] edit = {"csrfToken", token(alice), "shipmentId", id, "source", "Mumbai", "destination", "Delhi",
            "cargoType", "FRAGILE", "weightKg", "1234.5", "dispatchDate", date, "userId", String.valueOf(user(bob).getUserId())};
        for (WebFakes.Request r : List.of(send(bob, "GET", "/shipments/edit", "shipmentId", id),
                send(bob, "POST", "/shipments/edit", "csrfToken", token(bob), "shipmentId", id, "source", "Mumbai",
                        "destination", "Delhi", "cargoType", "FRAGILE", "weightKg", "1", "dispatchDate", date),
                send(alice, "GET", "/shipments/edit", "shipmentId", "999999999"),
                send(alice, "GET", "/shipments/edit", "shipmentId", "x"))) {
            assertEquals(404, r.response.status);
            assertEquals(NOT_FOUND, r.attributes.get("errorMessage"));
        }
        assertEquals(403, send(alice, "POST", "/shipments/edit", "shipmentId", id, "weightKg", "1").response.status);
        WebFakes.Request invalid = send(alice, "POST", "/shipments/edit", "csrfToken", token(alice), "shipmentId", id,
                "source", "Atlantis", "destination", "Delhi", "cargoType", "GENERAL", "weightKg", "1.2345",
                "dispatchDate", "2020-01-01");
        assertEquals(400, invalid.response.status);
        @SuppressWarnings("unchecked")
        List<String> errors = (List<String>) invalid.attributes.get("errors");
        assertTrue(errors.size() >= 3, errors.toString());
        assertEquals(shipmentId, invalid.attributes.get("editShipmentId"));
        assertEquals(before, row(shipmentId), "nothing changed by refused edits");

        WebFakes.Request saved = send(alice, "POST", "/shipments/edit", edit);
        assertEquals("/logiopt/compare?shipmentId=" + id + "&strategy=BALANCED", saved.response.redirect);
        Shipment edited = services.getShipmentDao().findByIdForUser(shipmentId, user(alice).getUserId()).orElseThrow();
        assertEquals(1234.5, edited.getWeightKg());
        assertEquals(user(alice).getUserId(), edited.getUserId(), "the owner never comes from the form");

        long bookingId = book(alice, shipmentId, "2");
        WebFakes.Request locked = send(alice, "GET", "/shipments/edit", "shipmentId", id);
        assertEquals(409, locked.response.status);
        assertTrue(((String) locked.attributes.get("errorMessage")).contains("booked"));
        String booked = row(shipmentId);
        assertEquals(409, send(alice, "POST", "/shipments/edit", edit).response.status);
        assertEquals(booked, row(shipmentId));

        send(alice, "POST", "/bookings/cancel", "csrfToken", token(alice), "id", String.valueOf(bookingId));
        assertEquals("/WEB-INF/jsp/shipment-form.jsp",
                send(alice, "GET", "/shipments/edit", "shipmentId", id).response.forwardedTo, "a draft again");
    }

    private static String row(long shipmentId) throws Exception {
        return TestDatabase.queryString(db, "SELECT CONCAT_WS('|', user_id, source, destination, cargo_type, weight_kg, "
                + "dispatch_date, status) FROM shipments WHERE shipment_id = " + shipmentId);
    }

    private static String report(WebFakes.Browser b, long shipmentId) throws Exception {
        WebFakes.Request r = send(b, "POST", "/reports", "csrfToken", token(b), "shipmentId", String.valueOf(shipmentId),
                "strategy", "BALANCED");
        String jobId = r.response.redirect.replaceAll(".*jobId=", "");
        assertTrue(services.getReportService().awaitFinished(jobId, Duration.ofSeconds(10)));
        return jobId;
    }

    @Test
    void reportDownloadIsOwnerOnlyDoneOnlyAndSentAsASafeAttachment() throws Exception {
        WebFakes.Browser alice = signedIn("alice@example.test");
        WebFakes.Browser bob = signedIn("bob@example.test");
        long shipmentId = createShipment(alice);
        String jobId = report(alice, shipmentId);
        assertEquals(Boolean.TRUE, ((ReportRecord) send(alice, "GET", "/reports", "jobId", jobId).attributes.get("job"))
                .isDone());

        WebFakes.Request ok = send(alice, "GET", "/reports/download", "jobId", jobId, "file", "../../web.xml");
        assertEquals(200, ok.response.status);
        assertNull(ok.response.forwardedTo);
        assertEquals("text/plain; charset=UTF-8", ok.response.contentType);
        assertEquals("attachment; filename=\"logiopt-report-shipment-" + shipmentId + "-balanced.txt\"",
                ok.response.headers.get("Content-Disposition"));
        assertEquals("nosniff", ok.response.headers.get("X-Content-Type-Options"));
        String text = ok.response.body.toString(StandardCharsets.UTF_8);
        assertTrue(text.startsWith("LogiOpt shipment report"), text);
        String managed = TestDatabase.queryString(db, "SELECT file_name FROM reports WHERE job_id = '" + jobId + "'");
        assertEquals(Files.readString(reportsDir.resolve(managed), StandardCharsets.UTF_8), text, "the file, unchanged");
        assertEquals(ok.response.body.size(), ok.response.contentLength);

        TestDatabase.execute(db, "INSERT INTO reports (job_id, user_id, shipment_id, strategy_key, status, error_message, "
                + "finished_at) VALUES ('failed-web-job', " + user(alice).getUserId() + ", " + shipmentId
                + ", 'BALANCED', 'FAILED', 'x', CURRENT_TIMESTAMP(3))", "INSERT INTO reports (job_id, user_id, shipment_id, "
                + "strategy_key, status) VALUES ('queued-web-job', " + user(alice).getUserId() + ", " + shipmentId
                + ", 'BALANCED', 'QUEUED')");
        List<WebFakes.Request> refused = new ArrayList<>(List.of(
                send(bob, "GET", "/reports/download", "jobId", jobId),
                send(alice, "GET", "/reports/download", "jobId", "no-such-job"),
                send(alice, "GET", "/reports/download"),
                send(alice, "GET", "/reports/download", "jobId", "failed-web-job"),
                send(alice, "GET", "/reports/download", "jobId", "queued-web-job"),
                send(bob, "GET", "/reports", "jobId", jobId)));
        for (String crafted : new String[] {"../" + managed, "..\\\\..\\\\WEB-INF\\\\web.xml",
                reportsDir.resolve(managed).toString().replace("\\", "\\\\"), "shipment-1-BALANCED-20990101000000.txt"}) {
            TestDatabase.execute(db, "UPDATE reports SET file_name = '" + crafted + "' WHERE job_id = '" + jobId + "'");
            refused.add(send(alice, "GET", "/reports/download", "jobId", jobId));
        }
        for (WebFakes.Request r : refused) {
            assertEquals(404, r.response.status);
            assertEquals(NOT_FOUND, r.attributes.get("errorMessage"), "no internal path is revealed");
            assertEquals(0, r.response.body.size());
            assertNull(r.response.headers.get("Content-Disposition"));
        }
        assertEquals(ReportStatus.FAILED, ((ReportRecord) send(alice, "GET", "/reports", "jobId", "queued-web-job")
                .attributes.get("job")).getStatus(), "an abandoned QUEUED job is shown as FAILED");
    }

    @Test
    void sameSourceAndDestinationIsRejectedOnCreateAndEdit() throws Exception {
        WebFakes.Browser alice = signedIn("alice@example.test");
        long before = TestDatabase.queryLong(db, "SELECT COUNT(*) FROM shipments");
        String date = LocalDate.now().plusDays(7).toString();
        WebFakes.Request create = send(alice, "POST", "/shipments/create", "csrfToken", token(alice), "source", "Delhi",
                "destination", " delhi ", "cargoType", "GENERAL", "weightKg", "5000", "dispatchDate", date);
        assertEquals(400, create.response.status);
        @SuppressWarnings("unchecked")
        List<String> errors = (List<String>) create.attributes.get("errors");
        assertTrue(errors.contains("Source and destination must be different."), errors.toString());
        assertEquals(before, TestDatabase.queryLong(db, "SELECT COUNT(*) FROM shipments"), "nothing is saved");
        String id = String.valueOf(createShipment(alice));
        WebFakes.Request edit = send(alice, "POST", "/shipments/edit", "csrfToken", token(alice), "shipmentId", id,
                "source", "Mumbai", "destination", "Mumbai", "cargoType", "GENERAL", "weightKg", "5000",
                "dispatchDate", date);
        assertEquals(400, edit.response.status);
        @SuppressWarnings("unchecked")
        List<String> editErrors = (List<String>) edit.attributes.get("errors");
        assertTrue(editErrors.contains("Source and destination must be different."), editErrors.toString());
        assertEquals("Delhi", TestDatabase.queryString(db, "SELECT destination FROM shipments WHERE shipment_id = " + id));
    }
}

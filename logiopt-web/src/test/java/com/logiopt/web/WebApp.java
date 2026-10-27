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
import com.logiopt.model.User;
import com.logiopt.report.ReportService;
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
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The application as the container would assemble it, for servlet-level tests on the disposable
 * test database (LOGIOPT_TEST_DB_URL, name ending in _test): the services are wired like
 * AppContextListener does, the filters are instantiated from web.xml in its filter-mapping order,
 * and every {@code @WebServlet} class in the compiled output is mapped at its declared URL. JSPs are
 * not rendered here; forwards are recorded (see JspRenderingIntegrationTest for rendering).
 */
final class WebApp {

    /** Password used for every account the harness registers. */
    static final String PASSWORD = "s3cret-pass";
    /** Administrator password seeded by {@link #signedInAdmin()}. */
    static final String ADMIN_PASSWORD = "admin-pass-1";

    final DBConnection db;
    final AppServices services;
    final NotificationService notifications;
    final List<Filter> filters = new ArrayList<>();
    final Map<String, HttpServlet> servlets = new LinkedHashMap<>();
    private final Map<String, WebFakes.Session> sessionStore = new HashMap<>();

    /**
     * Drops and migrates the test database and assembles the application.
     *
     * @param reportsDir reports directory
     * @throws Exception on setup failure
     */
    WebApp(Path reportsDir) throws Exception {
        db = TestDatabase.connect();
        TestDatabase.dropAllTables(db);
        MigrationRunner migrations = new MigrationRunner(db);
        migrations.migrate();
        ShipmentValidator validator = new ShipmentValidator(Clock.systemDefaultZone());
        RecommendationService recommendations = new RecommendationService();
        PlanningService planning = new PlanningService(validator, new EligibilityService(),
                new RouteEvaluator(new CostService(), new DurationService(), new EmissionService()), recommendations);
        UserDAO users = new UserDAO(db);
        AuthService auth = new AuthService(users, new PasswordHasher(PasswordHasher.MIN_ITERATIONS, new SecureRandom()),
                new LoginThrottle(Clock.systemUTC(), 5, Duration.ofMinutes(5), Duration.ofMinutes(5)));
        SchemaGate gate = new SchemaGate(migrations::appliedVersion, migrations.requiredVersion(), db.getDatabaseName(),
                Clock.systemUTC(), Duration.ofSeconds(10), () -> { });
        ShipmentDAO shipmentDao = new ShipmentDAO(db);
        RouteDAO routeDao = new RouteDAO(db);
        BookingDAO bookingDao = new BookingDAO(db);
        notifications = new NotificationService(new NotificationDAO(db), users, 1, 100);
        ReportService reports = new ReportService(reportsDir, Clock.systemDefaultZone(), 1, new ReportDAO(db));
        services = new AppServices(shipmentDao, routeDao, validator, recommendations, planning, reports, auth, gate,
                new BookingService(shipmentDao, routeDao, bookingDao, planning, Clock.systemDefaultZone(), notifications),
                new PaymentService(bookingDao, new PaymentDAO(db), notifications),
                new TrackingService(bookingDao, new TrackingDAO(db), notifications), notifications,
                new ShipmentService(shipmentDao, validator), new DashboardService(new DashboardDAO(db), notifications),
                new HistoryService(shipmentDao, bookingDao, reports));
        ServletContext context = WebFakes.context(services);
        for (String filterClass : filterClassesInWebXmlOrder()) {
            filters.add(WebFakes.init((Filter) Class.forName(filterClass).getDeclaredConstructor().newInstance(),
                    context));
        }
        for (Class<? extends HttpServlet> type : annotatedServlets()) {
            for (String path : type.getAnnotation(WebServlet.class).value()) {
                servlets.put(path, WebFakes.init(type.getDeclaredConstructor().newInstance(), context));
            }
        }
    }

    /**
     * @return the filter classes in the order of web.xml's filter-mapping elements
     * @throws Exception if web.xml cannot be read
     */
    static List<String> filterClassesInWebXmlOrder() throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Document doc = f.newDocumentBuilder().parse(Paths.get("src", "main", "webapp", "WEB-INF", "web.xml").toFile());
        Map<String, String> classes = new HashMap<>();
        NodeList declared = doc.getElementsByTagNameNS("*", "filter");
        for (int i = 0; i < declared.getLength(); i++) {
            Element e = (Element) declared.item(i);
            classes.put(text(e, "filter-name"), text(e, "filter-class"));
        }
        List<String> ordered = new ArrayList<>();
        NodeList mappings = doc.getElementsByTagNameNS("*", "filter-mapping");
        for (int i = 0; i < mappings.getLength(); i++) {
            ordered.add(classes.get(text((Element) mappings.item(i), "filter-name")));
        }
        return ordered;
    }

    private static String text(Element parent, String tag) {
        return parent.getElementsByTagNameNS("*", tag).item(0).getTextContent().trim();
    }

    /**
     * @return every HttpServlet subclass with {@code @WebServlet} in the compiled web package
     * @throws Exception if the classes cannot be listed or loaded
     */
    @SuppressWarnings("unchecked")
    static List<Class<? extends HttpServlet>> annotatedServlets() throws Exception {
        List<Class<? extends HttpServlet>> result = new ArrayList<>();
        try (Stream<Path> files = Files.list(Paths.get("target", "classes", "com", "logiopt", "web"))) {
            for (Path p : (Iterable<Path>) files.sorted()::iterator) {
                String name = p.getFileName().toString();
                if (!name.endsWith(".class") || name.contains("$")) {
                    continue;
                }
                Class<?> type = Class.forName("com.logiopt.web." + name.substring(0, name.length() - 6));
                if (HttpServlet.class.isAssignableFrom(type) && type.isAnnotationPresent(WebServlet.class)) {
                    result.add((Class<? extends HttpServlet>) type);
                }
            }
        }
        return result;
    }

    /** Deletes every row a test may have created; keeps the seeded routes and demo user 1. */
    void clean() throws Exception {
        TestDatabase.execute(db, "DELETE FROM reports", "DELETE FROM notifications", "DELETE FROM tracking",
                "DELETE FROM payments", "DELETE FROM booking_legs", "DELETE FROM bookings",
                "DELETE FROM shipments WHERE user_id <> 1", "DELETE FROM users WHERE user_id <> 1");
    }

    /** Stops the background executors. */
    void stop() {
        services.getNotificationService().shutdown();
        services.getReportService().shutdown();
    }

    /** @return a new browser with an empty cookie jar */
    WebFakes.Browser browser() {
        return new WebFakes.Browser(sessionStore);
    }

    /**
     * Sends a request through the filter chain to the Servlet mapped at the path.
     *
     * @param browser client
     * @param method  HTTP method
     * @param path    path inside the application
     * @param params  name/value pairs
     * @return the completed request
     * @throws Exception on failure
     */
    WebFakes.Request send(WebFakes.Browser browser, String method, String path, String... params) throws Exception {
        WebFakes.Request r = browser.request(method, path);
        for (int i = 0; i < params.length; i += 2) {
            r.param(params[i], params[i + 1]);
        }
        WebFakes.run(r, filters, servlets.get(path));
        browser.afterResponse(r);
        return r;
    }

    /**
     * @param browser client
     * @return the CSRF token of the browser's session
     */
    static String token(WebFakes.Browser browser) {
        return (String) browser.session().attributes.get(CsrfFilter.SESSION_TOKEN);
    }

    /**
     * @param browser client
     * @return the signed-in user of the browser's session
     */
    static User user(WebFakes.Browser browser) {
        return (User) browser.session().attributes.get(WebSupport.SESSION_USER);
    }

    /**
     * Signs in through the login Servlet.
     *
     * @param email    email
     * @param password password
     * @return the browser
     * @throws Exception on failure
     */
    WebFakes.Browser signIn(String email, String password) throws Exception {
        WebFakes.Browser b = browser();
        send(b, "GET", "/login");
        WebFakes.Request r = send(b, "POST", "/login", "csrfToken", token(b), "email", email, "password", password);
        assertEquals(WebFakes.CONTEXT_PATH + "/dashboard", r.response.redirect, "sign-in of " + email);
        return b;
    }

    /**
     * Registers through the service and signs in through the Servlet.
     *
     * @param email email
     * @return the browser
     * @throws Exception on failure
     */
    WebFakes.Browser signedIn(String email) throws Exception {
        services.getAuthService().register(email, email.substring(0, email.indexOf('@')), PASSWORD.toCharArray());
        return signIn(email, PASSWORD);
    }

    /**
     * Seeds the administrator and signs in.
     *
     * @return the administrator's browser
     * @throws Exception on failure
     */
    WebFakes.Browser signedInAdmin() throws Exception {
        services.getAuthService().seedAdmin(ADMIN_PASSWORD);
        return signIn(AuthService.ADMIN_EMAIL, ADMIN_PASSWORD);
    }
}

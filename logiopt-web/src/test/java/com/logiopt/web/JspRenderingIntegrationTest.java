package com.logiopt.web;

import com.logiopt.app.SchemaGate;
import com.logiopt.dao.TestDatabase;
import com.logiopt.model.Milestone;
import com.logiopt.report.ReportRecord;
import com.logiopt.report.ReportService;
import com.logiopt.report.ReportStatus;
import jakarta.servlet.ServletException;
import jakarta.servlet.descriptor.JspPropertyGroupDescriptor;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.Context;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.Tomcat;
import org.apache.jasper.servlet.JasperInitializer;
import org.apache.tomcat.util.descriptor.web.JspConfigDescriptorImpl;
import org.apache.tomcat.util.descriptor.web.JspPropertyGroup;
import org.apache.tomcat.util.descriptor.web.JspPropertyGroupDescriptorImpl;
import org.apache.tomcat.util.descriptor.web.WebXml;
import org.apache.tomcat.util.descriptor.web.WebXmlParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.InputSource;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Renders every JSP with Jasper (Tomcat 10.1.61 embed, test scope only) and fails on any EL, tag or
 * compilation error. The data is realistic: each scenario replays the request attributes that the
 * real Servlets, run through the web.xml filter chain on the disposable test database
 * (LOGIOPT_TEST_DB_URL, name ending in _test), forwarded to the page. Empty, normal and error
 * states are rendered for a signed-in customer, the administrator and anonymous visitors. The
 * JSP configuration (page encoding, scripting-invalid) is taken from the real web.xml.
 *
 * <p>The embedded container listens only on an ephemeral 127.0.0.1 port for the duration of the
 * test; it is not the installed Tomcat and deploys nothing.
 */
class JspRenderingIntegrationTest {

    private static final Path WEBAPP = Paths.get("src", "main", "webapp");
    private static final Duration WAIT = Duration.ofSeconds(10);

    /** A page and the request state it is rendered with. */
    private static final class Scenario {
        final String view;
        final Map<String, Object> attributes;
        final int status;
        volatile Throwable failure;

        Scenario(String view, Map<String, Object> attributes, int status) {
            this.view = view;
            this.attributes = attributes;
            this.status = status;
        }
    }

    private static final Map<String, Scenario> SCENARIOS = new ConcurrentHashMap<>();

    @TempDir
    static Path reportsDir;
    @TempDir
    static Path tomcatDir;

    private static WebApp app;
    private static Tomcat tomcat;
    private static HttpClient http;
    private static int port;
    private static final Set<String> RENDERED_VIEWS = new TreeSet<>();
    private static final List<String> BODIES = new ArrayList<>();

    /** Test-only Servlet: puts a scenario's attributes on the request and forwards to its JSP. */
    public static class RenderServlet extends HttpServlet {
        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            Scenario s = SCENARIOS.get(req.getParameter("scenario"));
            s.attributes.forEach(req::setAttribute);
            resp.setStatus(s.status);
            try {
                req.getRequestDispatcher(s.view).forward(req, resp);
            } catch (ServletException | IOException | RuntimeException | Error e) {
                s.failure = e;
                throw e;
            }
        }
    }

    @BeforeAll
    static void start() throws Exception {
        app = new WebApp(reportsDir);
        app.clean();
        tomcat = new Tomcat();
        tomcat.setBaseDir(tomcatDir.toString());
        Connector connector = new Connector();
        connector.setPort(0);
        connector.setProperty("address", "127.0.0.1");
        tomcat.setConnector(connector);
        Context ctx = tomcat.addContext("/logiopt", WEBAPP.toAbsolutePath().toString());
        Tomcat.initWebappDefaults(ctx);
        ctx.addServletContainerInitializer(new JasperInitializer(), null);
        ctx.setJspConfigDescriptor(jspConfigFromWebXml());
        ctx.setRequestCharacterEncoding("UTF-8");
        ctx.setResponseCharacterEncoding("UTF-8");
        ctx.setParentClassLoader(JspRenderingIntegrationTest.class.getClassLoader());
        Tomcat.addServlet(ctx, "render", new RenderServlet());
        ctx.addServletMappingDecoded("/render", "render");
        tomcat.start();
        port = connector.getLocalPort();
        http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(WAIT).build();
    }

    @AfterAll
    static void stop() throws Exception {
        if (tomcat != null) {
            tomcat.stop();
            tomcat.destroy();
        }
        if (app != null) {
            app.stop();
        }
    }

    /** The jsp-config of the real web.xml (UTF-8, scripting-invalid), parsed with Tomcat's own parser. */
    private static JspConfigDescriptorImpl jspConfigFromWebXml() {
        WebXml webXml = new WebXml();
        WebXmlParser parser = new WebXmlParser(true, false, true);
        assertTrue(parser.parseWebXml(new InputSource(WEBAPP.resolve("WEB-INF/web.xml").toUri().toString()), webXml,
                false), "web.xml parses");
        List<JspPropertyGroupDescriptor> groups = new ArrayList<>();
        for (JspPropertyGroup g : webXml.getJspPropertyGroups()) {
            groups.add(new JspPropertyGroupDescriptorImpl(g));
        }
        assertEquals(1, groups.size());
        assertEquals("true", groups.get(0).getScriptingInvalid());
        return new JspConfigDescriptorImpl(groups, List.of());
    }

    /** Records what a real Servlet forwarded to. */
    private static String capture(String name, WebFakes.Request r) {
        assertTrue(r.response.forwardedTo != null, name + " did not forward: redirect " + r.response.redirect);
        SCENARIOS.put(name, new Scenario(r.response.forwardedTo, new HashMap<>(r.attributes), r.response.status));
        return name;
    }

    /** A scenario built from explicit attributes (states no Servlet can be driven into here). */
    private static String manual(String name, String jsp, int status, Map<String, Object> attributes) {
        SCENARIOS.put(name, new Scenario("/WEB-INF/jsp/" + jsp, new HashMap<>(attributes), status));
        return name;
    }

    /** Renders a scenario and checks status, content type, the absence of errors and the expected texts. */
    private static String render(String name, String... expected) throws Exception {
        Scenario s = SCENARIOS.get(name);
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port
                + "/logiopt/render?scenario=" + URLEncoder.encode(name, StandardCharsets.UTF_8))).timeout(WAIT).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (s.failure != null) {
            StringWriter trace = new StringWriter();
            s.failure.printStackTrace(new PrintWriter(trace));
            fail(name + " (" + s.view + ") failed to render:\n" + trace);
        }
        String body = response.body();
        assertEquals(s.status, response.statusCode(), name);
        assertEquals("text/html;charset=UTF-8", response.headers().firstValue("Content-Type").orElse("")
                .replace(" ", ""), name);
        assertTrue(body.contains("</html>"), name + " rendered completely");
        for (String bad : List.of("${", "<%", "Exception", "jasper", "at com.logiopt", "Stack")) {
            assertFalse(body.contains(bad), name + " contains " + bad);
        }
        for (String text : expected) {
            assertTrue(body.contains(text), name + " should contain [" + text + "]");
        }
        synchronized (RENDERED_VIEWS) {
            RENDERED_VIEWS.add(s.view.substring(s.view.lastIndexOf('/') + 1));
            BODIES.add(body);
        }
        return body;
    }

    private WebFakes.Request get(WebFakes.Browser b, String path, String... params) throws Exception {
        return app.send(b, "GET", path, params);
    }

    private WebFakes.Request post(WebFakes.Browser b, String path, String... params) throws Exception {
        String[] all = new String[params.length + 2];
        all[0] = "csrfToken";
        all[1] = WebApp.token(b);
        System.arraycopy(params, 0, all, 2, params.length);
        return app.send(b, "POST", path, all);
    }

    private long shipment(WebFakes.Browser b, String source, String destination, String dimensions) throws Exception {
        String location = post(b, "/shipments/create", "source", source, "destination", destination, "cargoType",
                "GENERAL", "weightKg", "5000", "dimensions", dimensions, "dispatchDate",
                LocalDate.now().plusDays(7).toString()).response.redirect;
        return Long.parseLong(location.replaceAll(".*shipmentId=(\\d+).*", "$1"));
    }

    private long book(WebFakes.Browser b, long shipmentId, int routeId) throws Exception {
        String location = post(b, "/bookings", "shipmentId", String.valueOf(shipmentId), "routeId",
                String.valueOf(routeId), "strategy", "BALANCED").response.redirect;
        return Long.parseLong(location.replaceAll(".*id=(\\d+).*", "$1"));
    }

    @Test
    void everyPageRendersWithRealisticDataInEmptyNormalAndErrorStates() throws Exception {
        WebFakes.Browser anonymous = app.browser();
        render(capture("login", get(anonymous, "/login")), "Sign in");
        render(capture("login-notice", get(anonymous, "/login", "registered", "1")), "Registration received");
        render(capture("login-error", post(anonymous, "/login", "email", "nobody@example.test", "password", "wrong")),
                LoginServlet.INVALID_MESSAGE);
        render(capture("register", get(anonymous, "/register")));
        render(capture("register-errors", post(anonymous, "/register", "email", "bad", "name", "", "password", "x")));

        WebFakes.Browser admin = app.signedInAdmin();
        render(capture("admin-tracking-empty", get(admin, "/admin/tracking")), "Admin: tracking");
        render(capture("admin-bookings-empty", get(admin, "/admin/bookings")));

        WebFakes.Browser carol = app.signedIn("carol@example.test");
        render(capture("dashboard-empty", get(carol, "/dashboard")), "You have no shipments yet",
                "You have no bookings yet", "No confirmed bookings yet", "Signed in as");
        render(capture("shipments-empty", get(carol, "/shipments")), "No shipments yet");
        render(capture("bookings-empty", get(carol, "/bookings")), "No bookings yet");
        render(capture("notifications-empty", get(carol, "/notifications")));
        render(capture("history-empty", get(carol, "/history")), "Showing the latest 25", "No reports yet");
        render(capture("shipment-form-new", get(carol, "/shipments/new")), "New shipment", "Mumbai", "Delhi");

        WebFakes.Browser alice = app.signedIn("alice@example.test");
        long shipmentId = shipment(alice, "Mumbai", "Delhi", "<b>bold</b> & \"quoted\"");
        String id = String.valueOf(shipmentId);
        render(capture("shipment-form-errors", post(alice, "/shipments/create", "source", "Atlantis", "destination",
                "Delhi", "cargoType", "GENERAL", "weightKg", "1.2345", "dispatchDate", "2020-01-01")), "Atlantis");
        for (String strategy : List.of("BALANCED", "CHEAPEST", "FASTEST", "GREENEST")) {
            render(capture("comparison-" + strategy, get(alice, "/compare", "shipmentId", id, "strategy", strategy)),
                    "Rail direct");
        }
        // Every pair of selectable cities has routes since V9, so the no-routes state needs a saved legacy row.
        TestDatabase.execute(app.db, "INSERT INTO shipments (user_id, source, destination, cargo_type, weight_kg, "
                + "dispatch_date, status) VALUES (" + WebApp.user(alice).getUserId()
                + ", 'Mumbai', 'Pune', 'GENERAL', 5000, '" + LocalDate.now().plusDays(7) + "', 'DRAFT')");
        long noRoutes = TestDatabase.queryLong(app.db, "SELECT MAX(shipment_id) FROM shipments");
        render(capture("comparison-no-routes", get(alice, "/compare", "shipmentId", String.valueOf(noRoutes),
                "strategy", "BALANCED")), "No routes are configured for this source and destination.");
        TestDatabase.execute(app.db, "INSERT INTO shipments (user_id, source, destination, cargo_type, weight_kg, "
                + "dispatch_date, status) VALUES (" + WebApp.user(alice).getUserId()
                + ", 'Mumbai', 'Delhi', 'GENERAL', 5000, '2025-01-15', 'DRAFT')");
        long past = TestDatabase.queryLong(app.db, "SELECT MAX(shipment_id) FROM shipments");
        render(capture("comparison-past-date", get(alice, "/compare", "shipmentId", String.valueOf(past), "strategy",
                "BALANCED")), "Rail direct");
        render(capture("shipment-form-edit", get(alice, "/shipments/edit", "shipmentId", id)), "Edit draft shipment",
                "value=\"5000\"");
        render(capture("shipment-form-edit-errors", post(alice, "/shipments/edit", "shipmentId", id, "source", "Mumbai",
                "destination", "Delhi", "cargoType", "GENERAL", "weightKg", "0", "dispatchDate", "2020-01-01")));

        long bookingId = book(alice, shipmentId, 2);
        String bid = String.valueOf(bookingId);
        render(capture("booking-pending", get(alice, "/bookings/view", "id", bid, "created", "1")), "Booking saved");
        render(capture("comparison-active-booking", get(alice, "/compare", "shipmentId", id, "strategy", "BALANCED")));
        render(capture("payment", get(alice, "/payments", "bookingId", bid)), "Simulate success", "Simulate failure",
                "UPI", "Net banking");
        post(alice, "/payments", "bookingId", bid, "method", "UPI", "outcome", "FAILURE");
        render(capture("booking-payment-failed", get(alice, "/bookings/view", "id", bid, "payment", "failed")),
                "Simulated payment failed");
        post(alice, "/payments", "bookingId", bid, "method", "CARD", "outcome", "SUCCESS");
        render(capture("booking-confirmed", get(alice, "/bookings/view", "id", bid, "payment", "paid")), "Booked");
        long other = shipment(alice, "Mumbai", "Delhi", "");
        long cancelled = book(alice, other, 1);
        post(alice, "/bookings/cancel", "id", String.valueOf(cancelled));
        render(capture("booking-cancelled", get(alice, "/bookings/view", "id", String.valueOf(cancelled),
                "cancelled", "1")), "Booking cancelled");

        render(capture("admin-tracking", get(admin, "/admin/tracking", "result", "recorded")), "Milestone recorded.",
                "Picked up");
        app.services.getTrackingService().advance(bookingId, Milestone.BOOKED);
        render(capture("booking-tracked", get(alice, "/bookings/view", "id", bid)), "Picked up");
        render(capture("booking-admin-readonly", get(admin, "/bookings/view", "id", bid)), "Admin: dashboard");
        render(capture("admin-bookings", get(admin, "/admin/bookings")), "Rail direct");
        render(capture("admin-users", get(admin, "/admin/users")), "alice@example.test", "carol@example.test");
        render(capture("admin-dashboard", get(admin, "/admin/dashboard")), "Admin dashboard",
                "the disabled demo user is not counted");

        assertTrue(app.notifications.awaitIdle(WAIT));
        render(capture("dashboard", get(alice, "/dashboard")), "Picked up", "unread");
        render(capture("shipments", get(alice, "/shipments")), "&lt;b&gt;bold&lt;/b&gt; &amp; &#034;quoted&#034;",
                "Edit draft");
        render(capture("bookings", get(alice, "/bookings")), "Rail direct");
        render(capture("notifications", get(alice, "/notifications")), "Mark all as read");

        String jobId = post(alice, "/reports", "shipmentId", id, "strategy", "BALANCED").response.redirect
                .replaceAll(".*jobId=", "");
        assertTrue(app.services.getReportService().awaitFinished(jobId, WAIT));
        render(capture("report-done", get(alice, "/reports", "jobId", jobId)), "DONE", "Download the report (TXT)");
        TestDatabase.execute(app.db, "INSERT INTO reports (job_id, user_id, shipment_id, strategy_key, status, "
                + "error_message, finished_at) VALUES ('render-failed', " + WebApp.user(alice).getUserId() + ", " + id
                + ", 'CHEAPEST', 'FAILED', '" + ReportService.WRITE_FAILED_MESSAGE + "', CURRENT_TIMESTAMP(3))");
        render(capture("report-failed", get(alice, "/reports", "jobId", "render-failed")), "FAILED",
                ReportService.WRITE_FAILED_MESSAGE);
        Map<String, Object> running = new HashMap<>(SCENARIOS.get("report-done").attributes);
        running.put("job", new ReportRecord("render-running", WebApp.user(alice).getUserId(), shipmentId, "FASTEST",
                ReportStatus.RUNNING, LocalDateTime.now(), LocalDateTime.now(), null, null, null));
        String runningBody = render(manual("report-running", "report-status.jsp", 200, running), "RUNNING",
                "refreshes every 2 seconds");
        assertTrue(runningBody.contains("http-equiv=\"refresh\""));
        render(capture("history", get(alice, "/history")), "Download TXT", ReportService.WRITE_FAILED_MESSAGE,
                "Showing the latest 25");

        // Error pages: application errors, CSRF, and every status the container itself may raise.
        render(capture("error-not-editable", get(alice, "/shipments/edit", "shipmentId", id)), "cannot be edited");
        render(capture("error-foreign-404", get(carol, "/bookings/view", "id", bid)),
                "The page you requested does not exist.");
        render(capture("error-csrf-403", app.send(alice, "POST", "/notifications", "action", "readAll")),
                "This form has expired");
        Map<String, Object> customer = Map.of(WebSupport.CURRENT_USER, WebApp.user(alice), CsrfFilter.PARAM,
                WebApp.token(alice));
        Map<Integer, String> expectedText = Map.of(400, "not understood", 401, "Please sign in", 403, "not allowed",
                404, "does not exist", 405, "cannot be used that way", 429, "Too many attempts", 500,
                "unexpected error", 503, "temporarily unavailable");
        for (Map.Entry<Integer, String> e : expectedText.entrySet()) {
            render(manual("error-container-" + e.getKey(), "error.jsp", e.getKey(),
                    Map.of("jakarta.servlet.error.status_code", e.getKey())), e.getValue());
        }
        Map<String, Object> signedInError = new HashMap<>(customer);
        signedInError.put("jakarta.servlet.error.status_code", 500);
        render(manual("error-container-500-signed-in", "error.jsp", 500, signedInError), "unexpected error",
                "Signed in as");
        Map<String, Object> dashboardError = new HashMap<>(customer);
        dashboardError.put("dashboardError", DashboardServlet.UNAVAILABLE);
        render(manual("dashboard-error", "dashboard.jsp", 503, dashboardError), "Dashboard unavailable");
        Map<String, Object> adminError = new HashMap<>(Map.of(WebSupport.CURRENT_USER, WebApp.user(admin),
                CsrfFilter.PARAM, WebApp.token(admin), "dashboardError", DashboardServlet.UNAVAILABLE));
        render(manual("admin-dashboard-error", "admin-dashboard.jsp", 503, adminError), "Dashboard unavailable");
        render(manual("error-schema-gate-503", "error.jsp", 503, Map.of("errorMessage", SchemaGate.PAGE_MESSAGE)),
                SchemaGate.PAGE_MESSAGE);

        // Every JSP was rendered, and no page leaks credentials.
        Set<String> jsps;
        try (Stream<Path> files = Files.list(WEBAPP.resolve("WEB-INF/jsp"))) {
            jsps = files.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".jsp"))
                    .collect(Collectors.toCollection(TreeSet::new));
        }
        assertEquals(jsps, RENDERED_VIEWS, "every JSP is rendered at least once");
        String hash = TestDatabase.queryString(app.db, "SELECT password_hash FROM users WHERE email = 'alice@example.test'");
        String salt = TestDatabase.queryString(app.db, "SELECT password_salt FROM users WHERE email = 'alice@example.test'");
        for (String body : BODIES) {
            assertFalse(body.contains(hash) || body.contains(salt) || body.contains(WebApp.PASSWORD)
                    || body.contains(reportsDir.toString()), "a page shows a hash, salt, password or server path");
        }
        assertTrue(SCENARIOS.size() >= 50, "scenarios rendered: " + SCENARIOS.size());
    }

    @Test
    void anElErrorFailsTheRender() throws Exception {
        String name = manual("broken-on-purpose", "report-status.jsp", 200, Map.of("job", "not a report"));
        HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port
                + "/logiopt/render?scenario=" + name)).timeout(WAIT).build(), HttpResponse.BodyHandlers.ofString());
        assertTrue(SCENARIOS.get(name).failure != null, "a missing bean property fails the render");
        StringBuilder causes = new StringBuilder();
        for (Throwable t = SCENARIOS.get(name).failure; t != null; t = t.getCause()) {
            causes.append(t).append(" | ");
        }
        assertTrue(causes.toString().contains("finished"), causes::toString);
        assertEquals(500, r.statusCode());
    }

    @Test
    void indexRedirectsToTheDashboard() throws Exception {
        HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port
                + "/logiopt/index.jsp")).timeout(WAIT).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(302, r.statusCode());
        assertTrue(r.headers().firstValue("Location").orElse("").endsWith("/logiopt/dashboard"));
    }

    @Test
    void pagesUseNoScriptletScriptOrStyleAndTheWebappHasNoScriptOrStylesheetFiles() throws Exception {
        try (Stream<Path> files = Files.walk(WEBAPP)) {
            for (Path p : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator) {
                String name = p.getFileName().toString().toLowerCase();
                assertFalse(name.endsWith(".js") || name.endsWith(".css") || name.endsWith(".mjs"), p.toString());
                if (name.endsWith(".jsp") || name.endsWith(".jspf")) {
                    String jsp = Files.readString(p, StandardCharsets.UTF_8);
                    String lower = jsp.toLowerCase();
                    assertFalse(jsp.contains("<%=") || jsp.contains("<%!") || jsp.matches("(?s).*<%\\s.*"),
                            p + " has a scriptlet");
                    assertFalse(lower.contains("<script") || lower.contains("javascript:"), p + " has script");
                    assertFalse(lower.contains("<style") || lower.contains(" style=") || lower.contains("<link"),
                            p + " has CSS");
                    assertFalse(lower.matches("(?s).*\\son[a-z]+\\s*=.*"), p + " has an inline event handler");
                }
            }
        }
    }
}

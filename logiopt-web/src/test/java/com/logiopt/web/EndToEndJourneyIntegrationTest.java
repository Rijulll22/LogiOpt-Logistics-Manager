package com.logiopt.web;

import com.logiopt.dao.TestDatabase;
import com.logiopt.model.Booking;
import com.logiopt.model.BookingStatus;
import com.logiopt.model.CustomerDashboard;
import com.logiopt.model.Milestone;
import com.logiopt.model.Notification;
import com.logiopt.model.NotificationType;
import com.logiopt.model.Payment;
import com.logiopt.model.PaymentStatus;
import com.logiopt.model.PlanningResult;
import com.logiopt.model.RouteEvaluation;
import com.logiopt.model.ShipmentStatus;
import com.logiopt.model.TrackingEvent;
import com.logiopt.model.TransportMode;
import com.logiopt.report.ReportRecord;
import com.logiopt.service.HistoryService;
import com.logiopt.service.TrackingService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One complete customer journey through the real filters (web.xml order) and Servlets on the
 * disposable test database (LOGIOPT_TEST_DB_URL, name ending in _test): register, sign in, create a
 * shipment, compare, book, fail and then succeed the simulated payment, admin tracking up to
 * DELIVERED, the customer's timeline and notifications, a report generated and downloaded, sign
 * out, and protected URLs redirecting to sign-in afterwards. Every response is checked for the
 * security headers. JSPs are not rendered here (JspRenderingIntegrationTest renders them).
 */
class EndToEndJourneyIntegrationTest {

    private static final Duration WAIT = Duration.ofSeconds(10);
    private static final String EMAIL = "journey@example.test";

    @TempDir
    static Path reportsDir;

    private static WebApp app;
    private final List<WebFakes.Request> responses = new ArrayList<>();

    @BeforeAll
    static void start() throws Exception {
        app = new WebApp(reportsDir);
        app.clean();
    }

    @AfterAll
    static void stop() {
        if (app != null) {
            app.stop();
        }
    }

    private WebFakes.Request send(WebFakes.Browser b, String method, String path, String... params) throws Exception {
        WebFakes.Request r = app.send(b, method, path, params);
        responses.add(r);
        return r;
    }

    private static String id(long value) {
        return String.valueOf(value);
    }

    @Test
    void completeJourney() throws Exception {
        WebFakes.Browser customer = app.browser();
        assertEquals("/logiopt/login", send(customer, "GET", "/dashboard").response.redirect, "sign-in required");

        // Register and sign in; the dashboard is the landing page.
        assertEquals("/WEB-INF/jsp/register.jsp", send(customer, "GET", "/register").response.forwardedTo);
        assertEquals("/logiopt/login?registered=1", send(customer, "POST", "/register", "csrfToken",
                WebApp.token(customer), "email", EMAIL, "name", "Journey", "password", WebApp.PASSWORD).response.redirect);
        send(customer, "GET", "/login");
        assertEquals("/logiopt/dashboard", send(customer, "POST", "/login", "csrfToken", WebApp.token(customer),
                "email", EMAIL, "password", WebApp.PASSWORD).response.redirect);
        CustomerDashboard empty = (CustomerDashboard) send(customer, "GET", "/dashboard").attributes.get("dashboard");
        assertEquals(0, empty.getShipmentTotal());

        // Create a shipment and compare its routes.
        String created = send(customer, "POST", "/shipments/create", "csrfToken", WebApp.token(customer),
                "source", "Mumbai", "destination", "Delhi", "cargoType", "GENERAL", "weightKg", "5000",
                "dispatchDate", LocalDate.now().plusDays(7).toString()).response.redirect;
        assertNotNull(created, "shipment saved");
        long shipmentId = Long.parseLong(created.replaceAll(".*shipmentId=(\\d+).*", "$1"));
        WebFakes.Request compare = send(customer, "GET", "/compare", "shipmentId", id(shipmentId), "strategy", "BALANCED");
        assertEquals("/WEB-INF/jsp/comparison.jsp", compare.response.forwardedTo);
        PlanningResult result = (PlanningResult) compare.attributes.get("result");
        assertEquals(2L, result.getRecommendation().getChosen().getRouteId(), "Review 2 Balanced winner unchanged");
        RouteEvaluation chosen = result.getEvaluations().stream()
                .filter(e -> e.getMode() == TransportMode.MULTIMODAL).findFirst()
                .orElse(result.getRecommendation().getChosen());

        // Book, fail the simulated payment, then succeed.
        String booked = send(customer, "POST", "/bookings", "csrfToken", WebApp.token(customer), "shipmentId",
                id(shipmentId), "routeId", id(chosen.getRouteId()), "strategy", "BALANCED").response.redirect;
        long bookingId = Long.parseLong(booked.replaceAll(".*id=(\\d+).*", "$1"));
        assertEquals("/WEB-INF/jsp/payment.jsp", send(customer, "GET", "/payments", "bookingId", id(bookingId))
                .response.forwardedTo);
        assertEquals("/logiopt/bookings/view?id=" + bookingId + "&payment=failed", send(customer, "POST", "/payments",
                "csrfToken", WebApp.token(customer), "bookingId", id(bookingId), "method", "UPI", "outcome", "FAILURE")
                .response.redirect);
        Booking pending = (Booking) send(customer, "GET", "/bookings/view", "id", id(bookingId)).attributes.get("booking");
        assertEquals(BookingStatus.PENDING_PAYMENT, pending.getStatus());
        assertEquals("/logiopt/bookings/view?id=" + bookingId + "&payment=paid", send(customer, "POST", "/payments",
                "csrfToken", WebApp.token(customer), "bookingId", id(bookingId), "method", "CARD", "outcome", "SUCCESS")
                .response.redirect);
        WebFakes.Request paid = send(customer, "GET", "/bookings/view", "id", id(bookingId));
        assertEquals(BookingStatus.CONFIRMED, ((Booking) paid.attributes.get("booking")).getStatus());
        @SuppressWarnings("unchecked")
        List<Payment> attempts = (List<Payment>) paid.attributes.get("payments");
        assertEquals(Set.of(PaymentStatus.FAILED, PaymentStatus.PAID),
                attempts.stream().map(Payment::getStatus).collect(Collectors.toSet()), "a failed and a paid attempt");
        assertEquals(2, attempts.size());

        // The administrator advances tracking, alternating the record form and the Advance button.
        WebFakes.Browser admin = app.signedInAdmin();
        boolean record = true;
        for (int step = 0; step < 10; step++) {
            @SuppressWarnings("unchecked")
            List<TrackingRow> rows = (List<TrackingRow>) send(admin, "GET", "/admin/tracking").attributes.get("rows");
            TrackingRow row = rows.stream().filter(x -> x.getBooking().getBookingId() == bookingId).findFirst()
                    .orElseThrow();
            if (row.getNext() == null) {
                break;
            }
            String[] params = record
                    ? new String[] {"csrfToken", WebApp.token(admin), "action", "record", "bookingId", id(bookingId),
                        "current", row.getCurrentKey(), "milestone", row.getNext().name(), "description",
                        "Recorded by the administrator: " + row.getNext().getLabel()}
                    : new String[] {"csrfToken", WebApp.token(admin), "action", "advance", "bookingId", id(bookingId),
                        "current", row.getCurrentKey()};
            assertEquals("/logiopt/admin/tracking?result=recorded", send(admin, "POST", "/admin/tracking", params)
                    .response.redirect);
            record = !record;
        }
        assertEquals("COMPLETED", TestDatabase.queryString(app.db,
                "SELECT status FROM shipments WHERE shipment_id = " + shipmentId), "DELIVERED completes the shipment");

        // The customer sees the full timeline and the notifications.
        WebFakes.Request view = send(customer, "GET", "/bookings/view", "id", id(bookingId));
        @SuppressWarnings("unchecked")
        List<TrackingEvent> timeline = (List<TrackingEvent>) view.attributes.get("timeline");
        Booking confirmed = (Booking) view.attributes.get("booking");
        assertEquals(Milestone.sequence(TrackingService.isMultiLeg(confirmed)),
                timeline.stream().map(TrackingEvent::getMilestone).toList());
        assertNull(view.attributes.get("nextMilestone"), "DELIVERED is terminal");
        assertTrue(app.notifications.awaitIdle(WAIT));
        WebFakes.Request inbox = send(customer, "GET", "/notifications");
        @SuppressWarnings("unchecked")
        List<Notification> notes = (List<Notification>) inbox.attributes.get("notifications");
        List<NotificationType> types = notes.stream().map(Notification::getType).toList();
        for (NotificationType t : List.of(NotificationType.BOOKING_CREATED, NotificationType.PAYMENT_FAILED,
                NotificationType.PAYMENT_SUCCEEDED, NotificationType.TRACKING_UPDATE)) {
            assertTrue(types.contains(t), t + " in " + types);
        }
        assertEquals(timeline.size(), types.stream().filter(t -> t == NotificationType.TRACKING_UPDATE).count(),
                "one notification per milestone");
        assertEquals(notes.size(), inbox.attributes.get(AuthFilter.UNREAD_COUNT));
        send(customer, "POST", "/notifications", "csrfToken", WebApp.token(customer), "action", "readAll");
        assertEquals(0, send(customer, "GET", "/notifications").attributes.get(AuthFilter.UNREAD_COUNT));

        // Generate the TXT report and download it.
        String status = send(customer, "POST", "/reports", "csrfToken", WebApp.token(customer), "shipmentId",
                id(shipmentId), "strategy", "BALANCED").response.redirect;
        String jobId = status.replaceAll(".*jobId=", "");
        assertTrue(app.services.getReportService().awaitFinished(jobId, WAIT));
        assertTrue(((ReportRecord) send(customer, "GET", "/reports", "jobId", jobId).attributes.get("job")).isDone());
        WebFakes.Request download = send(customer, "GET", "/reports/download", "jobId", jobId);
        assertEquals(200, download.response.status);
        assertTrue(download.response.body.toString(StandardCharsets.UTF_8).startsWith("LogiOpt shipment report"));
        assertEquals("attachment; filename=\"logiopt-report-shipment-" + shipmentId + "-balanced.txt\"",
                download.response.headers.get("Content-Disposition"));

        // Dashboard and history reflect the journey.
        CustomerDashboard done = (CustomerDashboard) send(customer, "GET", "/dashboard").attributes.get("dashboard");
        assertEquals(1, done.getShipmentsByStatus().get(ShipmentStatus.COMPLETED));
        assertEquals(1, done.getBookingsByStatus().get(BookingStatus.CONFIRMED));
        assertEquals(Milestone.DELIVERED, done.getTracking().get(0).getLatest());
        HistoryService.History history = (HistoryService.History) send(customer, "GET", "/history")
                .attributes.get("history");
        assertEquals(jobId, history.getReports().get(0).getJobId());

        // Sign out; every protected URL now redirects to sign-in, and nothing was cacheable.
        assertEquals("/logiopt/login?loggedOut=1", send(customer, "POST", "/logout", "csrfToken",
                WebApp.token(customer)).response.redirect);
        for (String path : List.of("/dashboard", "/shipments", "/shipments/new", "/shipments/edit", "/compare",
                "/bookings", "/bookings/view", "/payments", "/notifications", "/history", "/reports",
                "/reports/download", "/admin/tracking")) {
            WebFakes.Request after = send(customer, "GET", path, "id", id(bookingId), "jobId", jobId,
                    "shipmentId", id(shipmentId));
            assertTrue(after.response.redirect != null && after.response.redirect.startsWith("/logiopt/login"), path);
            assertNull(after.response.forwardedTo, path);
            assertEquals(0, after.response.body.size(), path);
        }
        for (WebFakes.Request r : responses) {
            assertEquals("DENY", r.response.headers.get("X-Frame-Options"), r.servletPath);
            assertEquals("nosniff", r.response.headers.get("X-Content-Type-Options"), r.servletPath);
            assertEquals("same-origin", r.response.headers.get("Referrer-Policy"), r.servletPath);
            assertEquals(SecurityHeadersFilter.CONTENT_SECURITY_POLICY,
                    r.response.headers.get("Content-Security-Policy"), r.servletPath);
            assertTrue(r.response.headers.get("Cache-Control").contains("no-store"), r.servletPath);
        }
    }
}

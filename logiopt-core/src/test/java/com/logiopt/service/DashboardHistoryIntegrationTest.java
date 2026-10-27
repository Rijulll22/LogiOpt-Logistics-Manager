package com.logiopt.service;

import com.logiopt.TestData;
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
import com.logiopt.model.CargoType;
import com.logiopt.model.CustomerDashboard;
import com.logiopt.model.Milestone;
import com.logiopt.model.PaymentMethod;
import com.logiopt.model.Role;
import com.logiopt.model.Shipment;
import com.logiopt.model.ShipmentStatus;
import com.logiopt.model.TrackingSummary;
import com.logiopt.model.User;
import com.logiopt.report.ReportJob;
import com.logiopt.report.ReportRecord;
import com.logiopt.report.ReportService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Dashboard counts and history against fixtures for two customers on the disposable test database
 * (LOGIOPT_TEST_DB_URL, name ending in _test). Every count is compared with the fixture, and
 * neither user ever sees the other's rows.
 */
class DashboardHistoryIntegrationTest {

    private static final LocalDate DISPATCH = LocalDate.of(2026, 11, 2);
    private static final Duration WAIT = Duration.ofSeconds(10);

    private static DBConnection db;

    @TempDir
    Path reportsDir;

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-25T06:00:00Z"), ZoneOffset.UTC);
    private NotificationService notifications;
    private ReportService reports;
    private BookingService bookings;
    private PaymentService payments;
    private TrackingService tracking;
    private DashboardService dashboards;
    private HistoryService history;
    private User alice;
    private User bob;
    private User carol;
    private User admin;

    @BeforeAll
    static void connect() throws Exception {
        db = TestDatabase.connect();
        TestDatabase.dropAllTables(db);
        new MigrationRunner(db).migrate();
    }

    @BeforeEach
    void reset() throws Exception {
        TestDatabase.execute(db, "DELETE FROM reports", "DELETE FROM notifications", "DELETE FROM tracking",
                "DELETE FROM payments", "DELETE FROM booking_legs", "DELETE FROM bookings", "DELETE FROM shipments",
                "DELETE FROM users WHERE user_id <> 1");
        UserDAO users = new UserDAO(db);
        PasswordHasher hasher = new PasswordHasher(PasswordHasher.MIN_ITERATIONS, new SecureRandom());
        alice = user(users, hasher, "alice@example.test", Role.CUSTOMER);
        bob = user(users, hasher, "bob@example.test", Role.CUSTOMER);
        carol = user(users, hasher, "carol@example.test", Role.CUSTOMER);
        admin = user(users, hasher, "admin@logiopt.local", Role.ADMIN);
        PlanningService planning = new PlanningService(new ShipmentValidator(clock), new EligibilityService(),
                new RouteEvaluator(new CostService(), new DurationService(), new EmissionService()),
                new RecommendationService());
        notifications = new NotificationService(new NotificationDAO(db), users, 1, 100);
        BookingDAO bookingDao = new BookingDAO(db);
        bookings = new BookingService(new ShipmentDAO(db), new RouteDAO(db), bookingDao, planning, clock, notifications);
        payments = new PaymentService(bookingDao, new PaymentDAO(db), notifications);
        tracking = new TrackingService(bookingDao, new TrackingDAO(db), notifications);
        reports = new ReportService(reportsDir, TestData.CLOCK, 1, new ReportDAO(db));
        dashboards = new DashboardService(new DashboardDAO(db), notifications);
        history = new HistoryService(new ShipmentDAO(db), bookingDao, reports);
    }

    private static User user(UserDAO users, PasswordHasher hasher, String email, Role role) throws Exception {
        String name = email.substring(0, email.indexOf('@'));
        return new User(users.insert(email, name, hasher.hash("x".toCharArray()), role).get(), email, name, role, true);
    }

    @AfterEach
    void stop() throws Exception {
        assertTrue(notifications.awaitIdle(WAIT));
        notifications.shutdown();
        reports.shutdown();
    }

    private static long shipment(User owner) throws Exception {
        return new ShipmentDAO(db).insert(new Shipment(0, owner.getUserId(), "Mumbai", "Delhi", CargoType.GENERAL, 5000,
                "", DISPATCH, ShipmentStatus.DRAFT));
    }

    private long confirmed(User owner) throws Exception {
        long id = bookings.book(owner, shipment(owner), 2, "BALANCED");
        payments.simulate(owner, id, PaymentMethod.UPI, true);
        return id;
    }

    /**
     * Alice: one confirmed booking tracked to PICKED_UP, one unpaid booking, one cancelled booking
     * (shipment back to DRAFT) and one plain draft. Bob: one confirmed booking at BOOKED.
     */
    private long[] fixture() throws Exception {
        long aliceConfirmed = confirmed(alice);
        tracking.advance(aliceConfirmed, Milestone.BOOKED);
        bookings.book(alice, shipment(alice), 2, "BALANCED");
        bookings.cancel(alice, bookings.book(alice, shipment(alice), 1, "BALANCED"));
        shipment(alice);
        long bobConfirmed = confirmed(bob);
        assertTrue(notifications.awaitIdle(WAIT));
        return new long[] {aliceConfirmed, bobConfirmed};
    }

    private static long unread(User u) throws Exception {
        return TestDatabase.queryLong(db, "SELECT COUNT(*) FROM notifications WHERE is_read = FALSE AND user_id = "
                + u.getUserId());
    }

    @Test
    void customerDashboardsCountOnlyTheirOwnRows() throws Exception {
        long[] ids = fixture();
        CustomerDashboard a = dashboards.customer(alice);
        assertEquals(Map.of(ShipmentStatus.DRAFT, 2, ShipmentStatus.PLANNED, 0, ShipmentStatus.BOOKED, 2,
                ShipmentStatus.CANCELLED, 0, ShipmentStatus.COMPLETED, 0), a.getShipmentsByStatus());
        assertEquals(List.of(ShipmentStatus.values()), new ArrayList<>(a.getShipmentsByStatus().keySet()),
                "every status, in order");
        assertEquals(4, a.getShipmentTotal());
        assertEquals(Map.of(BookingStatus.PENDING_PAYMENT, 1, BookingStatus.CONFIRMED, 1, BookingStatus.CANCELLED, 1),
                a.getBookingsByStatus());
        assertEquals(3, a.getBookingTotal());
        assertEquals(7, a.getUnreadNotifications(), "3 created, 1 paid, 2 milestones, 1 cancelled");
        assertEquals(unread(alice), a.getUnreadNotifications());
        assertEquals(1, a.getTracking().size(), "only confirmed bookings are listed");
        TrackingSummary t = a.getTracking().get(0);
        assertEquals(ids[0], t.getBookingId());
        assertEquals(Milestone.PICKED_UP, t.getLatest());
        assertEquals("Rail direct", t.getRouteName());
        assertFalse(a.isTrackingTruncated());

        CustomerDashboard b = dashboards.customer(bob);
        assertEquals(1, b.getShipmentsByStatus().get(ShipmentStatus.BOOKED));
        assertEquals(1, b.getShipmentTotal());
        assertEquals(Map.of(BookingStatus.PENDING_PAYMENT, 0, BookingStatus.CONFIRMED, 1, BookingStatus.CANCELLED, 0),
                b.getBookingsByStatus());
        assertEquals(3, b.getUnreadNotifications(), "created, paid, BOOKED");
        assertEquals(List.of(ids[1]), b.getTracking().stream().map(TrackingSummary::getBookingId).toList());
        assertEquals(Milestone.BOOKED, b.getTracking().get(0).getLatest());

        new NotificationDAO(db).markAllRead(alice.getUserId());
        assertEquals(0, dashboards.customer(alice).getUnreadNotifications());
        assertEquals(3, dashboards.customer(bob).getUnreadNotifications(), "alice's read-all is her own");
    }

    @Test
    void emptyDashboardHasZerosForEveryStatus() throws Exception {
        fixture();
        CustomerDashboard c = dashboards.customer(carol);
        assertEquals(0, c.getShipmentTotal());
        assertEquals(0, c.getBookingTotal());
        assertEquals(0, c.getUnreadNotifications());
        assertTrue(c.getTracking().isEmpty());
        assertEquals(ShipmentStatus.values().length, c.getShipmentsByStatus().size());
        assertEquals(BookingStatus.values().length, c.getBookingsByStatus().size());
    }

    @Test
    void confirmedBookingWithoutMilestonesShowsNone() throws Exception {
        long id = confirmed(alice);
        TestDatabase.execute(db, "DELETE FROM tracking WHERE booking_id = " + id);
        TrackingSummary t = dashboards.customer(alice).getTracking().get(0);
        assertNull(t.getLatest());
        assertNull(t.getLatestAt());
    }

    @Test
    void adminDashboardCountsTheWholeSystem() throws Exception {
        long[] ids = fixture();
        AdminDashboard d = dashboards.admin(admin);
        assertEquals(Map.of(Role.CUSTOMER, 3, Role.ADMIN, 1), d.getUsersByRole(), "alice, bob, carol; not the demo user");
        assertEquals(4, d.getUserTotal());
        assertEquals("FALSE", TestDatabase.queryString(db, "SELECT IF(enabled, 'TRUE', 'FALSE') FROM users WHERE user_id = 1"),
                "the seeded demo user exists but is disabled");
        TestDatabase.execute(db, "UPDATE users SET enabled = FALSE WHERE user_id = " + carol.getUserId());
        assertEquals(2, dashboards.admin(admin).getUsersByRole().get(Role.CUSTOMER), "disabled accounts are not counted");
        assertEquals(Map.of(BookingStatus.PENDING_PAYMENT, 1, BookingStatus.CONFIRMED, 2, BookingStatus.CANCELLED, 1),
                d.getBookingsByStatus());
        assertEquals(2, d.getConfirmedAwaitingTracking());
        Milestone current = Milestone.BOOKED;
        while (current != Milestone.DELIVERED) {
            current = tracking.advance(ids[1], current).getCurrent();
        }
        assertEquals(1, dashboards.admin(admin).getConfirmedAwaitingTracking(), "delivered bookings no longer wait");
        assertThrows(SecurityException.class, () -> dashboards.admin(alice));
        assertThrows(SecurityException.class, () -> dashboards.admin(null));
    }

    @Test
    void customerTrackingListIsLimitedWithANote() throws Exception {
        for (int i = 0; i <= DashboardService.TRACKING_LIMIT; i++) {
            confirmed(alice);
        }
        CustomerDashboard a = dashboards.customer(alice);
        assertEquals(DashboardService.TRACKING_LIMIT, a.getTracking().size());
        assertTrue(a.isTrackingTruncated());
        assertEquals(DashboardService.TRACKING_LIMIT + 1, a.getBookingsByStatus().get(BookingStatus.CONFIRMED),
                "the counts are not limited");
    }

    @Test
    void historyIsTheOwnersOnlyNewestFirst() throws Exception {
        fixture();
        Shipment aliceShipment = new ShipmentDAO(db).findByUser(alice.getUserId()).get(0);
        ReportJob job = reports.submit(TestData.planningService().plan(aliceShipment,
                new RouteDAO(db).findBySourceAndDestination("Mumbai", "Delhi"), "CHEAPEST"));
        assertTrue(reports.awaitFinished(job.getJobId(), WAIT));

        HistoryService.History a = history.of(alice);
        assertEquals(4, a.getShipments().size());
        assertTrue(a.getShipments().stream().allMatch(s -> s.getUserId() == alice.getUserId()));
        List<Long> ids = a.getShipments().stream().map(Shipment::getShipmentId).toList();
        assertEquals(ids.stream().sorted((x, y) -> Long.compare(y, x)).toList(), ids, "newest first");
        assertEquals(3, a.getBookings().size());
        assertTrue(a.getBookings().stream().allMatch(b -> b.getOwnerUserId() == alice.getUserId()));
        List<Long> bookingIds = a.getBookings().stream().map(Booking::getBookingId).toList();
        assertEquals(bookingIds.stream().sorted((x, y) -> Long.compare(y, x)).toList(), bookingIds);
        assertEquals(List.of(job.getJobId()), a.getReports().stream().map(ReportRecord::getJobId).toList());
        assertFalse(a.isMoreShipments() || a.isMoreBookings() || a.isMoreReports());

        HistoryService.History b = history.of(bob);
        assertEquals(1, b.getShipments().size());
        assertEquals(1, b.getBookings().size());
        assertTrue(b.getReports().isEmpty(), "alice's report is not in bob's history");
        assertTrue(b.getShipments().stream().noneMatch(s -> s.getUserId() == alice.getUserId()));
        HistoryService.History c = history.of(carol);
        assertTrue(c.getShipments().isEmpty() && c.getBookings().isEmpty() && c.getReports().isEmpty());
    }

    @Test
    void historyShowsTheLatestLimitEntries() throws Exception {
        long newest = 0;
        for (int i = 0; i <= HistoryService.LIMIT; i++) {
            newest = shipment(alice);
        }
        HistoryService.History a = history.of(alice);
        assertEquals(HistoryService.LIMIT, a.getShipments().size());
        assertTrue(a.isMoreShipments());
        assertEquals(newest, a.getShipments().get(0).getShipmentId());
        assertEquals(HistoryService.LIMIT, a.getLimit());
    }
}

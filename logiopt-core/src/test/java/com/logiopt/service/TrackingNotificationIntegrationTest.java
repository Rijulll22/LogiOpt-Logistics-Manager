package com.logiopt.service;

import com.logiopt.dao.BookingDAO;
import com.logiopt.dao.DBConnection;
import com.logiopt.dao.MigrationRunner;
import com.logiopt.dao.NotificationDAO;
import com.logiopt.dao.PaymentDAO;
import com.logiopt.dao.RouteDAO;
import com.logiopt.dao.ShipmentDAO;
import com.logiopt.dao.TestDatabase;
import com.logiopt.dao.TrackingDAO;
import com.logiopt.dao.UserDAO;
import com.logiopt.exception.DataAccessException;
import com.logiopt.exception.NotFoundException;
import com.logiopt.exception.TrackingException;
import com.logiopt.model.CargoType;
import com.logiopt.model.Milestone;
import com.logiopt.model.Notification;
import com.logiopt.model.NotificationType;
import com.logiopt.model.PaymentMethod;
import com.logiopt.model.Role;
import com.logiopt.model.Shipment;
import com.logiopt.model.ShipmentStatus;
import com.logiopt.model.TrackingEvent;
import com.logiopt.model.TrackingSource;
import com.logiopt.model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tracking milestones and in-app notifications against the disposable test database
 * (LOGIOPT_TEST_DB_URL, name ending in _test). Waits for notifications with awaitIdle, never sleeps.
 */
class TrackingNotificationIntegrationTest {

    private static final LocalDate DISPATCH = LocalDate.of(2026, 11, 2);
    private static final Duration WAIT = Duration.ofSeconds(10);
    private static final int THREADS = 8;

    private static DBConnection db;

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-25T06:00:00Z"), ZoneOffset.UTC);
    private final List<NotificationService> started = new ArrayList<>();
    private NotificationService notifications;
    private BookingService bookings;
    private PaymentService payments;
    private TrackingService tracking;
    private User alice;
    private User bob;
    private User admin;

    @BeforeAll
    static void connect() throws Exception {
        db = TestDatabase.connect();
        TestDatabase.dropAllTables(db);
        new MigrationRunner(db).migrate();
    }

    @BeforeEach
    void reset() throws Exception {
        TestDatabase.execute(db, "DELETE FROM notifications", "DELETE FROM tracking", "DELETE FROM payments",
                "DELETE FROM booking_legs", "DELETE FROM bookings", "DELETE FROM shipments",
                "DELETE FROM users WHERE user_id <> 1");
        UserDAO users = new UserDAO(db);
        PasswordHasher hasher = new PasswordHasher(PasswordHasher.MIN_ITERATIONS, new SecureRandom());
        alice = new User(users.insert("alice@example.test", "Alice", hasher.hash("x".toCharArray()), Role.CUSTOMER).get(),
                "alice@example.test", "Alice", Role.CUSTOMER, true);
        bob = new User(users.insert("bob@example.test", "Bob", hasher.hash("x".toCharArray()), Role.CUSTOMER).get(),
                "bob@example.test", "Bob", Role.CUSTOMER, true);
        admin = new User(users.insert("admin@logiopt.local", "Administrator", hasher.hash("x".toCharArray()),
                Role.ADMIN).get(), "admin@logiopt.local", "Administrator", Role.ADMIN, true);
        wire(new NotificationDAO(db));
    }

    /** Builds the services, as the application does at start-up (also used to simulate a restart). */
    private void wire(NotificationDAO notificationDao) {
        notifications = new NotificationService(notificationDao, new UserDAO(db), 1, 100);
        started.add(notifications);
        PlanningService planning = new PlanningService(new ShipmentValidator(clock), new EligibilityService(),
                new RouteEvaluator(new CostService(), new DurationService(), new EmissionService()),
                new RecommendationService());
        BookingDAO bookingDao = new BookingDAO(db);
        bookings = new BookingService(new ShipmentDAO(db), new RouteDAO(db), bookingDao, planning, clock, notifications);
        payments = new PaymentService(bookingDao, new PaymentDAO(db), notifications);
        tracking = new TrackingService(bookingDao, new TrackingDAO(db), notifications);
    }

    @AfterEach
    void stop() throws Exception {
        for (NotificationService n : started) {
            assertTrue(n.awaitIdle(WAIT));
            n.shutdown();
        }
        started.clear();
    }

    private static long shipment(User owner) throws DataAccessException {
        return new ShipmentDAO(db).insert(new Shipment(0, owner.getUserId(), "Mumbai", "Delhi", CargoType.GENERAL, 5000, "",
                DISPATCH, ShipmentStatus.DRAFT));
    }

    private long booked(User owner, long routeId) throws Exception {
        return bookings.book(owner, shipment(owner), routeId, "BALANCED");
    }

    private long confirmed(User owner, long routeId) throws Exception {
        long id = booked(owner, routeId);
        payments.simulate(owner, id, PaymentMethod.UPI, true);
        return id;
    }

    private List<Milestone> milestones(long bookingId) throws Exception {
        List<Milestone> result = new ArrayList<>();
        for (TrackingEvent e : tracking.eventsForAdmin(admin, bookingId)) {
            result.add(e.getMilestone());
        }
        return result;
    }

    private static String shipmentStatusOfBooking(long bookingId) throws Exception {
        return TestDatabase.queryString(db, "SELECT s.status FROM shipments s JOIN bookings b ON b.shipment_id = "
                + "s.shipment_id WHERE b.booking_id = " + bookingId);
    }

    @Test
    void successfulPaymentRecordsBookedInTheSameTransactionAndFailedPaymentDoesNot() throws Exception {
        long id = booked(alice, 2);
        payments.simulate(alice, id, PaymentMethod.UPI, false);
        assertEquals(List.of(), milestones(id));
        payments.simulate(alice, id, PaymentMethod.CARD, true);
        List<TrackingEvent> events = tracking.eventsForAdmin(admin, id);
        assertEquals(1, events.size());
        TrackingEvent booked = events.get(0);
        assertEquals(Milestone.BOOKED, booked.getMilestone());
        assertEquals(TrackingSource.SYSTEM, booked.getSource());
        assertEquals(PaymentService.BOOKED_TEXT, booked.getDescription());
        assertEquals(id, booked.getBookingId());
        assertEquals(TestDatabase.queryLong(db, "SELECT shipment_id FROM bookings WHERE booking_id = " + id),
                booked.getShipmentId());
        assertNull(booked.getRecordedByUserId());
    }

    @Test
    void onlyConfirmedBookingsCanBeTracked() throws Exception {
        long pending = booked(alice, 2);
        assertThrows(TrackingException.class, () -> tracking.advance(pending, null));
        assertThrows(TrackingException.class, () -> tracking.record(admin, pending, null, Milestone.BOOKED, "x"));
        bookings.cancel(alice, pending);
        assertThrows(TrackingException.class, () -> tracking.advance(pending, null));
        assertThrows(NotFoundException.class, () -> tracking.advance(999_999, null));
        assertEquals(0, TestDatabase.queryLong(db, "SELECT COUNT(*) FROM tracking"));
    }

    @Test
    void singleLegRouteSkipsTransferAndDeliveredIsTerminalAndCompletesTheShipment() throws Exception {
        long id = confirmed(alice, 2);
        Milestone current = Milestone.BOOKED;
        for (Milestone expected : List.of(Milestone.PICKED_UP, Milestone.IN_TRANSIT, Milestone.OUT_FOR_DELIVERY,
                Milestone.DELIVERED)) {
            assertEquals("BOOKED", shipmentStatusOfBooking(id));
            TrackingService.Result r = tracking.advance(id, current);
            assertEquals(TrackingService.Outcome.RECORDED, r.getOutcome());
            assertEquals(expected, r.getCurrent());
            current = expected;
        }
        assertEquals(Milestone.sequence(false), milestones(id));
        assertEquals("COMPLETED", shipmentStatusOfBooking(id));
        assertEquals(TrackingService.Outcome.TERMINAL, tracking.advance(id, Milestone.DELIVERED).getOutcome());
        assertEquals(TrackingService.Outcome.TERMINAL,
                tracking.record(admin, id, Milestone.DELIVERED, Milestone.DELIVERED, "again").getOutcome());
        assertEquals(5, milestones(id).size(), "nothing after DELIVERED");
        for (TrackingEvent e : tracking.eventsForAdmin(admin, id)) {
            if (e.getMilestone() != Milestone.BOOKED) {
                assertEquals(TrackingSource.SIMULATED, e.getSource());
            }
        }
    }

    @Test
    void multiLegRouteMustPassThroughTransfer() throws Exception {
        long id = confirmed(alice, 3);
        tracking.record(admin, id, Milestone.BOOKED, Milestone.PICKED_UP, "Collected at Mumbai depot");
        tracking.record(admin, id, Milestone.PICKED_UP, Milestone.IN_TRANSIT, "On the road leg");
        TrackingException skip = assertThrows(TrackingException.class,
                () -> tracking.record(admin, id, Milestone.IN_TRANSIT, Milestone.OUT_FOR_DELIVERY, "skip transfer"));
        assertTrue(skip.getMessage().startsWith("Only At transfer can follow In transit."), skip.getMessage());
        assertEquals(TrackingService.Outcome.RECORDED,
                tracking.record(admin, id, Milestone.IN_TRANSIT, Milestone.AT_TRANSFER, "Transferred to rail").getOutcome());
        TrackingEvent byAdmin = tracking.eventsForAdmin(admin, id).get(1);
        assertEquals(TrackingSource.ADMIN, byAdmin.getSource());
        assertEquals(admin.getUserId(), byAdmin.getRecordedByUserId());
        assertEquals("Collected at Mumbai depot", byAdmin.getDescription());
    }

    @Test
    void skippingRepeatingAndGoingBackAreRejected() throws Exception {
        long id = confirmed(alice, 2);
        assertThrows(TrackingException.class, () -> tracking.record(admin, id, Milestone.BOOKED, Milestone.IN_TRANSIT, "skip"));
        assertThrows(TrackingException.class, () -> tracking.record(admin, id, Milestone.BOOKED, Milestone.DELIVERED, "skip"));
        assertThrows(TrackingException.class, () -> tracking.record(admin, id, Milestone.BOOKED, Milestone.BOOKED, "repeat"));
        tracking.record(admin, id, Milestone.BOOKED, Milestone.PICKED_UP, "picked");
        assertThrows(TrackingException.class, () -> tracking.record(admin, id, Milestone.PICKED_UP, Milestone.BOOKED, "back"));
        assertThrows(TrackingException.class, () -> tracking.record(admin, id, Milestone.PICKED_UP, Milestone.IN_TRANSIT, " "));
        assertThrows(TrackingException.class, () -> tracking.advance(id, Milestone.AT_TRANSFER), "not on a 1-leg route");
        assertEquals(List.of(Milestone.BOOKED, Milestone.PICKED_UP), milestones(id));
    }

    @Test
    void repeatedAdvanceWithTheSameStartingPointRecordsOnce() throws Exception {
        long id = confirmed(alice, 2);
        assertEquals(TrackingService.Outcome.RECORDED, tracking.advance(id, Milestone.BOOKED).getOutcome());
        TrackingService.Result again = tracking.advance(id, Milestone.BOOKED);
        assertEquals(TrackingService.Outcome.ALREADY_ADVANCED, again.getOutcome());
        assertEquals(Milestone.PICKED_UP, again.getCurrent());
        assertEquals(TrackingService.Outcome.ALREADY_ADVANCED,
                tracking.record(admin, id, Milestone.BOOKED, Milestone.PICKED_UP, "double click").getOutcome());
        assertEquals(List.of(Milestone.BOOKED, Milestone.PICKED_UP), milestones(id));
    }

    @Test
    void concurrentAdvanceRecordsExactlyOneMilestone() throws Exception {
        long id = confirmed(alice, 2);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < THREADS; i++) {
                Callable<Object> task = () -> {
                    ready.countDown();
                    go.await();
                    try {
                        return tracking.advance(id, Milestone.BOOKED).getOutcome();
                    } catch (Exception e) {
                        return e;
                    }
                };
                futures.add(pool.submit(task));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            go.countDown();
            int recorded = 0;
            for (Future<Object> f : futures) {
                Object r = f.get(30, TimeUnit.SECONDS);
                assertTrue(r instanceof TrackingService.Outcome, "no exception expected: " + r);
                if (r == TrackingService.Outcome.RECORDED) {
                    recorded++;
                }
            }
            assertEquals(1, recorded);
        } finally {
            pool.shutdownNow();
        }
        assertEquals(List.of(Milestone.BOOKED, Milestone.PICKED_UP), milestones(id));
    }

    @Test
    void databaseRefusesDuplicateMilestonesAndDuplicateNotifications() throws Exception {
        long id = confirmed(alice, 2);
        long shipmentId = TestDatabase.queryLong(db, "SELECT shipment_id FROM bookings WHERE booking_id = " + id);
        assertThrows(SQLIntegrityConstraintViolationException.class, () -> TestDatabase.execute(db,
                "INSERT INTO tracking (booking_id, shipment_id, status, description, source) VALUES (" + id + ", "
                        + shipmentId + ", 'BOOKED', 'dup', 'ADMIN')"));
        assertThrows(SQLException.class, () -> TestDatabase.execute(db,
                "INSERT INTO tracking (booking_id, shipment_id, status, description, source) VALUES (" + id + ", "
                        + shipmentId + ", 'LOST', 'bad', 'ADMIN')"), "chk_tracking_status");
        assertTrue(notifications.awaitIdle(WAIT));
        assertThrows(SQLIntegrityConstraintViolationException.class, () -> TestDatabase.execute(db,
                "INSERT INTO notifications (user_id, type, booking_id, event_key, message) VALUES (" + alice.getUserId()
                        + ", 'BOOKING_CREATED', " + id + ", 'created', 'dup')"));
    }

    @Test
    void onlyTheAdministratorCanRecordOrListTracking() throws Exception {
        long id = confirmed(alice, 2);
        assertThrows(SecurityException.class, () -> tracking.record(alice, id, Milestone.BOOKED, Milestone.PICKED_UP, "x"));
        assertThrows(SecurityException.class, () -> tracking.record(null, id, Milestone.BOOKED, Milestone.PICKED_UP, "x"));
        assertThrows(SecurityException.class, () -> tracking.advance(alice, id, Milestone.BOOKED));
        assertThrows(SecurityException.class, () -> tracking.advance(null, id, Milestone.BOOKED));
        assertThrows(SecurityException.class, () -> tracking.confirmedBookings(bob));
        assertThrows(SecurityException.class, () -> tracking.eventsForAdmin(alice, id));
        assertEquals(1, tracking.confirmedBookings(admin).size());
        assertEquals(List.of(Milestone.BOOKED), milestones(id));
    }

    @Test
    void ownerIsNotifiedOfEveryEventOnceAndCanMarkThemRead() throws Exception {
        long id = booked(alice, 2);
        payments.simulate(alice, id, PaymentMethod.UPI, false);
        payments.simulate(alice, id, PaymentMethod.CARD, true);
        tracking.advance(id, Milestone.BOOKED);
        long cancelledId = booked(alice, 1);
        bookings.cancel(alice, cancelledId);
        notifications.notify(alice.getUserId(), NotificationType.BOOKING_CREATED, id, "created", "retry of the same event");
        assertTrue(notifications.awaitIdle(WAIT));

        List<Notification> list = notifications.list(alice);
        List<String> kinds = new ArrayList<>();
        for (Notification n : list) {
            kinds.add(n.getType() + (n.getBookingId() == id ? "" : "@cancelled"));
            assertEquals(alice.getUserId(), n.getUserId());
            assertFalse(n.isRead());
        }
        assertEquals(7, list.size(), kinds.toString());
        assertTrue(kinds.containsAll(List.of("BOOKING_CREATED", "PAYMENT_FAILED", "PAYMENT_SUCCEEDED", "TRACKING_UPDATE",
                "BOOKING_CREATED@cancelled", "BOOKING_CANCELLED@cancelled")), kinds.toString());
        assertEquals(2, kinds.stream().filter("TRACKING_UPDATE"::equals).count(), "BOOKED and PICKED_UP");
        assertEquals(7, notifications.unreadCount(alice));
        assertEquals(0, notifications.list(bob).size());
        assertEquals(0, notifications.unreadCount(bob));

        long first = list.get(0).getNotificationId();
        assertThrows(NotFoundException.class, () -> notifications.markRead(bob, first), "someone else's notification");
        assertThrows(NotFoundException.class, () -> notifications.markRead(alice, 999_999_999));
        notifications.markRead(alice, first);
        notifications.markRead(alice, first);
        assertEquals(6, notifications.unreadCount(alice));
        assertTrue(notifications.list(alice).stream().anyMatch(n -> n.getNotificationId() == first && n.isRead()
                && n.getReadAt() != null));
        assertEquals(6, notifications.markAllRead(alice));
        assertEquals(0, notifications.unreadCount(alice));
        assertEquals(0, notifications.markAllRead(bob));
    }

    @Test
    void theAdministratorReceivesNoNotifications() throws Exception {
        long id = confirmed(admin, 2);
        tracking.advance(id, Milestone.BOOKED);
        assertTrue(notifications.awaitIdle(WAIT));
        assertEquals(0, notifications.list(admin).size());
        assertEquals(0, TestDatabase.queryLong(db, "SELECT COUNT(*) FROM notifications"));
        assertEquals(0, notifications.getFailureCount());
    }

    @Test
    void notificationFailureNeverAffectsTheTransactionAndThePoolRecovers() throws Exception {
        AtomicInteger failNext = new AtomicInteger(3);
        wire(new NotificationDAO(db) {
            @Override
            public boolean insert(long userId, NotificationType type, long bookingId, String eventKey, String message)
                    throws DataAccessException {
                if (failNext.getAndDecrement() > 0) {
                    throw new DataAccessException("simulated notification store failure");
                }
                return super.insert(userId, type, bookingId, eventKey, message);
            }
        });
        long id = booked(alice, 2);
        payments.simulate(alice, id, PaymentMethod.UPI, true);
        assertTrue(notifications.awaitIdle(WAIT));
        assertEquals("CONFIRMED", TestDatabase.queryString(db, "SELECT status FROM bookings WHERE booking_id = " + id));
        assertEquals(List.of(Milestone.BOOKED), milestones(id));
        assertEquals(3, notifications.getFailureCount());
        assertEquals(0, notifications.unreadCount(alice), "the three notifications so far all failed");
        tracking.advance(id, Milestone.BOOKED);
        assertTrue(notifications.awaitIdle(WAIT));
        assertEquals(1, notifications.unreadCount(alice), "the same pool keeps working after failures");
    }

    @Test
    void notifyAfterShutdownIsDroppedWithoutAnExceptionAndAwaitIdleNeverHangs() throws Exception {
        long id = booked(alice, 2);
        assertTrue(notifications.awaitIdle(WAIT));
        notifications.shutdown();
        assertTrue(notifications.isShutdown());
        long failuresBefore = notifications.getFailureCount();
        notifications.notify(alice.getUserId(), NotificationType.BOOKING_CANCELLED, id, "late", "after shutdown");
        assertEquals(failuresBefore + 1, notifications.getFailureCount());
        assertTrue(notifications.awaitIdle(Duration.ofMillis(1)));
        assertEquals(1, notifications.unreadCount(alice), "only the booking notification from before shutdown");
    }

    @Test
    void trackingAndNotificationsSurviveARestart() throws Exception {
        long id = confirmed(alice, 3);
        tracking.advance(id, Milestone.BOOKED);
        assertTrue(notifications.awaitIdle(WAIT));
        notifications.markAllRead(alice);
        notifications.shutdown();

        wire(new NotificationDAO(db));
        assertEquals(List.of(Milestone.BOOKED, Milestone.PICKED_UP), milestones(id));
        assertEquals(0, notifications.unreadCount(alice));
        assertTrue(notifications.list(alice).stream().allMatch(Notification::isRead));
        assertEquals(Milestone.IN_TRANSIT, tracking.advance(id, Milestone.PICKED_UP).getCurrent());
        assertEquals(Milestone.AT_TRANSFER, tracking.advance(id, Milestone.IN_TRANSIT).getCurrent());
        assertTrue(notifications.awaitIdle(WAIT));
        assertEquals(2, notifications.unreadCount(alice));
    }

    @Test
    void schedulerPassAdvancesEveryConfirmedBookingByOneStep() throws Exception {
        long a = confirmed(alice, 2);
        long b = confirmed(bob, 3);
        long pending = booked(alice, 1);
        assertEquals(2, tracking.advanceAll());
        assertEquals(List.of(Milestone.BOOKED, Milestone.PICKED_UP), milestones(a));
        assertEquals(List.of(Milestone.BOOKED, Milestone.PICKED_UP), milestones(b));
        assertEquals(List.of(), milestones(pending));
        for (int i = 0; i < 10; i++) {
            tracking.advanceAll();
        }
        assertEquals(Milestone.sequence(false), milestones(a));
        assertEquals(Milestone.sequence(true), milestones(b));
        assertEquals(0, tracking.advanceAll(), "delivered bookings are left alone");
    }
}

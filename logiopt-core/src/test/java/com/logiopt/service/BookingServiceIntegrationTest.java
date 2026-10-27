package com.logiopt.service;

import com.logiopt.dao.BookingDAO;
import com.logiopt.dao.DBConnection;
import com.logiopt.dao.MigrationRunner;
import com.logiopt.dao.NotificationDAO;
import com.logiopt.dao.PaymentDAO;
import com.logiopt.dao.RouteDAO;
import com.logiopt.dao.ShipmentDAO;
import com.logiopt.dao.TestDatabase;
import com.logiopt.dao.UserDAO;
import com.logiopt.exception.BookingException;
import com.logiopt.exception.DataAccessException;
import com.logiopt.exception.NotFoundException;
import com.logiopt.model.Booking;
import com.logiopt.model.BookingSnapshot;
import com.logiopt.model.BookingStatus;
import com.logiopt.model.CargoType;
import com.logiopt.model.CostBreakdown;
import com.logiopt.model.Payment;
import com.logiopt.model.PaymentMethod;
import com.logiopt.model.PaymentStatus;
import com.logiopt.model.Role;
import com.logiopt.model.RouteLeg;
import com.logiopt.model.Shipment;
import com.logiopt.model.ShipmentStatus;
import com.logiopt.model.TransportMode;
import com.logiopt.model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Booking, snapshot, simulated payment and cancellation rules against the disposable test database
 * (LOGIOPT_TEST_DB_URL, name ending in _test), including real multi-thread double submissions.
 * "Today" is fixed at 2026-10-25.
 */
class BookingServiceIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 25);
    private static final LocalDate DISPATCH = LocalDate.of(2026, 11, 2);
    private static final int THREADS = 8;

    private static DBConnection db;

    private BookingService bookings;
    private PaymentService payments;
    private BookingDAO bookingDao;
    private NotificationService notifications;
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
                "DELETE FROM booking_legs", "DELETE FROM bookings",
                "DELETE FROM shipments", "DELETE FROM users WHERE user_id <> 1");
        TestDatabase.execute(db, "UPDATE routes SET name = 'Rail direct' WHERE route_id = 2",
                "UPDATE route_legs SET rate_per_tonne_km = 2.80, distance_km = 1450 WHERE route_id = 2");
        Clock clock = Clock.fixed(Instant.parse("2026-10-25T06:00:00Z"), ZoneOffset.UTC);
        PlanningService planning = new PlanningService(new ShipmentValidator(clock), new EligibilityService(),
                new RouteEvaluator(new CostService(), new DurationService(), new EmissionService()),
                new RecommendationService());
        bookingDao = new BookingDAO(db);
        UserDAO users = new UserDAO(db);
        notifications = new NotificationService(new NotificationDAO(db), users, 1, 100);
        bookings = new BookingService(new ShipmentDAO(db), new RouteDAO(db), bookingDao, planning, clock, notifications);
        payments = new PaymentService(bookingDao, new PaymentDAO(db), notifications);
        PasswordHasher hasher = new PasswordHasher(PasswordHasher.MIN_ITERATIONS, new SecureRandom());
        alice = user(users.insert("alice@example.test", "Alice", hasher.hash("x".toCharArray()), Role.CUSTOMER).get(),
                "alice@example.test", Role.CUSTOMER);
        bob = user(users.insert("bob@example.test", "Bob", hasher.hash("x".toCharArray()), Role.CUSTOMER).get(),
                "bob@example.test", Role.CUSTOMER);
        admin = user(users.insert("admin@logiopt.local", "Administrator", hasher.hash("x".toCharArray()), Role.ADMIN).get(),
                "admin@logiopt.local", Role.ADMIN);
    }

    @AfterEach
    void stopNotifications() throws Exception {
        assertTrue(notifications.awaitIdle(Duration.ofSeconds(10)));
        notifications.shutdown();
    }

    private static User user(long id, String email, Role role) {
        return new User(id, email, email, role, true);
    }

    private static long shipment(User owner, String source, String destination, double weight, LocalDate dispatch)
            throws DataAccessException {
        return new ShipmentDAO(db).insert(new Shipment(0, owner.getUserId(), source, destination, CargoType.GENERAL,
                weight, "", dispatch, ShipmentStatus.DRAFT));
    }

    private static long mumbaiDelhi(User owner) throws DataAccessException {
        return shipment(owner, "Mumbai", "Delhi", 5000, DISPATCH);
    }

    private static String shipmentStatus(long shipmentId) throws Exception {
        return TestDatabase.queryString(db, "SELECT status FROM shipments WHERE shipment_id = " + shipmentId);
    }

    @Test
    void snapshotCopiesTheServerEvaluationAndNeverChangesAfterwards() throws Exception {
        long shipmentId = mumbaiDelhi(alice);
        long bookingId = bookings.book(alice, shipmentId, 2, "balanced");
        Booking booking = bookings.view(alice, bookingId);
        BookingSnapshot s = booking.getSnapshot();
        assertEquals(BookingStatus.PENDING_PAYMENT, booking.getStatus());
        assertEquals("Rail direct", s.getRouteName());
        assertEquals(TransportMode.RAIL, s.getRouteMode());
        assertEquals(new BigDecimal("20300.00"), s.getCost().getTransport());
        assertEquals(new BigDecimal("2500.00"), s.getCost().getHandling());
        assertEquals(new BigDecimal("22800.00"), s.getTotalCost());
        assertEquals(38.22, s.getDurationHours());
        assertEquals(217.5, s.getEmissionsKg());
        assertEquals(0.9, s.getReliability());
        assertEquals(0.167971, s.getBalancedScore());
        assertEquals("BALANCED", s.getStrategyKey());
        assertTrue(s.getReason().startsWith("Balanced: Rail direct (route 2) has the lowest score 0.167971"), s.getReason());
        assertEquals(DISPATCH.atStartOfDay().plusMinutes(Math.round(38.22 * 60)), s.getEstimatedArrival());
        assertEquals(1, s.getLegs().size());
        RouteLeg leg = s.getLegs().get(0);
        assertEquals(TransportMode.RAIL, leg.getMode());
        assertEquals(1450, leg.getDistanceKm());
        assertEquals(new BigDecimal("2.8000"), leg.getRatePerTonneKm());
        assertEquals(45.0, leg.getSpeedKmh());
        assertEquals(6.0, leg.getBufferHours());
        assertEquals(0.03, leg.getEmissionFactorKgPerTonneKm());
        assertEquals("BOOKED", shipmentStatus(shipmentId));
        assertTrue(booking.getBookedAt() != null && booking.getConfirmedAt() == null && booking.getCancelledAt() == null);
        assertEquals(alice.getUserId(), booking.getOwnerUserId());

        TestDatabase.execute(db, "UPDATE routes SET name = 'Renamed route' WHERE route_id = 2",
                "UPDATE route_legs SET rate_per_tonne_km = 9.99, distance_km = 99 WHERE route_id = 2");
        BookingSnapshot after = bookings.view(alice, bookingId).getSnapshot();
        assertEquals("Rail direct", after.getRouteName());
        assertEquals(new BigDecimal("22800.00"), after.getTotalCost());
        assertEquals(new BigDecimal("2.8000"), after.getLegs().get(0).getRatePerTonneKm());
        assertEquals(1450, after.getLegs().get(0).getDistanceKm());
    }

    @Test
    void choosingANonRecommendedRouteRecordsWhyAndMissingMetricsStayNull() throws Exception {
        long shipmentId = mumbaiDelhi(alice);
        BookingSnapshot road = bookings.view(alice, bookings.book(alice, shipmentId, 1, "BALANCED")).getSnapshot();
        assertTrue(road.getReason().startsWith("Chosen by the customer instead of the Balanced recommendation Rail direct "
                + "(route 2)."), road.getReason());
        assertEquals(0.625, road.getBalancedScore());

        long ahmedabad = shipment(alice, "Ahmedabad", "Bengaluru", 5000, DISPATCH);
        BookingSnapshot rail = bookings.view(alice, bookings.book(alice, ahmedabad, 9, "GREENEST")).getSnapshot();
        assertNull(rail.getEmissionsKg());
        assertNull(rail.getLegs().get(0).getEmissionFactorKgPerTonneKm());
        assertNull(rail.getBalancedScore());
        assertTrue(rail.getReason().endsWith("Not considered by Greenest: Emissions not available."), rail.getReason());
        assertTrue(rail.getNotes().contains("Emissions not available"), rail.getNotes());
    }

    @Test
    void onlyTheOwnersEligibleShipmentWithAFutureDateCanBeBooked() throws Exception {
        long shipmentId = mumbaiDelhi(alice);
        assertThrows(NotFoundException.class, () -> bookings.book(bob, shipmentId, 2, "BALANCED"));
        assertThrows(NotFoundException.class, () -> bookings.book(admin, shipmentId, 2, "BALANCED"));
        assertThrows(NotFoundException.class, () -> bookings.book(alice, 999_999, 2, "BALANCED"));
        BookingException wrongPair = assertThrows(BookingException.class, () -> bookings.book(alice, shipmentId, 5, "BALANCED"));
        assertEquals("Route 5 is not an eligible route for this shipment.", wrongPair.getMessage());
        long heavy = shipment(alice, "Mumbai", "Delhi", 16000, DISPATCH);
        assertThrows(BookingException.class, () -> bookings.book(alice, heavy, 3, "BALANCED"), "route 3 capacity 15000 kg");
        long yesterday = shipment(alice, "Mumbai", "Delhi", 5000, TODAY.minusDays(1));
        BookingException past = assertThrows(BookingException.class, () -> bookings.book(alice, yesterday, 2, "BALANCED"));
        assertTrue(past.getMessage().startsWith("Booking needs a dispatch date of today or later."), past.getMessage());
        long today = shipment(alice, "Mumbai", "Delhi", 5000, TODAY);
        assertTrue(bookings.book(alice, today, 2, "BALANCED") > 0);
        assertEquals(0, TestDatabase.queryLong(db, "SELECT COUNT(*) FROM bookings WHERE shipment_id IN ("
                + shipmentId + ", " + heavy + ", " + yesterday + ")"));
    }

    @Test
    void secondActiveBookingIsRefusedByServiceAndDatabaseAndCancelFreesTheShipment() throws Exception {
        long shipmentId = mumbaiDelhi(alice);
        long first = bookings.book(alice, shipmentId, 2, "BALANCED");
        BookingException again = assertThrows(BookingException.class, () -> bookings.book(alice, shipmentId, 1, "BALANCED"));
        assertEquals("This shipment already has an active booking.", again.getMessage());
        assertThrows(SQLIntegrityConstraintViolationException.class, () -> TestDatabase.execute(db,
                "INSERT INTO bookings (shipment_id, route_id, route_name, route_mode, shipment_source, shipment_destination, "
                        + "cargo_type, weight_kg, dispatch_date, transport_cost, handling_cost, transfer_cost, waiting_cost, "
                        + "strategy_key, strategy_name, total_cost, recommendation_reason) SELECT shipment_id, route_id, "
                        + "route_name, route_mode, shipment_source, shipment_destination, cargo_type, weight_kg, dispatch_date, "
                        + "transport_cost, handling_cost, transfer_cost, waiting_cost, strategy_key, strategy_name, total_cost, "
                        + "recommendation_reason FROM bookings WHERE booking_id = " + first));
        assertThrows(NotFoundException.class, () -> bookings.cancel(bob, first));
        assertThrows(NotFoundException.class, () -> bookings.cancel(admin, first), "admin can view but not cancel");
        bookings.cancel(alice, first);
        Booking cancelled = bookings.view(alice, first);
        assertEquals(BookingStatus.CANCELLED, cancelled.getStatus());
        assertTrue(cancelled.getCancelledAt() != null);
        assertEquals("DRAFT", shipmentStatus(shipmentId));
        BookingException twice = assertThrows(BookingException.class, () -> bookings.cancel(alice, first));
        assertEquals("This booking is already cancelled.", twice.getMessage());
        BookingException payCancelled = assertThrows(BookingException.class,
                () -> payments.simulate(alice, first, PaymentMethod.UPI, true));
        assertEquals("Cancelled bookings cannot be paid.", payCancelled.getMessage());
        long second = bookings.book(alice, shipmentId, 1, "BALANCED");
        assertEquals("BOOKED", shipmentStatus(shipmentId));
        assertEquals(2, bookings.myBookings(alice).size());
        assertEquals(BookingStatus.PENDING_PAYMENT, bookings.activeBooking(alice, shipmentId).get().getStatus());
        assertEquals(second, bookings.activeBooking(alice, shipmentId).get().getBookingId());
    }

    @Test
    void failureInsideTheTransactionRollsEverythingBack() throws Exception {
        long shipmentId = mumbaiDelhi(alice);
        BookingSnapshot broken = new BookingSnapshot("Mumbai", "Delhi", CargoType.GENERAL, 5000, DISPATCH, 2,
                "Rail direct", TransportMode.RAIL,
                List.of(new RouteLeg(TransportMode.RAIL, 1450, new BigDecimal("2.80"), 45.0, 6.0, 0.03),
                        new RouteLeg(TransportMode.RAIL, 1e12, new BigDecimal("2.80"), 45.0, 6.0, 0.03)),
                new CostBreakdown(new BigDecimal("20300"), new BigDecimal("2500"), BigDecimal.ZERO, BigDecimal.ZERO),
                38.22, null, 217.5, 0.9, null, "BALANCED", "Balanced", "test", "");
        assertThrows(DataAccessException.class, () -> bookingDao.create(shipmentId, alice.getUserId(),
                BookingService.BOOKABLE, ShipmentStatus.BOOKED, broken), "second leg overflows DECIMAL(10,2)");
        assertEquals(0, TestDatabase.queryLong(db, "SELECT COUNT(*) FROM bookings"));
        assertEquals(0, TestDatabase.queryLong(db, "SELECT COUNT(*) FROM booking_legs"));
        assertEquals("DRAFT", shipmentStatus(shipmentId));
        assertTrue(bookings.book(alice, shipmentId, 2, "BALANCED") > 0, "nothing was left half-done");
    }

    @Test
    void failedPaymentCanBeRetriedAndPaidBookingCannotBeCancelled() throws Exception {
        long shipmentId = mumbaiDelhi(alice);
        long bookingId = bookings.book(alice, shipmentId, 2, "BALANCED");
        assertThrows(NotFoundException.class, () -> payments.simulate(bob, bookingId, PaymentMethod.UPI, true));
        assertThrows(NotFoundException.class, () -> payments.simulate(admin, bookingId, PaymentMethod.UPI, true),
                "admin can view but not pay");

        Payment failed = payments.simulate(alice, bookingId, PaymentMethod.UPI, false);
        assertEquals(PaymentStatus.FAILED, failed.getStatus());
        assertEquals(1, failed.getAttemptNo());
        assertEquals(new BigDecimal("22800.00"), failed.getAmount(), "amount from the snapshot");
        assertTrue(failed.getFailedAt() != null && failed.getPaidAt() == null);
        assertEquals(BookingStatus.PENDING_PAYMENT, bookings.view(alice, bookingId).getStatus());

        Payment paid = payments.simulate(alice, bookingId, PaymentMethod.NET_BANKING, true);
        assertEquals(PaymentStatus.PAID, paid.getStatus());
        assertEquals(2, paid.getAttemptNo());
        assertTrue(paid.getPaidAt() != null && paid.getFailedAt() == null);
        Booking confirmed = bookings.view(alice, bookingId);
        assertEquals(BookingStatus.CONFIRMED, confirmed.getStatus());
        assertTrue(confirmed.getConfirmedAt() != null);
        assertEquals("BOOKED", shipmentStatus(shipmentId));

        assertEquals("This booking is already paid.", assertThrows(BookingException.class,
                () -> payments.simulate(alice, bookingId, PaymentMethod.UPI, true)).getMessage());
        assertEquals("Paid bookings cannot be cancelled.", assertThrows(BookingException.class,
                () -> bookings.cancel(alice, bookingId)).getMessage());
        assertEquals(BookingStatus.CONFIRMED, bookings.view(admin, bookingId).getStatus(), "admin can view");
        assertEquals(2, payments.attempts(confirmed).size());
        assertEquals(0, TestDatabase.queryLong(db, "SELECT COUNT(*) FROM payments WHERE status = 'PENDING'"));
        assertThrows(SQLIntegrityConstraintViolationException.class, () -> TestDatabase.execute(db,
                "INSERT INTO payments (booking_id, attempt_no, amount, method, status, paid_at) VALUES (" + bookingId
                        + ", 3, 1, 'UPI', 'PAID', CURRENT_TIMESTAMP)"), "uk_payments_one_paid");
        assertThrows(SecurityException.class, () -> bookings.allBookings(alice));
        assertEquals(1, bookings.allBookings(admin).size());
    }

    private static <T> List<Object> race(Callable<T> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < THREADS; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    try {
                        return task.call();
                    } catch (Exception e) {
                        return e;
                    }
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            go.countDown();
            List<Object> results = Collections.synchronizedList(new ArrayList<>());
            for (Future<Object> f : futures) {
                results.add(f.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentDoubleSubmissionsCreateExactlyOneBooking() throws Exception {
        long shipmentId = mumbaiDelhi(alice);
        List<Object> results = race(() -> bookings.book(alice, shipmentId, 2, "BALANCED"));
        long created = results.stream().filter(r -> r instanceof Long).count();
        long refused = results.stream().filter(r -> r instanceof BookingException
                && "This shipment already has an active booking.".equals(((BookingException) r).getMessage())).count();
        assertEquals(1, created, results.toString());
        assertEquals(THREADS - 1, refused, "every other request is refused cleanly, no deadlock or 503: " + results);
        assertEquals(1, TestDatabase.queryLong(db, "SELECT COUNT(*) FROM bookings WHERE shipment_id = " + shipmentId));
        assertEquals(1, TestDatabase.queryLong(db, "SELECT COUNT(*) FROM booking_legs"));
    }

    @Test
    void concurrentPaymentSubmissionsChargeExactlyOnce() throws Exception {
        long bookingId = bookings.book(alice, mumbaiDelhi(alice), 2, "BALANCED");
        List<Object> results = race(() -> payments.simulate(alice, bookingId, PaymentMethod.UPI, true));
        long paid = results.stream().filter(r -> r instanceof Payment).count();
        long refused = results.stream().filter(r -> r instanceof BookingException
                && "This booking is already paid.".equals(((BookingException) r).getMessage())).count();
        assertEquals(1, paid, results.toString());
        assertEquals(THREADS - 1, refused, results.toString());
        assertEquals(1, TestDatabase.queryLong(db, "SELECT COUNT(*) FROM payments WHERE booking_id = " + bookingId));
        assertEquals(BookingStatus.CONFIRMED, bookings.view(alice, bookingId).getStatus());
    }

    @Test
    void concurrentPayAndCancelEndInOneConsistentState() throws Exception {
        long shipmentId = mumbaiDelhi(alice);
        long bookingId = bookings.book(alice, shipmentId, 2, "BALANCED");
        int[] turn = {0};
        List<Object> results = race(() -> {
            boolean pay;
            synchronized (turn) {
                pay = turn[0]++ % 2 == 0;
            }
            if (pay) {
                return payments.simulate(alice, bookingId, PaymentMethod.CARD, true);
            }
            bookings.cancel(alice, bookingId);
            return "cancelled";
        });
        assertTrue(results.stream().noneMatch(r -> r instanceof DataAccessException), results.toString());
        Booking end = bookings.view(alice, bookingId);
        long paidRows = TestDatabase.queryLong(db, "SELECT COUNT(*) FROM payments WHERE status = 'PAID'");
        if (end.getStatus() == BookingStatus.CONFIRMED) {
            assertEquals(1, paidRows);
            assertEquals("BOOKED", shipmentStatus(shipmentId));
            assertEquals(0, results.stream().filter("cancelled"::equals).count());
        } else {
            assertEquals(BookingStatus.CANCELLED, end.getStatus());
            assertEquals(0, paidRows);
            assertEquals("DRAFT", shipmentStatus(shipmentId));
            assertEquals(1, results.stream().filter("cancelled"::equals).count());
        }
    }
}

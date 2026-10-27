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
import com.logiopt.exception.InvalidShipmentException;
import com.logiopt.exception.NotFoundException;
import com.logiopt.exception.ShipmentNotEditableException;
import com.logiopt.model.Booking;
import com.logiopt.model.BookingSnapshot;
import com.logiopt.model.CargoType;
import com.logiopt.model.PaymentMethod;
import com.logiopt.model.Role;
import com.logiopt.model.Shipment;
import com.logiopt.model.ShipmentStatus;
import com.logiopt.model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Draft editing against the disposable test database (LOGIOPT_TEST_DB_URL, name ending in _test):
 * only the owner's DRAFT shipments can be edited, with the creation rules; booked, planned,
 * cancelled and completed shipments are refused and unchanged; booking snapshots are never touched.
 */
class ShipmentDraftEditIntegrationTest {

    private static final LocalDate DISPATCH = LocalDate.of(2026, 11, 2);

    private static DBConnection db;

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-25T06:00:00Z"), ZoneOffset.UTC);
    private NotificationService notifications;
    private ShipmentService service;
    private BookingService bookings;
    private PaymentService payments;
    private List<String> cities;
    private User alice;
    private User bob;

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
        alice = new User(users.insert("alice@example.test", "Alice", hasher.hash("x".toCharArray()), Role.CUSTOMER).get(),
                "alice@example.test", "Alice", Role.CUSTOMER, true);
        bob = new User(users.insert("bob@example.test", "Bob", hasher.hash("x".toCharArray()), Role.CUSTOMER).get(),
                "bob@example.test", "Bob", Role.CUSTOMER, true);
        ShipmentValidator validator = new ShipmentValidator(clock);
        PlanningService planning = new PlanningService(validator, new EligibilityService(),
                new RouteEvaluator(new CostService(), new DurationService(), new EmissionService()),
                new RecommendationService());
        notifications = new NotificationService(new NotificationDAO(db), users, 1, 100);
        BookingDAO bookingDao = new BookingDAO(db);
        bookings = new BookingService(new ShipmentDAO(db), new RouteDAO(db), bookingDao, planning, clock, notifications);
        payments = new PaymentService(bookingDao, new PaymentDAO(db), notifications);
        service = new ShipmentService(new ShipmentDAO(db), validator);
        cities = new RouteDAO(db).knownCities();
    }

    @AfterEach
    void stop() throws Exception {
        assertTrue(notifications.awaitIdle(Duration.ofSeconds(10)));
        notifications.shutdown();
    }

    private static long draft(User owner) throws Exception {
        return new ShipmentDAO(db).insert(new Shipment(0, owner.getUserId(), "Mumbai", "Delhi", CargoType.GENERAL, 5000,
                "", DISPATCH, ShipmentStatus.DRAFT));
    }

    private static Shipment values(String source, String destination, double weight, LocalDate dispatch) {
        return new Shipment(0, 0, source, destination, CargoType.FRAGILE, weight, "1 x 1 x 1 m", dispatch,
                ShipmentStatus.CANCELLED);
    }

    private static Shipment load(User owner, long id) throws Exception {
        return new ShipmentDAO(db).findByIdForUser(id, owner.getUserId()).orElseThrow();
    }

    private static String row(long id) throws Exception {
        return TestDatabase.queryString(db, "SELECT CONCAT_WS('|', user_id, source, destination, cargo_type, weight_kg, "
                + "IFNULL(dimensions, ''), dispatch_date, status) FROM shipments WHERE shipment_id = " + id);
    }

    private static String snapshot(long bookingId) throws Exception {
        return TestDatabase.queryString(db, "SELECT CONCAT_WS('|', shipment_source, shipment_destination, cargo_type, "
                + "weight_kg, dispatch_date, route_name, total_cost) FROM bookings WHERE booking_id = " + bookingId);
    }

    @Test
    void draftIsEditedWithTheCreationRules() throws Exception {
        long id = draft(alice);
        assertEquals(id, service.editableDraft(alice, id).getShipmentId());
        service.updateDraft(alice, id, values("Ahmedabad", "Bengaluru", 1234.567, DISPATCH.plusDays(3)), cities);
        Shipment edited = load(alice, id);
        assertEquals("Ahmedabad", edited.getSource());
        assertEquals("Bengaluru", edited.getDestination());
        assertEquals(CargoType.FRAGILE, edited.getCargoType());
        assertEquals(1234.567, edited.getWeightKg());
        assertEquals("1 x 1 x 1 m", edited.getDimensions());
        assertEquals(DISPATCH.plusDays(3), edited.getDispatchDate());
        assertEquals(ShipmentStatus.DRAFT, edited.getStatus(), "the posted status is ignored");
        assertEquals(alice.getUserId(), edited.getUserId());

        String before = row(id);
        for (Shipment invalid : List.of(values("Mumbai", "Delhi", 5000, LocalDate.of(2026, 10, 24)),
                values("Mumbai", "Delhi", 0, DISPATCH), values("Mumbai", "Delhi", 1_000_000_000, DISPATCH),
                values("Atlantis", "Delhi", 5000, DISPATCH), values("Mumbai", "Gotham", 5000, DISPATCH))) {
            assertThrows(InvalidShipmentException.class, () -> service.updateDraft(alice, id, invalid, cities),
                    invalid.getSource() + " " + invalid.getWeightKg() + " " + invalid.getDispatchDate());
        }
        assertEquals(before, row(id), "a rejected edit changes nothing");
    }

    @Test
    void someoneElsesShipmentIsNotFound() throws Exception {
        long id = draft(alice);
        String before = row(id);
        assertThrows(NotFoundException.class, () -> service.editableDraft(bob, id));
        assertThrows(NotFoundException.class,
                () -> service.updateDraft(bob, id, values("Mumbai", "Delhi", 1, DISPATCH), cities));
        assertThrows(NotFoundException.class, () -> service.editableDraft(alice, 999_999_999L));
        assertEquals(before, row(id));
    }

    @Test
    void bookedShipmentIsRefusedAndNeitherItNorTheSnapshotChanges() throws Exception {
        long id = draft(alice);
        long bookingId = bookings.book(alice, id, 2, "BALANCED");
        String shipmentBefore = row(id);
        String snapshotBefore = snapshot(bookingId);
        ShipmentNotEditableException refused = assertThrows(ShipmentNotEditableException.class,
                () -> service.updateDraft(alice, id, values("Mumbai", "Delhi", 1, DISPATCH), cities));
        assertTrue(refused.getMessage().contains("booked"), refused.getMessage());
        assertThrows(ShipmentNotEditableException.class, () -> service.editableDraft(alice, id));
        payments.simulate(alice, bookingId, PaymentMethod.UPI, true);
        assertThrows(ShipmentNotEditableException.class,
                () -> service.updateDraft(alice, id, values("Mumbai", "Delhi", 1, DISPATCH), cities));
        assertEquals(shipmentBefore, row(id));
        assertEquals(snapshotBefore, snapshot(bookingId));
    }

    @Test
    void cancelledAndCompletedShipmentsAreRefused() throws Exception {
        long cancelled = draft(alice);
        long completed = draft(alice);
        TestDatabase.execute(db, "UPDATE shipments SET status = 'CANCELLED' WHERE shipment_id = " + cancelled,
                "UPDATE shipments SET status = 'COMPLETED' WHERE shipment_id = " + completed);
        ShipmentNotEditableException c = assertThrows(ShipmentNotEditableException.class,
                () -> service.updateDraft(alice, cancelled, values("Mumbai", "Delhi", 1, DISPATCH), cities));
        assertTrue(c.getMessage().contains("cancelled"), c.getMessage());
        ShipmentNotEditableException d = assertThrows(ShipmentNotEditableException.class,
                () -> service.editableDraft(alice, completed));
        assertTrue(d.getMessage().contains("completed"), d.getMessage());
        assertTrue(row(cancelled).endsWith("|CANCELLED") && row(completed).endsWith("|COMPLETED"));
    }

    @Test
    void cancellingAnUnpaidBookingMakesTheDraftEditableAgainWithoutTouchingTheSnapshot() throws Exception {
        long id = draft(alice);
        long bookingId = bookings.book(alice, id, 2, "BALANCED");
        String snapshotBefore = snapshot(bookingId);
        bookings.cancel(alice, bookingId);
        assertEquals(ShipmentStatus.DRAFT, load(alice, id).getStatus());

        service.updateDraft(alice, id, values("Mumbai", "Delhi", 2500, DISPATCH.plusDays(1)), cities);
        assertEquals(2500, load(alice, id).getWeightKg());
        assertEquals(snapshotBefore, snapshot(bookingId), "the cancelled booking keeps the values it was booked with");
        Booking cancelledBooking = bookings.view(alice, bookingId);
        BookingSnapshot kept = cancelledBooking.getSnapshot();
        assertEquals(5000, kept.getWeightKg());
        assertEquals(DISPATCH, kept.getDispatchDate());

        long rebooked = bookings.book(alice, id, 2, "BALANCED");
        assertEquals(2500, bookings.view(alice, rebooked).getSnapshot().getWeightKg(), "a new booking uses the edit");
    }

    @Test
    void cancellingBackToANonDraftStatusKeepsTheShipmentLocked() throws Exception {
        long id = draft(alice);
        TestDatabase.execute(db, "UPDATE shipments SET status = 'PLANNED' WHERE shipment_id = " + id);
        long bookingId = bookings.book(alice, id, 2, "BALANCED");
        bookings.cancel(alice, bookingId);
        assertEquals(ShipmentStatus.PLANNED, load(alice, id).getStatus());
        String before = row(id);
        ShipmentNotEditableException refused = assertThrows(ShipmentNotEditableException.class,
                () -> service.updateDraft(alice, id, values("Mumbai", "Delhi", 1, DISPATCH), cities));
        assertTrue(refused.getMessage().contains("PLANNED"), refused.getMessage());
        assertEquals(before, row(id));
    }
}

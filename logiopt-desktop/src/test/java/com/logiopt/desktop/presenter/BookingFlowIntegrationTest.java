package com.logiopt.desktop.presenter;

import com.logiopt.app.AppServices;
import com.logiopt.app.CoreRuntime;
import com.logiopt.app.ShipmentFormInput;
import com.logiopt.dao.DBConnection;
import com.logiopt.dao.MigrationRunner;
import com.logiopt.dao.TestDatabase;
import com.logiopt.desktop.presenter.Models.ComparisonModel;
import com.logiopt.desktop.presenter.Models.StrategyCard;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Comparison, order summary, booking, payment and cancellation through the desktop presenters with
 * fake views and the real shared services on the disposable test database (LOGIOPT_TEST_DB_URL, name
 * ending in _test), on the seeded Mumbai -> Delhi 5000 kg dataset.
 */
class BookingFlowIntegrationTest {

    private static final String PASSWORD = "booking-pass-1";

    private static DBConnection db;
    private static CoreRuntime runtime;
    private static AppServices services;
    private static User alice;
    private static User bob;

    private final Fakes.CheckingRunner runner = new Fakes.CheckingRunner();

    @BeforeAll
    static void setUp() throws Exception {
        db = TestDatabase.connect();
        TestDatabase.dropAllTables(db);
        new MigrationRunner(db).migrate();
        runtime = new CoreRuntime(db, name -> null, Files.createTempDirectory("logiopt-booking"), "cp");
        services = runtime.services();
        AuthService auth = services.getAuthService();
        auth.register("alice@example.test", "Alice", PASSWORD.toCharArray());
        auth.register("bob@example.test", "Bob", PASSWORD.toCharArray());
        alice = auth.login("alice@example.test", PASSWORD.toCharArray(), "test").getUser();
        bob = auth.login("bob@example.test", PASSWORD.toCharArray(), "test").getUser();
    }

    @AfterAll
    static void stop() {
        if (runtime != null) {
            runtime.shutdown();
        }
    }

    private long shipment(User owner, String preference, LocalDate dispatch) {
        Map<String, String> values = new HashMap<>();
        values.put(ShipmentFormInput.SOURCE, "Mumbai");
        values.put(ShipmentFormInput.DESTINATION, "Delhi");
        values.put(ShipmentFormInput.CARGO_TYPE, "GENERAL");
        values.put(ShipmentFormInput.WEIGHT_KG, "5000");
        values.put(ShipmentFormInput.DIMENSIONS, "");
        values.put(ShipmentFormInput.DISPATCH_DATE, dispatch.toString());
        if (preference != null) {
            values.put(ShipmentFormInput.PREFERENCE, preference);
        }
        List<Long> saved = new ArrayList<>();
        Fakes.FormView form = new Fakes.FormView();
        ShipmentFormPresenter p = new ShipmentFormPresenter(services, runner, owner, saved::add, form);
        p.loadNew();
        p.submit(values);
        assertEquals(1, saved.size(), form.calls.toString());
        return saved.get(0);
    }

    private ComparisonModel compare(User user, long shipmentId, Fakes.ComparisonView view, List<Long> booked) {
        ComparisonPresenter p = new ComparisonPresenter(services, runner, user, booked::add, view);
        p.load(shipmentId);
        return view.model;
    }

    @Test
    void balancedRecommendsRoute2WithTheVerifiedScoresAndWinners() {
        long id = shipment(alice, null, LocalDate.now().plusDays(7));
        ComparisonModel m = compare(alice, id, new Fakes.ComparisonView(), new ArrayList<>());
        assertNotNull(m);
        assertEquals("BALANCED", m.preferredKey(), "no stored preference means BALANCED");
        assertEquals(2, m.recommendation().routeId());
        assertEquals("Balanced score 0.167971", m.recommendation().highlight());
        assertTrue(m.recommendation().reason().contains("0.361788"), "runner-up: " + m.recommendation().reason());
        Map<String, String> winners = new HashMap<>();
        for (StrategyCard c : m.strategies()) {
            winners.put(c.key(), c.winner());
        }
        assertEquals(Map.of("CHEAPEST", "#2 Rail direct", "FASTEST", "#1 Road direct", "GREENEST", "#4 Rail electric",
                "BALANCED", "#2 Rail direct"), winners);
        assertTrue(m.bookable());
        assertTrue(runner.tasks >= 3, "loaded in the background");
    }

    @Test
    void bookingGoesThroughTheOrderSummaryAndRefusesADuplicate() throws Exception {
        long id = shipment(alice, "GREENEST", LocalDate.now().plusDays(7));
        Fakes.ComparisonView view = new Fakes.ComparisonView();
        List<Long> booked = new ArrayList<>();
        ComparisonPresenter p = new ComparisonPresenter(services, runner, alice, booked::add, view);
        p.load(id);
        assertEquals(4, view.model.recommendation().routeId(), "Greenest preferred");
        long before = TestDatabase.queryLong(db, "SELECT COUNT(*) FROM bookings");
        p.review(4);
        assertEquals(4, view.order.routeId());
        assertEquals("GREENEST", view.order.selected(), "the preferred strategy is pre-selected");
        assertEquals(before, TestDatabase.queryLong(db, "SELECT COUNT(*) FROM bookings"), "reviewing books nothing");
        p.confirm(4, "GREENEST");
        assertEquals(1, booked.size(), view.calls.toString());
        assertEquals("GREENEST", TestDatabase.queryString(db, "SELECT strategy_key FROM bookings WHERE booking_id = "
                + booked.get(0)));
        assertEquals("Rail electric", TestDatabase.queryString(db, "SELECT route_name FROM bookings WHERE booking_id = "
                + booked.get(0)), "the snapshot is written by the service");

        p.confirm(2, "BALANCED");
        assertEquals("This shipment already has an active booking.", view.bookingError);
        assertEquals(1, booked.size());
        Fakes.ComparisonView again = new Fakes.ComparisonView();
        assertFalse(compare(alice, id, again, new ArrayList<>()).bookable());
        assertTrue(again.model.bookBlockedReason().contains("already has an active booking (#" + booked.get(0) + ")"));
    }

    @Test
    void aSecondConfirmWhileTheFirstRunsDoesNothing() throws Exception {
        long id = shipment(alice, null, LocalDate.now().plusDays(7));
        Fakes.ComparisonView view = new Fakes.ComparisonView();
        List<Long> booked = new ArrayList<>();
        Fakes.DeferredRunner deferred = new Fakes.DeferredRunner();
        ComparisonPresenter p = new ComparisonPresenter(services, deferred, alice, booked::add, view);
        p.load(id);
        deferred.runAll();
        p.confirm(2, "BALANCED");
        p.confirm(2, "BALANCED");
        assertEquals(1, deferred.pending(), "only one booking call");
        assertTrue(p.isBooking());
        assertEquals(1, view.busyOn, "the button was disabled once");
        deferred.runAll();
        assertEquals(1, booked.size());
        assertFalse(p.isBooking());
    }

    @Test
    void anotherUsersShipmentAndBookingLookMissing() throws Exception {
        long id = shipment(alice, null, LocalDate.now().plusDays(7));
        Fakes.ComparisonView mine = new Fakes.ComparisonView();
        List<Long> booked = new ArrayList<>();
        ComparisonPresenter p = new ComparisonPresenter(services, runner, alice, booked::add, mine);
        p.load(id);
        p.confirm(2, "BALANCED");
        Fakes.ComparisonView theirs = new Fakes.ComparisonView();
        compare(bob, id, theirs, new ArrayList<>());
        assertEquals(ErrorMessages.NOT_FOUND, theirs.error);
        Fakes.BookingView booking = new Fakes.BookingView();
        new BookingPresenter(services, runner, bob, () -> { }, booking).load(booked.get(0));
        assertEquals(ErrorMessages.NOT_FOUND, booking.error);
        Fakes.BookingListView list = new Fakes.BookingListView();
        new BookingListPresenter(services.getBookingService(), runner, bob, list).load();
        assertTrue(list.rows.stream().noneMatch(r -> r.id() == booked.get(0)));
    }

    @Test
    void aPastDatedShipmentCanBeComparedButBookingIsRefused() throws Exception {
        long id = shipment(alice, "FASTEST", LocalDate.now().plusDays(3));
        TestDatabase.execute(db, "UPDATE shipments SET dispatch_date = '2025-01-15' WHERE shipment_id = " + id);
        Fakes.ComparisonView view = new Fakes.ComparisonView();
        List<Long> booked = new ArrayList<>();
        ComparisonPresenter p = new ComparisonPresenter(services, runner, alice, booked::add, view);
        p.load(id);
        assertEquals(4, view.model.rows().size(), "still compared");
        assertFalse(view.model.bookable());
        assertTrue(view.model.bookBlockedReason().contains("in the past"), view.model.bookBlockedReason());
        p.review(1);
        assertEquals(null, view.order, "Review is not offered while booking is blocked");
        p.confirm(1, "FASTEST");
        assertTrue(view.bookingError.startsWith("Booking needs a dispatch date of today or later"), view.bookingError);
        assertTrue(booked.isEmpty());
    }

    @Test
    void paymentFailsThenSucceedsAndOnlyUnpaidBookingsCanBeCancelled() throws Exception {
        long unpaidShipment = shipment(alice, null, LocalDate.now().plusDays(7));
        List<Long> booked = new ArrayList<>();
        ComparisonPresenter first = new ComparisonPresenter(services, runner, alice, booked::add,
                new Fakes.ComparisonView());
        first.load(unpaidShipment);
        first.confirm(2, "BALANCED");
        long unpaid = booked.get(0);
        Fakes.BookingView cancelView = new Fakes.BookingView();
        int[] changes = {0};
        BookingPresenter cancel = new BookingPresenter(services, runner, alice, () -> changes[0]++, cancelView);
        cancel.load(unpaid);
        assertTrue(cancelView.model.canCancel() && cancelView.model.canPay());
        assertEquals("Not paid yet", cancelView.model.payment());
        cancel.cancel();
        assertEquals(BookingPresenter.CANCELLED, cancelView.actionMessage);
        assertEquals("Cancelled", cancelView.model.status());
        assertFalse(cancelView.model.canPay() || cancelView.model.canCancel());

        long paidShipment = shipment(alice, null, LocalDate.now().plusDays(8));
        ComparisonPresenter second = new ComparisonPresenter(services, runner, alice, booked::add,
                new Fakes.ComparisonView());
        second.load(paidShipment);
        second.confirm(1, "FASTEST");
        long bookingId = booked.get(1);
        Fakes.BookingView view = new Fakes.BookingView();
        BookingPresenter pay = new BookingPresenter(services, runner, alice, () -> changes[0]++, view);
        pay.load(bookingId);
        assertEquals("#1 Road direct", view.model.summary().route());
        assertEquals("Fastest", view.model.strategy());
        pay.pay("UPI", false);
        assertEquals(Boolean.FALSE, view.paid);
        assertEquals(BookingPresenter.FAILED, view.paymentMessage);
        assertEquals("Failed", view.model.payment());
        assertTrue(view.model.canPay(), "retry allowed");
        pay.pay("CARD", true);
        assertEquals(Boolean.TRUE, view.paid);
        assertEquals("Confirmed", view.model.status());
        assertEquals("Paid", view.model.payment());
        assertEquals(2, view.model.attempts().size());
        assertFalse(view.model.canPay() || view.model.canCancel());
        pay.cancel();
        assertEquals("Paid bookings cannot be cancelled.", view.actionMessage);
        assertEquals("CONFIRMED", TestDatabase.queryString(db, "SELECT status FROM bookings WHERE booking_id = " + bookingId));
        assertTrue(changes[0] >= 3, "the badge is refreshed after every change");
        assertTrue(services.getNotificationService().awaitIdle(Duration.ofSeconds(10)));

        Fakes.BookingListView list = new Fakes.BookingListView();
        new BookingListPresenter(services.getBookingService(), runner, alice, list).load();
        assertTrue(list.rows.stream().anyMatch(r -> r.id() == bookingId && r.status().equals("Confirmed")));
        pay.pay("NOT_A_METHOD", true);
        assertEquals("Choose a payment method.", view.paymentMessage);
    }

    @Test
    void thePreferenceRoundTripsThroughTheFormAndEditingIsRefusedOnceBooked() throws Exception {
        long id = shipment(alice, "FASTEST", LocalDate.now().plusDays(9));
        Fakes.FormView edit = new Fakes.FormView();
        ShipmentFormPresenter p = new ShipmentFormPresenter(services, runner, alice, x -> { }, edit);
        p.loadEdit(id);
        assertEquals("FASTEST", edit.model.values().get(ShipmentFormInput.PREFERENCE));
        assertEquals(4, edit.model.preferences().size());
        Map<String, String> values = new HashMap<>(edit.model.values());
        values.put(ShipmentFormInput.PREFERENCE, "CHEAPEST");
        List<Long> compared = new ArrayList<>();
        p.onSavedAndCompare(compared::add);
        p.submitAndCompare(values);
        assertEquals(List.of(id), compared, "Save and compare routes opens the comparison");
        assertEquals("CHEAPEST", TestDatabase.queryString(db,
                "SELECT optimisation_preference FROM shipments WHERE shipment_id = " + id));
        services.getBookingService().book(alice, id, 2, "CHEAPEST");
        Fakes.FormView late = new Fakes.FormView();
        new ShipmentFormPresenter(services, runner, alice, x -> { }, late).loadEdit(id);
        assertNotNull(late.unavailable);
        assertEquals("CHEAPEST", TestDatabase.queryString(db,
                "SELECT optimisation_preference FROM shipments WHERE shipment_id = " + id));
    }
}

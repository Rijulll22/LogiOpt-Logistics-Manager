package com.logiopt.desktop.support;

import com.logiopt.app.SchemaStatus;
import com.logiopt.exception.AccountChangeException;
import com.logiopt.exception.BookingException;
import com.logiopt.exception.DataAccessException;
import com.logiopt.exception.InvalidRegistrationException;
import com.logiopt.exception.InvalidShipmentException;
import com.logiopt.exception.NotFoundException;
import com.logiopt.exception.ShipmentNotEditableException;
import com.logiopt.model.BookingStatus;
import com.logiopt.model.CargoType;
import com.logiopt.model.PaymentStatus;
import com.logiopt.model.Role;
import com.logiopt.model.ShipmentStatus;
import com.logiopt.model.User;
import com.logiopt.report.ReportStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Session, Navigator, ErrorMessages, formats, chips and the start-up dialog text; no display, no database. */
class SupportTest {

    private static final User CUSTOMER = new User(7, "c@example.test", "Cara Customer", Role.CUSTOMER, true);
    private static final User ADMIN = new User(1, "admin@logiopt.local", "Admin", Role.ADMIN, true);

    /** Records what the navigator asked the window to draw. */
    private static final class RecordingShell implements Navigator.Shell {
        private final List<String> shown = new ArrayList<>();

        @Override
        public void showPublic(Screen screen, String notice) {
            shown.add("public " + screen + (notice == null ? "" : " | " + notice));
        }

        @Override
        public void showPage(Screen screen, Long shipmentId, String notice) {
            shown.add("page " + screen + (shipmentId == null ? "" : " #" + shipmentId) + (notice == null ? "" : " | " + notice));
        }

        String last() {
            return shown.get(shown.size() - 1);
        }
    }

    @Test
    void sessionHoldsOneUserAndRefusesWhenEmpty() {
        Session session = new Session();
        assertFalse(session.isSignedIn());
        assertThrows(IllegalStateException.class, session::require);
        assertThrows(IllegalArgumentException.class, () -> session.signIn(null));
        session.signIn(CUSTOMER);
        assertTrue(session.isSignedIn());
        assertFalse(session.isAdmin());
        assertEquals(CUSTOMER, session.require());
        session.signIn(ADMIN);
        assertTrue(session.isAdmin());
        session.signOut();
        assertTrue(session.user().isEmpty());
    }

    @Test
    void navigatorKeepsPagesBehindSignInAndSignOutReturnsToLogin() {
        Session session = new Session();
        RecordingShell shell = new RecordingShell();
        Navigator navigator = new Navigator(session, shell);
        assertNull(navigator.current());
        navigator.start();
        assertEquals("public LOGIN", shell.last());
        for (Screen page : List.of(Screen.DASHBOARD, Screen.SHIPMENTS, Screen.NEW_SHIPMENT, Screen.HISTORY)) {
            navigator.go(page);
            assertEquals("public LOGIN | " + Navigator.SIGN_IN_FIRST, shell.last(), page.name());
        }
        navigator.editShipment(5);
        assertEquals("public LOGIN | " + Navigator.SIGN_IN_FIRST, shell.last());
        navigator.go(Screen.REGISTER);
        assertEquals("public REGISTER", shell.last());

        navigator.signedIn(CUSTOMER);
        assertEquals("page DASHBOARD", shell.last());
        assertEquals(CUSTOMER, session.require());
        navigator.go(Screen.SHIPMENTS, "Shipment #3 saved.");
        assertEquals("page SHIPMENTS | Shipment #3 saved.", shell.last());
        navigator.editShipment(3);
        assertEquals("page EDIT_SHIPMENT #3", shell.last());
        assertEquals(Screen.EDIT_SHIPMENT, navigator.current());
        navigator.go(Screen.LOGIN);
        assertEquals("page DASHBOARD", shell.last(), "the sign-in card is not shown while signed in");
        assertThrows(IllegalArgumentException.class, () -> navigator.go(Screen.EDIT_SHIPMENT));

        navigator.signOut();
        assertEquals("public LOGIN | " + Navigator.SIGNED_OUT, shell.last());
        assertFalse(session.isSignedIn());
        navigator.go(Screen.DASHBOARD);
        assertEquals("public LOGIN | " + Navigator.SIGN_IN_FIRST, shell.last());
    }

    @Test
    void menuListsTheSignedInPagesInOrder() {
        assertEquals(List.of(Screen.DASHBOARD, Screen.SHIPMENTS, Screen.NEW_SHIPMENT, Screen.BOOKINGS,
                Screen.NOTIFICATIONS, Screen.REPORTS, Screen.HISTORY, Screen.ACCOUNT), Screen.menu());
        assertFalse(Screen.COMPARISON.isInMenu());
        assertFalse(Screen.BOOKING.isInMenu());
        assertTrue(Screen.ACCOUNT.requiresSignIn());
        assertFalse(Screen.EDIT_SHIPMENT.isInMenu());
        assertTrue(Screen.EDIT_SHIPMENT.requiresSignIn());
        assertFalse(Screen.LOGIN.requiresSignIn());
    }

    @Test
    void theMenuIsBuiltFromTheRole() {
        assertEquals(Screen.menu(), Screen.menu(false));
        assertEquals(List.of(Screen.DASHBOARD, Screen.ADMIN_USERS, Screen.ADMIN_BOOKINGS, Screen.ADMIN_TRACKING,
                Screen.ACCOUNT), Screen.menu(true));
        for (Screen s : Screen.menu(false)) {
            assertFalse(s.isAdminOnly(), s.name());
        }
        assertTrue(Screen.BOOKING.allows(true) && Screen.BOOKING.allows(false));
    }

    @Test
    void eachRoleOpensOnlyItsOwnPages() {
        Session session = new Session();
        RecordingShell shell = new RecordingShell();
        Navigator navigator = new Navigator(session, shell);
        navigator.signedIn(CUSTOMER);
        for (Screen admin : List.of(Screen.ADMIN_USERS, Screen.ADMIN_BOOKINGS, Screen.ADMIN_TRACKING)) {
            navigator.go(admin);
            assertEquals("page DASHBOARD | " + Navigator.NOT_AVAILABLE, shell.last(), admin.name());
        }
        navigator.go(Screen.NOTIFICATIONS);
        assertEquals("page NOTIFICATIONS", shell.last());
        navigator.go(Screen.REPORTS);
        assertEquals("page REPORTS", shell.last());
        navigator.signOut();
        navigator.signedIn(ADMIN);
        navigator.go(Screen.ADMIN_TRACKING);
        assertEquals("page ADMIN_TRACKING", shell.last());
        navigator.go(Screen.NOTIFICATIONS);
        assertEquals("page DASHBOARD | " + Navigator.NOT_AVAILABLE, shell.last());
        navigator.compare(17);
        assertEquals("page DASHBOARD | " + Navigator.NOT_AVAILABLE, shell.last());
        navigator.openBooking(12, null);
        assertEquals("page BOOKING #12", shell.last(), "the administrator opens bookings read-only");
    }

    @Test
    void securityRefusalsLookLikeMissingItems() {
        assertEquals(ErrorMessages.NOT_FOUND, ErrorMessages.of(new SecurityException("Administrator only")));
    }

    @Test
    void comparisonAndBookingPagesNeedAnIdAndASignedInUser() {
        Session session = new Session();
        RecordingShell shell = new RecordingShell();
        Navigator navigator = new Navigator(session, shell);
        navigator.compare(17);
        assertEquals("public LOGIN | " + Navigator.SIGN_IN_FIRST, shell.last());
        navigator.openBooking(12, null);
        assertEquals("public LOGIN | " + Navigator.SIGN_IN_FIRST, shell.last());
        navigator.signedIn(CUSTOMER);
        navigator.compare(17);
        assertEquals("page COMPARISON #17", shell.last());
        navigator.openBooking(12, "Booked.");
        assertEquals("page BOOKING #12 | Booked.", shell.last());
        navigator.go(Screen.BOOKINGS);
        assertEquals("page BOOKINGS", shell.last());
        assertThrows(IllegalArgumentException.class, () -> navigator.go(Screen.COMPARISON));
        assertThrows(IllegalArgumentException.class, () -> navigator.go(Screen.BOOKING));
    }

    @Test
    void everyStatusHasAPlainLanguageMeaning() {
        for (ShipmentStatus st : ShipmentStatus.values()) {
            assertFalse(StatusHelp.of(st).isBlank(), st.name());
        }
        for (BookingStatus st : BookingStatus.values()) {
            assertFalse(StatusHelp.of(st).isBlank(), st.name());
        }
        for (PaymentStatus st : PaymentStatus.values()) {
            assertFalse(StatusHelp.of(st).isBlank(), st.name());
        }
        for (ReportStatus st : ReportStatus.values()) {
            assertFalse(StatusHelp.of(st).isBlank(), st.name());
        }
        assertTrue(StatusHelp.of(BookingStatus.PENDING_PAYMENT).contains("pay or cancel"));
        assertTrue(StatusHelp.SHIPMENT_NOTE.startsWith("What do statuses mean?"));
        assertEquals("No payment has been attempted yet.", StatusHelp.of((PaymentStatus) null));
    }

    @Test
    void metricFormatsNeverShowMissingValuesAsZero() {
        assertEquals("38.22 h", Formats.hours(38.2222));
        assertEquals("217.50 kg CO2e", Formats.emissions(217.5));
        assertEquals("0.900", Formats.reliability(0.9));
        assertEquals("0.167971", Formats.score(0.1679714));
        assertEquals("1,450 km", Formats.km(1450));
        assertEquals("not available", Formats.hours(null));
        assertEquals("not available", Formats.emissions(null));
        assertEquals("not available", Formats.reliability(null));
        assertEquals("not available", Formats.score(null));
    }

    @Test
    void errorMessagesAreFriendlyAndRevealNothingInternal() {
        DataAccessException db = new DataAccessException("Failed to load jdbc:mysql://host/db as root",
                new SQLException("Access denied for user 'root'"));
        assertEquals(ErrorMessages.DATABASE_UNAVAILABLE, ErrorMessages.of(db));
        assertEquals(ErrorMessages.NOT_FOUND, ErrorMessages.of(new NotFoundException("Shipment 9 of user 3")));
        assertEquals("Only draft shipments can be edited.",
                ErrorMessages.of(new ShipmentNotEditableException("Only draft shipments can be edited.")));
        assertEquals("Paid bookings cannot be cancelled.",
                ErrorMessages.of(new BookingException("Paid bookings cannot be cancelled.")));
        assertEquals("Source is required. Weight must be a number.",
                ErrorMessages.of(new InvalidShipmentException(List.of("Source is required.", "Weight must be a number."))));
        assertEquals("Email is required.", ErrorMessages.of(new InvalidRegistrationException(List.of("Email is required."))));
        assertEquals("The current password is incorrect.",
                ErrorMessages.of(new AccountChangeException("The current password is incorrect.")));
        assertEquals(ErrorMessages.CLOSING, ErrorMessages.of(new RejectedExecutionException("pool")));
        String unexpected = ErrorMessages.of(new IllegalStateException("java.lang.NullPointerException at Foo.java:12"));
        assertEquals(ErrorMessages.UNEXPECTED, unexpected);
        assertFalse(unexpected.contains("Exception"));
    }

    @Test
    void formatsShowStoredValuesWithoutRounding() {
        assertEquals("1234.567 kg", Formats.weight(1234.567));
        assertEquals("5000 kg", Formats.weight(5000.0));
        assertEquals("999999999.999 kg", Formats.weight(999_999_999.999));
        assertEquals(Formats.NONE, Formats.weight(Double.NaN));
        assertEquals("INR 22,800.00", Formats.money(new BigDecimal("22800")));
        assertEquals(Formats.NONE, Formats.money(null));
        assertEquals("2026-11-03", Formats.date(LocalDate.of(2026, 11, 3)));
        assertEquals("2026-11-03 06:05", Formats.dateTime(LocalDateTime.of(2026, 11, 3, 6, 5, 59)));
        assertEquals("Mumbai → Delhi", Formats.route(" Mumbai ", "Delhi"));
        assertEquals("Pending payment", Formats.label(BookingStatus.PENDING_PAYMENT));
        assertEquals("Hazardous", Formats.label(CargoType.HAZARDOUS));
        assertEquals("1 booking", Formats.count(1, "booking", "bookings"));
        assertEquals("3 bookings", Formats.count(3, "booking", "bookings"));
    }

    @Test
    void chipColoursAreConsistent() {
        assertEquals(ChipKind.NEUTRAL, ChipKind.of(ShipmentStatus.DRAFT));
        assertEquals(ChipKind.INFO, ChipKind.of(ShipmentStatus.BOOKED));
        assertEquals(ChipKind.SUCCESS, ChipKind.of(ShipmentStatus.COMPLETED));
        assertEquals(ChipKind.DANGER, ChipKind.of(ShipmentStatus.CANCELLED));
        assertEquals(ChipKind.WARNING, ChipKind.of(BookingStatus.PENDING_PAYMENT));
        assertEquals(ChipKind.SUCCESS, ChipKind.of(BookingStatus.CONFIRMED));
        assertEquals(ChipKind.DANGER, ChipKind.of(BookingStatus.CANCELLED));
        assertEquals(ChipKind.INFO, ChipKind.of(ReportStatus.RUNNING));
        assertEquals(ChipKind.SUCCESS, ChipKind.of(ReportStatus.DONE));
        assertEquals(ChipKind.DANGER, ChipKind.of(ReportStatus.FAILED));
        assertEquals(ChipKind.NEUTRAL, ChipKind.of((ShipmentStatus) null));
    }

    @Test
    void schemaDialogNamesBothVersionsButNoCommand() {
        for (SchemaStatus status : List.of(new SchemaStatus(SchemaStatus.State.BEHIND, 7, 9),
                new SchemaStatus(SchemaStatus.State.FAILED, null, 9),
                new SchemaStatus(SchemaStatus.State.UNREACHABLE, null, 9))) {
            SchemaDialogText text = SchemaDialogText.of(status);
            assertTrue(text.getContent().contains("Required version: 9"), text.getContent());
            assertTrue(text.getContent().contains("Database version: "
                    + (status.getAppliedVersion() == null ? "unknown" : "7")), text.getContent());
            assertFalse(text.getContent().contains("java -cp"), "the command is only in the log");
            assertFalse(text.getContent().contains("--confirm-database"));
            assertTrue(text.getContent().contains("Retry"));
        }
        assertTrue(SchemaDialogText.of(new SchemaStatus(SchemaStatus.State.BEHIND, 7, 9)).getHeader().contains("upgraded"));
        assertTrue(SchemaDialogText.of(new SchemaStatus(SchemaStatus.State.UNREACHABLE, null, 9)).getHeader()
                .contains("could not be reached"));
    }
}

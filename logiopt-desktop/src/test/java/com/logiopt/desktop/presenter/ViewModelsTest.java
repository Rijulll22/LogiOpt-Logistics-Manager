package com.logiopt.desktop.presenter;

import com.logiopt.desktop.presenter.Models.DashboardModel;
import com.logiopt.desktop.presenter.Models.ShipmentFormModel;
import com.logiopt.desktop.presenter.Models.ShipmentRow;
import com.logiopt.desktop.support.ChipKind;
import com.logiopt.desktop.support.Formats;
import com.logiopt.desktop.support.StatusHelp;
import com.logiopt.model.AccountProfile;
import com.logiopt.model.AdminDashboard;
import com.logiopt.model.BookingStatus;
import com.logiopt.model.CargoType;
import com.logiopt.model.CustomerDashboard;
import com.logiopt.model.Milestone;
import com.logiopt.model.Role;
import com.logiopt.model.Shipment;
import com.logiopt.model.ShipmentStatus;
import com.logiopt.model.TrackingSummary;
import com.logiopt.model.User;
import com.logiopt.service.AuthService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** View models built from service results; pure, no database and no display. */
class ViewModelsTest {

    private static final User ASHA = new User(3, "asha@example.com", "Asha Verma", Role.CUSTOMER, true);

    @Test
    void customerDashboardHasFourCardsWithBreakdownsAndTrackingRows() {
        Map<ShipmentStatus, Integer> shipments = new EnumMap<>(ShipmentStatus.class);
        shipments.put(ShipmentStatus.DRAFT, 2);
        shipments.put(ShipmentStatus.BOOKED, 1);
        shipments.put(ShipmentStatus.PLANNED, 0);
        Map<BookingStatus, Integer> bookings = new EnumMap<>(BookingStatus.class);
        bookings.put(BookingStatus.CONFIRMED, 1);
        CustomerDashboard d = new CustomerDashboard(shipments, bookings, 4, List.of(
                new TrackingSummary(12, 17, "Rail direct", "Mumbai", "Delhi", Milestone.IN_TRANSIT,
                        LocalDateTime.of(2026, 10, 27, 18, 56, 30)),
                new TrackingSummary(13, 18, "Road direct", "Delhi", "Mumbai", null, null)), 20, true);
        DashboardModel model = ViewModels.customerDashboard(d, ASHA);
        assertEquals("Welcome back, Asha", model.heading());
        assertTrue(model.showTracking());
        assertEquals(List.of("Shipments", "Bookings", "Notifications", "Tracked bookings"),
                model.cards().stream().map(Models.StatCard::label).toList());
        assertEquals("3", model.cards().get(0).value());
        assertEquals("2 draft · 1 booked", model.cards().get(0).caption(), "zero counts are left out");
        assertEquals("1 confirmed", model.cards().get(1).caption());
        assertEquals("4", model.cards().get(2).value());
        assertEquals("2", model.cards().get(3).value());
        assertEquals(new Models.TrackingRow("#12", "Mumbai → Delhi", "Rail direct", "In transit", "2026-10-27 18:56"),
                model.tracking().get(0));
        assertEquals("Awaiting first milestone", model.tracking().get(1).milestone());
        assertEquals("Showing the latest 20 confirmed bookings.", model.trackingNote());
    }

    @Test
    void emptyDashboardSaysNoneYet() {
        DashboardModel model = ViewModels.customerDashboard(new CustomerDashboard(Map.of(), Map.of(), 0, List.of(), 20,
                false), new User(4, "x@example.test", " ", Role.CUSTOMER, true));
        assertEquals("Welcome back, there", model.heading());
        assertEquals("None yet", model.cards().get(0).caption());
        assertEquals("You are all caught up", model.cards().get(2).caption());
        assertTrue(model.tracking().isEmpty());
    }

    @Test
    void adminDashboardHasCountsOnly() {
        Map<Role, Integer> users = new EnumMap<>(Role.class);
        users.put(Role.CUSTOMER, 3);
        users.put(Role.ADMIN, 1);
        DashboardModel model = ViewModels.adminDashboard(new AdminDashboard(users, Map.of(BookingStatus.CONFIRMED, 2), 2));
        assertFalse(model.showTracking());
        assertEquals("4", model.cards().get(0).value());
        assertEquals("3 customer · 1 admin", model.cards().get(0).caption());
        assertEquals("2", model.cards().get(2).value());
    }

    @Test
    void shipmentRowsShowStoredValuesAndOnlyDraftsAreEditable() {
        Shipment draft = new Shipment(21, 3, "Bengaluru", "Chennai", CargoType.FRAGILE, 1234.567, "",
                LocalDate.of(2026, 11, 5), ShipmentStatus.DRAFT);
        ShipmentRow row = ViewModels.shipmentRow(draft);
        assertEquals(new ShipmentRow(21, "Bengaluru → Chennai", "Fragile", "1234.567 kg", "2026-11-05", "Draft",
                ChipKind.NEUTRAL, true, "Balanced", StatusHelp.of(ShipmentStatus.DRAFT)), row);
        for (ShipmentStatus status : List.of(ShipmentStatus.PLANNED, ShipmentStatus.BOOKED, ShipmentStatus.CANCELLED,
                ShipmentStatus.COMPLETED)) {
            Shipment s = new Shipment(1, 3, "A", "B", CargoType.GENERAL, 1, "", LocalDate.of(2026, 11, 5), status);
            assertFalse(ViewModels.shipmentRow(s).editable(), status.name());
        }
    }

    @Test
    void accountModelShowsTheProfileAndExplainsTheAdministratorPassword() {
        Models.AccountModel customer = ViewModels.account(new AccountProfile(3, "asha@example.com", "Asha Verma",
                Role.CUSTOMER, LocalDateTime.of(2026, 10, 21, 9, 30)));
        assertEquals(new Models.AccountModel("Asha Verma", "asha@example.com", "Customer", ChipKind.NEUTRAL, "2026-10-21",
                true, "At least 8 characters. You stay signed in after the change."), customer);
        Models.AccountModel admin = ViewModels.account(new AccountProfile(1, AuthService.ADMIN_EMAIL, "Admin", Role.ADMIN,
                null));
        assertFalse(admin.canChangePassword());
        assertEquals("Administrator", admin.role());
        assertEquals(AuthService.ADMIN_PASSWORD_FIXED, admin.passwordNote());
        assertEquals(Formats.NONE, admin.memberSince());
        assertTrue(AccountPresenter.byField(List.of("The new passwords do not match.")).containsKey(AccountPresenter.CONFIRM));
    }

    @Test
    void historyNotesSayWhenTheListIsCut() {
        assertEquals("Newest first.", ViewModels.note(false, 25));
        assertEquals("Newest first; showing the latest 25.", ViewModels.note(true, 25));
    }

    @Test
    void shipmentFormModelsForNewAndEdit() {
        ShipmentFormModel created = ShipmentFormPresenter.model(null, Map.of(), List.of("Delhi", "Mumbai"));
        assertNull(created.editId());
        assertEquals("New shipment", created.heading());
        assertEquals(List.of("GENERAL", "FRAGILE", "PERISHABLE", "HAZARDOUS"), created.cargoTypes());
        assertEquals("0.001 to 999,999,999.999 kg, at most 3 decimal places", created.weightHint());
        ShipmentFormModel edit = ShipmentFormPresenter.model(18L, Map.of("weightKg", "320"), List.of("Delhi"));
        assertEquals("Edit shipment #18", edit.heading());
        assertEquals("Save changes", edit.saveLabel());
        assertThrows(UnsupportedOperationException.class, () -> edit.cities().add("Pune"), "models are immutable");
    }

    @Test
    void registerErrorsAreGroupedByField() {
        Map<String, List<String>> grouped = RegisterPresenter.byField(List.of("Enter a valid email address.",
                "Name is required.", "Password must be at least 8 characters.", "Other."));
        assertEquals(List.of("Enter a valid email address."), grouped.get(RegisterPresenter.EMAIL));
        assertEquals(List.of("Name is required."), grouped.get(RegisterPresenter.NAME));
        assertEquals(List.of("Password must be at least 8 characters."), grouped.get(RegisterPresenter.PASSWORD));
        assertEquals(List.of("Other."), grouped.get(RegisterPresenter.GENERAL));
        assertEquals("No unread", ShellPresenter.badge(0));
        assertEquals("12 unread", ShellPresenter.badge(12));
    }
}

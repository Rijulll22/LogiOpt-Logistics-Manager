package com.logiopt.desktop.tools;

import com.logiopt.app.ShipmentFormInput;
import com.logiopt.desktop.presenter.Models.AccountModel;
import com.logiopt.desktop.presenter.Models.AdminTrackingRow;
import com.logiopt.desktop.presenter.Models.NotificationRow;
import com.logiopt.desktop.presenter.Models.NotificationsModel;
import com.logiopt.desktop.presenter.Models.ReportJobRow;
import com.logiopt.desktop.presenter.Models.ReportProgress;
import com.logiopt.desktop.presenter.Models.ReportsModel;
import com.logiopt.desktop.presenter.Models.ShipmentChoice;
import com.logiopt.desktop.presenter.Models.StepState;
import com.logiopt.desktop.presenter.Models.TimelineModel;
import com.logiopt.desktop.presenter.Models.TimelineStep;
import com.logiopt.desktop.presenter.Models.UserRow;
import com.logiopt.desktop.presenter.Models.BookingDetailModel;
import com.logiopt.desktop.presenter.Models.BookingRow;
import com.logiopt.desktop.presenter.Models.ComparisonModel;
import com.logiopt.desktop.presenter.Models.DashboardModel;
import com.logiopt.desktop.presenter.Models.HistoryModel;
import com.logiopt.desktop.presenter.Models.OrderSummaryModel;
import com.logiopt.desktop.presenter.Models.PaymentRow;
import com.logiopt.desktop.presenter.Models.ReportRow;
import com.logiopt.desktop.presenter.Models.RouteSummary;
import com.logiopt.desktop.presenter.Models.ShipmentFormModel;
import com.logiopt.desktop.presenter.Models.ShipmentRow;
import com.logiopt.desktop.presenter.Models.StatCard;
import com.logiopt.desktop.presenter.Models.TrackingRow;
import com.logiopt.desktop.presenter.ViewModels;
import com.logiopt.desktop.support.ChipKind;
import com.logiopt.desktop.support.Formats;
import com.logiopt.desktop.support.StatusHelp;
import com.logiopt.exception.LogiOptException;
import com.logiopt.model.BookingStatus;
import com.logiopt.model.CargoType;
import com.logiopt.model.OptimisationPreference;
import com.logiopt.model.PaymentStatus;
import com.logiopt.model.PlanningResult;
import com.logiopt.model.Recommendation;
import com.logiopt.model.Route;
import com.logiopt.model.RouteEvaluation;
import com.logiopt.model.RouteFactory;
import com.logiopt.model.RouteLeg;
import com.logiopt.model.RouteParams;
import com.logiopt.model.Shipment;
import com.logiopt.model.ShipmentStatus;
import com.logiopt.model.TransportMode;
import com.logiopt.report.ReportStatus;
import com.logiopt.service.AuthService;
import com.logiopt.service.CostService;
import com.logiopt.service.DurationService;
import com.logiopt.service.EligibilityService;
import com.logiopt.service.EmissionService;
import com.logiopt.service.PlanningService;
import com.logiopt.service.RecommendationService;
import com.logiopt.service.RouteEvaluator;
import com.logiopt.service.ShipmentValidator;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Fixed sample view models for screenshots and view smoke tests. The values mirror the seeded demo
 * data (Mumbai → Delhi, 5000 kg, Rail direct at INR 22,800.00); nothing here touches a database.
 */
public final class SampleData {

    /** Sample customer name. */
    public static final String NAME = "Asha Verma";
    /** Sample customer email. */
    public static final String EMAIL = "asha@example.com";
    /** The six seeded cities. */
    public static final List<String> CITIES = List.of("Ahmedabad", "Bengaluru", "Chennai", "Delhi", "Kolkata", "Mumbai");
    /** Cargo type names. */
    public static final List<String> CARGO = List.of("GENERAL", "FRAGILE", "PERISHABLE", "HAZARDOUS");

    private static final RecommendationService RECOMMENDATIONS = new RecommendationService();
    private static final PlanningService PLANNING = new PlanningService(
            new ShipmentValidator(Clock.fixed(Instant.parse("2026-10-27T00:00:00Z"), ZoneOffset.UTC)),
            new EligibilityService(), new RouteEvaluator(new CostService(), new DurationService(), new EmissionService()),
            RECOMMENDATIONS);

    private SampleData() {
    }

    /** @return a customer dashboard with counts and three tracked bookings */
    public static DashboardModel customerDashboard() {
        return new DashboardModel("Welcome back, Asha", "Your shipments and bookings at a glance.", List.of(
                new StatCard("Shipments", "5", "2 draft · 1 booked · 1 cancelled · 1 completed"),
                new StatCard("Bookings", "4", "1 pending payment · 2 confirmed · 1 cancelled"),
                new StatCard("Notifications", "3", "Unread in-app messages"),
                new StatCard("Tracked bookings", "3", "Confirmed bookings with milestones")), true, List.of(
                new TrackingRow("#12", "Mumbai → Delhi", "Rail direct", "Delivered", "2026-10-27 18:56"),
                new TrackingRow("#14", "Chennai → Kolkata", "Road-Rail-Road", "In transit", "2026-10-27 18:58"),
                new TrackingRow("#15", "Delhi → Mumbai", "Road direct", "Booked", "2026-10-27 19:04")),
                "The latest milestone of each confirmed booking.");
    }

    /** @return a new customer's dashboard (all zero, empty table) */
    public static DashboardModel emptyDashboard() {
        return new DashboardModel("Welcome back, Asha", "Your shipments and bookings at a glance.", List.of(
                new StatCard("Shipments", "0", "None yet"),
                new StatCard("Bookings", "0", "None yet"),
                new StatCard("Notifications", "0", "You are all caught up"),
                new StatCard("Tracked bookings", "0", "Confirmed bookings with milestones")), true, List.of(),
                "The latest milestone of each confirmed booking.");
    }

    /** @return the administrator's system overview */
    public static DashboardModel adminDashboard() {
        return new DashboardModel("System overview", "Counts across all customers. The disabled demo user is not counted.",
                List.of(new StatCard("Active users", "4", "3 customer · 1 admin"),
                        new StatCard("Bookings", "7", "2 pending payment · 4 confirmed · 1 cancelled"),
                        new StatCard("Awaiting tracking", "3", "Confirmed bookings not yet delivered")),
                false, List.of(), "");
    }

    /** @return five shipments in every status */
    public static List<ShipmentRow> shipments() {
        return List.of(
                row(21, "Bengaluru \u2192 Chennai", "General", "1200 kg", "2026-11-05", ShipmentStatus.DRAFT, "Fastest"),
                row(19, "Delhi \u2192 Mumbai", "Perishable", "750.5 kg", "2026-11-03", ShipmentStatus.BOOKED, "Balanced"),
                row(18, "Chennai \u2192 Kolkata", "Fragile", "320 kg", "2026-11-04", ShipmentStatus.DRAFT, "Greenest"),
                row(17, "Mumbai \u2192 Delhi", "General", "5000 kg", "2026-11-03", ShipmentStatus.COMPLETED, "Balanced"),
                row(16, "Ahmedabad \u2192 Bengaluru", "Hazardous", "2400 kg", "2026-11-02", ShipmentStatus.CANCELLED, "Cheapest"));
    }

    private static ShipmentRow row(long id, String route, String cargo, String weight, String dispatch,
                                   ShipmentStatus status, String preference) {
        return new ShipmentRow(id, route, cargo, weight, dispatch, Formats.label(status), ChipKind.of(status),
                status == ShipmentStatus.DRAFT, preference, StatusHelp.of(status));
    }

    /** @return three bookings in every status */
    public static List<BookingRow> bookings() {
        return List.of(
                new BookingRow(15, 19, "Delhi \u2192 Mumbai", "Road direct", "INR 33,180.00", "Pending payment",
                        ChipKind.WARNING, "2026-10-27 19:04", StatusHelp.of(BookingStatus.PENDING_PAYMENT)),
                new BookingRow(13, 18, "Chennai \u2192 Kolkata", "Rail freight", "INR 24,810.00", "Cancelled",
                        ChipKind.DANGER, "2026-10-27 18:57", StatusHelp.of(BookingStatus.CANCELLED)),
                new BookingRow(12, 17, "Mumbai \u2192 Delhi", "Rail direct", "INR 22,800.00", "Confirmed",
                        ChipKind.SUCCESS, "2026-10-27 18:56", StatusHelp.of(BookingStatus.CONFIRMED)));
    }

    /** @return history tables with the "latest 25" note on shipments */
    public static HistoryModel history() {
        return new HistoryModel(shipments(), "Newest first; showing the latest 25.", bookings(), "Newest first.", List.of(
                new ReportRow("32f1114b", 17, "Balanced", "Done", ChipKind.SUCCESS, "2026-10-27 18:56",
                        StatusHelp.of(ReportStatus.DONE)),
                new ReportRow("7c0d92e1", 18, "Greenest", "Failed", ChipKind.DANGER, "2026-10-27 18:58",
                        StatusHelp.of(ReportStatus.FAILED))), "Newest first.");
    }

    /** Which sample comparison to build. */
    public enum Variant {
        /** The four seeded Mumbai -> Delhi routes. */
        STANDARD,
        /** Plus a small truck that the 5000 kg shipment does not fit (rejected by eligibility). */
        EXCLUDED_ROUTE,
        /** Rail direct without an emission factor (its emissions are not available). */
        MISSING_METRIC,
        /** A saved shipment whose dispatch date has passed: comparable, Book disabled. */
        PAST_DATED,
        /** No route has emission data, so Greenest cannot recommend any route. */
        NO_GREENEST
    }

    /**
     * A comparison computed by the real planning engine and strategies on in-memory copies of the
     * seeded Mumbai -> Delhi routes (5000 kg GENERAL), exactly as the presenter would build it.
     *
     * @param preference shipment preference
     * @param variant    which sample
     * @return comparison model
     */
    public static ComparisonModel comparison(OptimisationPreference preference, Variant variant) {
        Shipment shipment = sampleShipment(preference, variant == Variant.PAST_DATED);
        PlanningResult result = plan(shipment, variant);
        Map<String, Recommendation> byStrategy = new LinkedHashMap<>();
        for (String key : ViewModels.STRATEGY_ORDER) {
            try {
                byStrategy.put(key, RECOMMENDATIONS.recommend(key, result.getEvaluations()));
            } catch (LogiOptException e) {
                throw new IllegalStateException(e);
            }
        }
        return ViewModels.comparison(shipment, result, byStrategy, Optional.empty(), variant != Variant.PAST_DATED);
    }

    /** @return "Review your order" for Rail direct (route 2) of the standard comparison */
    public static OrderSummaryModel orderSummary() {
        Shipment shipment = sampleShipment(OptimisationPreference.BALANCED, false);
        for (RouteEvaluation e : plan(shipment, Variant.STANDARD).getEvaluations()) {
            if (e.getRouteId() == 2) {
                return ViewModels.orderSummary(shipment, e);
            }
        }
        throw new IllegalStateException("route 2 missing");
    }

    /** @return booking #12 after one failed payment attempt, still unpaid */
    public static BookingDetailModel bookingDetail() {
        RouteSummary summary = orderSummary().summary();
        return new BookingDetailModel(12, 17, "Mumbai \u2192 Delhi", "Pending payment", ChipKind.WARNING,
                StatusHelp.of(BookingStatus.PENDING_PAYMENT), "Failed", ChipKind.DANGER, StatusHelp.of(PaymentStatus.FAILED),
                summary, "Balanced", "Recommended by Balanced: Rail direct (route 2) has the best overall mix of cost, "
                        + "time, emissions and reliability (score 0.167971).", "2026-10-27 18:56", "INR 22,800.00", true,
                true, List.of(new PaymentRow(1, "UPI", "Failed", ChipKind.DANGER, StatusHelp.of(PaymentStatus.FAILED),
                        "2026-10-27 18:57")), new TimelineModel(false, ViewModels.UNPAID_TIMELINE,
                List.of()));
    }

    /** Sample tracking events, by milestone position: time, source, description. */
    private static final String[][] EVENTS = {
        {"2026-10-27 18:58", "System", "Payment received; booking confirmed."},
        {"2026-10-28 09:10", "Administrator", "Collected from the Mumbai warehouse, 5 pallets."},
        {"2026-10-28 14:30", "Simulated", "Simulated progress: In transit."},
        {"2026-10-29 08:05", "Administrator", "Arrived at the Nagpur rail terminal for the transfer."},
        {"2026-10-30 07:40", "Simulated", "Simulated progress: Out for delivery."},
        {"2026-10-30 16:15", "Administrator", "Delivered and signed for at the Delhi depot."},
    };

    /**
     * @param recorded how many milestones are recorded (1 = Booked only)
     * @param multiLeg true for a route with a transfer
     * @return a sample timeline in the shape ViewModels.timeline builds
     */
    public static TimelineModel timeline(int recorded, boolean multiLeg) {
        List<String> labels = new ArrayList<>(List.of("Booked", "Picked up", "In transit", "At transfer",
                "Out for delivery", "Delivered"));
        List<String[]> events = new ArrayList<>(List.of(EVENTS));
        if (!multiLeg) {
            labels.remove(3);
            events.remove(3);
        }
        List<TimelineStep> steps = new ArrayList<>();
        for (int i = 0; i < labels.size(); i++) {
            if (i < recorded) {
                String[] e = events.get(i);
                steps.add(new TimelineStep(labels.get(i), i == recorded - 1 ? StepState.CURRENT : StepState.DONE, e[0],
                        e[1], e[2]));
            } else {
                steps.add(new TimelineStep(labels.get(i), i == recorded ? StepState.NEXT : StepState.LATER, "", "",
                        i == recorded ? "Next step; waiting for this update." : ""));
            }
        }
        String message = recorded >= labels.size() ? "Delivered. Tracking is complete."
                : "Now: " + labels.get(recorded - 1) + ". Next: " + labels.get(recorded) + "."
                        + (multiLeg ? " This route has 2 legs, so it includes At transfer." : "");
        return new TimelineModel(true, message, steps);
    }

    /** @return booking #12, paid, with the given timeline */
    public static BookingDetailModel confirmedBooking(TimelineModel timeline) {
        BookingDetailModel m = bookingDetail();
        return new BookingDetailModel(m.bookingId(), m.shipmentId(), m.title(), "Confirmed", ChipKind.SUCCESS,
                StatusHelp.of(BookingStatus.CONFIRMED), "Paid", ChipKind.SUCCESS, StatusHelp.of(PaymentStatus.PAID),
                m.summary(), m.strategy(), m.reason(), m.bookedAt(), m.amount(), false, false, List.of(m.attempts().get(0),
                        new PaymentRow(2, "Card", "Paid", ChipKind.SUCCESS, StatusHelp.of(PaymentStatus.PAID),
                                "2026-10-27 18:58")), timeline);
    }

    /** @return notifications, two of them unread (or none) */
    public static NotificationsModel notifications(boolean empty) {
        if (empty) {
            return new NotificationsModel(List.of(), "Nothing yet. Booking, payment and tracking updates appear here.", 0);
        }
        return new NotificationsModel(List.of(
                new NotificationRow(41, 12, "Tracking update", "Booking 12: In transit. Simulated progress: In transit.",
                        "2026-10-28 14:30", true),
                new NotificationRow(40, 12, "Tracking update", "Booking 12: Picked up. Collected from the Mumbai "
                        + "warehouse, 5 pallets.", "2026-10-28 09:10", true),
                new NotificationRow(39, 12, "Payment success", "Payment for booking 12 succeeded. Your booking is "
                        + "confirmed.", "2026-10-27 18:58", false),
                new NotificationRow(38, 12, "Booking created", "Booking 12 was created for shipment 17.",
                        "2026-10-27 18:56", false)), "2 unread of 4", 2);
    }

    /** @return the reports screen with two shipments and three reports (or nothing at all) */
    public static ReportsModel reports(boolean empty) {
        if (empty) {
            return new ReportsModel(List.of(), ViewModels.strategyOptions(), List.of(), "Newest first.");
        }
        return new ReportsModel(List.of(
                new ShipmentChoice(17, "#17  Mumbai → Delhi · 5,000 kg · 2026-11-03", "BALANCED"),
                new ShipmentChoice(18, "#18  Chennai → Kolkata · 1,200 kg · 2025-01-15", "GREENEST")),
                ViewModels.strategyOptions(), List.of(
                        new ReportJobRow("5be0c2a1-1111", "5be0c2a1", 17, "Balanced", "Running", ChipKind.INFO,
                                "2026-10-28 10:02", Formats.NONE, false, StatusHelp.of(ReportStatus.RUNNING)),
                        new ReportJobRow("32f1114b-2222", "32f1114b", 17, "Cheapest", "Done", ChipKind.SUCCESS,
                                "2026-10-27 18:56", "2026-10-27 18:56", true, StatusHelp.of(ReportStatus.DONE)),
                        new ReportJobRow("7c0d92e1-3333", "7c0d92e1", 18, "Greenest", "Failed", ChipKind.DANGER,
                                "2026-10-27 18:58", "2026-10-27 18:58", false, StatusHelp.of(ReportStatus.FAILED))),
                "Newest first.");
    }

    /** @return progress of a report job in the given status */
    public static ReportProgress progress(ReportStatus status) {
        switch (status) {
            case DONE:
                return new ReportProgress("32f1114b-2222", "Done", ChipKind.SUCCESS,
                        "Done. Use Save a copy… to keep the file.", true, true);
            case FAILED:
                return new ReportProgress("7c0d92e1-3333", "Failed", ChipKind.DANGER, ViewModels.REPORT_FAILED, true,
                        false);
            default:
                return new ReportProgress("5be0c2a1-1111", "Running", ChipKind.INFO, "Writing the report…", false,
                        false);
        }
    }

    /** @return every user, without password data */
    public static List<UserRow> users() {
        return List.of(
                new UserRow(1, "LogiOpt Administrator", AuthService.ADMIN_EMAIL, "Admin", ChipKind.INFO, "Enabled",
                        ChipKind.SUCCESS),
                new UserRow(2, "Demo User", "demo@logiopt.local", "Customer", ChipKind.NEUTRAL, "Disabled",
                        ChipKind.NEUTRAL),
                new UserRow(3, NAME, EMAIL, "Customer", ChipKind.NEUTRAL, "Enabled", ChipKind.SUCCESS),
                new UserRow(4, "Ravi Iyer", "ravi@example.test", "Customer", ChipKind.NEUTRAL, "Enabled",
                        ChipKind.SUCCESS));
    }

    /** @return confirmed, undelivered bookings for the administrator's tracking screen */
    public static List<AdminTrackingRow> adminTracking() {
        return List.of(
                new AdminTrackingRow(12, "Mumbai → Delhi", "Rail direct · 1 leg", "In transit", "IN_TRANSIT",
                        "Out for delivery", "OUT_FOR_DELIVERY", "2026-10-28 14:30"),
                new AdminTrackingRow(16, "Chennai → Delhi", "Road + rail · 2 legs", "Booked", "BOOKED",
                        "Picked up", "PICKED_UP", "2026-10-28 11:20"));
    }

    private static Shipment sampleShipment(OptimisationPreference preference, boolean past) {
        return new Shipment(17, 3, "Mumbai", "Delhi", CargoType.GENERAL, 5000, "", past ? LocalDate.of(2025, 1, 15)
                : LocalDate.of(2026, 11, 3), ShipmentStatus.DRAFT, preference);
    }

    private static PlanningResult plan(Shipment shipment, Variant variant) {
        boolean noFactors = variant == Variant.NO_GREENEST;
        List<Route> routes = new ArrayList<>();
        routes.add(route(TransportMode.ROAD, 1, "Road direct", "1500", 0.80, 20000,
                leg(TransportMode.ROAD, 1400, "4.50", 50.0, 2.0, noFactors ? null : 0.09)));
        routes.add(route(TransportMode.RAIL, 2, "Rail direct", "2500", 0.90, 30000,
                leg(TransportMode.RAIL, 1450, "2.80", 45.0, 6.0,
                        noFactors || variant == Variant.MISSING_METRIC ? null : 0.03)));
        routes.add(RouteFactory.create(TransportMode.MULTIMODAL, params(3, "Road-Rail-Road",
                leg(TransportMode.ROAD, 60, "4.50", 40.0, 0, noFactors ? null : 0.09),
                leg(TransportMode.RAIL, 1400, "2.80", 45.0, 4.0, noFactors ? null : 0.03),
                leg(TransportMode.ROAD, 50, "4.50", 40.0, 0, noFactors ? null : 0.09))
                .handlingCharge(new BigDecimal("2000")).transferChargePerTransfer(new BigDecimal("800"))
                .transferHoursPerTransfer(3).waitingHours(4).waitingRatePerHour(new BigDecimal("100"))
                .reliability(0.70).capacityKg(15000)));
        routes.add(route(TransportMode.RAIL, 4, "Rail electric", "2800", 0.85, 30000,
                leg(TransportMode.RAIL, 1450, "3.10", 40.0, 8.0, noFactors ? null : 0.02)));
        if (variant == Variant.EXCLUDED_ROUTE) {
            routes.add(route(TransportMode.ROAD, 5, "Road small truck", "900", 0.75, 3000,
                    leg(TransportMode.ROAD, 1400, "3.90", 55.0, 2.0, 0.08)));
        }
        try {
            return PLANNING.plan(shipment, routes, shipment.getPreference().getStrategyKey());
        } catch (LogiOptException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Route route(TransportMode mode, long id, String name, String handling, double reliability,
                               double capacity, RouteLeg leg) {
        return RouteFactory.create(mode, params(id, name, leg).handlingCharge(new BigDecimal(handling))
                .reliability(reliability).capacityKg(capacity));
    }

    private static RouteParams params(long id, String name, RouteLeg... legs) {
        return new RouteParams().routeId(id).name(name).source("Mumbai").destination("Delhi").legs(List.of(legs));
    }

    private static RouteLeg leg(TransportMode mode, double km, String rate, Double speed, double buffer, Double factor) {
        return new RouteLeg(mode, km, new BigDecimal(rate), speed, buffer, factor);
    }

    /** @return the empty new-shipment form */
    public static ShipmentFormModel newForm() {
        return new ShipmentFormModel("New shipment", "Choose the cities served by a configured route and what "
                + "matters most, then save it as a draft or compare routes.", null, CITIES, CARGO, Map.of(), "0.001 to 999,999,999.999 kg, at most 3 decimal places",
                "Save as draft", ViewModels.preferenceOptions());
    }

    /** @return a filled new-shipment form that fails validation (see {@link #formErrors()}) */
    public static ShipmentFormModel invalidForm() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(ShipmentFormInput.SOURCE, "Mumbai");
        values.put(ShipmentFormInput.DESTINATION, "Mumbai");
        values.put(ShipmentFormInput.CARGO_TYPE, "GENERAL");
        values.put(ShipmentFormInput.WEIGHT_KG, "0.0001");
        values.put(ShipmentFormInput.DIMENSIONS, "");
        values.put(ShipmentFormInput.DISPATCH_DATE, "2025-01-15");
        values.put(ShipmentFormInput.PREFERENCE, "GREENEST");
        return new ShipmentFormModel(newForm().heading(), newForm().intro(), null, CITIES, CARGO, values,
                newForm().weightHint(), "Save as draft", ViewModels.preferenceOptions());
    }

    /** @return the errors the validator gives for {@link #invalidForm()} */
    public static Map<String, List<String>> formErrors() {
        Map<String, List<String>> errors = new LinkedHashMap<>();
        errors.put(ShipmentFormInput.DESTINATION, List.of("Source and destination must be different."));
        errors.put(ShipmentFormInput.WEIGHT_KG, List.of("Weight must be at least 0.001 kg."));
        errors.put(ShipmentFormInput.DISPATCH_DATE, List.of("Dispatch date cannot be in the past."));
        return errors;
    }

    /** @return the sample customer's account screen */
    public static AccountModel account() {
        return new AccountModel(NAME, EMAIL, "Customer", ChipKind.NEUTRAL, "2026-10-21", true,
                "At least 8 characters. You stay signed in after the change.");
    }

    /** @return the administrator's account screen (password set by the environment) */
    public static AccountModel adminAccount() {
        return new AccountModel("LogiOpt Administrator", AuthService.ADMIN_EMAIL, "Administrator", ChipKind.INFO, "2026-10-25",
                false, AuthService.ADMIN_PASSWORD_FIXED);
    }

    /** @return the edit form of draft #18 */
    public static ShipmentFormModel editForm() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(ShipmentFormInput.SOURCE, "Chennai");
        values.put(ShipmentFormInput.DESTINATION, "Kolkata");
        values.put(ShipmentFormInput.CARGO_TYPE, "FRAGILE");
        values.put(ShipmentFormInput.WEIGHT_KG, "320");
        values.put(ShipmentFormInput.DIMENSIONS, "1.2 x 0.8 x 1 m");
        values.put(ShipmentFormInput.DISPATCH_DATE, "2026-11-04");
        values.put(ShipmentFormInput.PREFERENCE, "FASTEST");
        return new ShipmentFormModel("Edit shipment #18", "Only draft shipments can be edited. Booked, cancelled and "
                + "completed shipments stay as they are.", 18L, CITIES, CARGO, values,
                "0.001 to 999,999,999.999 kg, at most 3 decimal places", "Save changes", ViewModels.preferenceOptions());
    }
}

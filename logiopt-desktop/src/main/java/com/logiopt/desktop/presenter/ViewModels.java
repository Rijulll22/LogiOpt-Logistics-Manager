package com.logiopt.desktop.presenter;

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
import com.logiopt.desktop.presenter.Models.ChartBar;
import com.logiopt.desktop.presenter.Models.ChartModel;
import com.logiopt.desktop.presenter.Models.ComparisonModel;
import com.logiopt.desktop.presenter.Models.ComparisonRow;
import com.logiopt.desktop.presenter.Models.CostLine;
import com.logiopt.desktop.presenter.Models.DashboardModel;
import com.logiopt.desktop.presenter.Models.ExcludedRow;
import com.logiopt.desktop.presenter.Models.HistoryModel;
import com.logiopt.desktop.presenter.Models.LegRow;
import com.logiopt.desktop.presenter.Models.OrderSummaryModel;
import com.logiopt.desktop.presenter.Models.PaymentRow;
import com.logiopt.desktop.presenter.Models.PreferenceOption;
import com.logiopt.desktop.presenter.Models.RecommendationCard;
import com.logiopt.desktop.presenter.Models.ReportRow;
import com.logiopt.desktop.presenter.Models.RouteSummary;
import com.logiopt.desktop.presenter.Models.ShipmentRow;
import com.logiopt.desktop.presenter.Models.StatCard;
import com.logiopt.desktop.presenter.Models.StrategyCard;
import com.logiopt.desktop.presenter.Models.StrategyOption;
import com.logiopt.desktop.presenter.Models.TrackingRow;
import com.logiopt.desktop.support.ChipKind;
import com.logiopt.desktop.support.Formats;
import com.logiopt.desktop.support.StatusHelp;
import com.logiopt.model.AccountProfile;
import com.logiopt.service.TrackingService;
import com.logiopt.model.TrackingSource;
import com.logiopt.model.TrackingEvent;
import com.logiopt.model.Notification;
import com.logiopt.model.Milestone;
import com.logiopt.model.AdminDashboard;
import com.logiopt.model.Booking;
import com.logiopt.model.BookingSnapshot;
import com.logiopt.model.BookingStatus;
import com.logiopt.model.CostBreakdown;
import com.logiopt.model.CustomerDashboard;
import com.logiopt.model.ExcludedRoute;
import com.logiopt.model.OptimisationPreference;
import com.logiopt.model.Payment;
import com.logiopt.model.PaymentStatus;
import com.logiopt.model.PlanningResult;
import com.logiopt.model.Recommendation;
import com.logiopt.model.RejectedRoute;
import com.logiopt.model.Role;
import com.logiopt.model.RouteEvaluation;
import com.logiopt.model.RouteLeg;
import com.logiopt.model.Shipment;
import com.logiopt.model.ShipmentStatus;
import com.logiopt.model.TrackingSummary;
import com.logiopt.model.User;
import com.logiopt.report.ReportRecord;
import com.logiopt.service.AuthService;
import com.logiopt.service.HistoryService;
import com.logiopt.strategy.BalancedStrategy;
import com.logiopt.strategy.CheapestStrategy;
import com.logiopt.strategy.FastestStrategy;
import com.logiopt.strategy.GreenestStrategy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Builds the display-ready {@link Models} from service results; pure functions, no I/O. */
public final class ViewModels {

    /** Caption of a count with nothing in it. */
    static final String NONE_YET = "None yet";
    /** Shipment statuses shown on the dashboard; PLANNED is not used by the application. */
    static final ShipmentStatus[] SHOWN_SHIPMENT_STATUSES = {ShipmentStatus.DRAFT, ShipmentStatus.BOOKED,
        ShipmentStatus.COMPLETED, ShipmentStatus.CANCELLED};
    /** Strategies in display order. */
    public static final List<String> STRATEGY_ORDER = List.of(CheapestStrategy.KEY, FastestStrategy.KEY,
            GreenestStrategy.KEY, BalancedStrategy.KEY);

    private ViewModels() {
    }

    /**
     * @param d    customer counts
     * @param user signed-in customer
     * @return dashboard with stat cards and the tracking table
     */
    public static DashboardModel customerDashboard(CustomerDashboard d, User user) {
        List<StatCard> cards = new ArrayList<>();
        cards.add(new StatCard("Shipments", String.valueOf(d.getShipmentTotal()),
                breakdown(d.getShipmentsByStatus(), SHOWN_SHIPMENT_STATUSES)));
        cards.add(new StatCard("Bookings", String.valueOf(d.getBookingTotal()),
                breakdown(d.getBookingsByStatus(), BookingStatus.values())));
        cards.add(new StatCard("Notifications", String.valueOf(d.getUnreadNotifications()),
                d.getUnreadNotifications() == 0 ? "You are all caught up" : "Unread in-app messages"));
        cards.add(new StatCard("Tracked bookings", String.valueOf(d.getTracking().size()),
                "Confirmed bookings with milestones"));
        List<TrackingRow> rows = new ArrayList<>();
        for (TrackingSummary t : d.getTracking()) {
            rows.add(new TrackingRow("#" + t.getBookingId(), Formats.route(t.getSource(), t.getDestination()),
                    t.getRouteName(), t.getLatest() == null ? "Awaiting first milestone" : t.getLatest().getLabel(),
                    Formats.dateTime(t.getLatestAt())));
        }
        String note = d.isTrackingTruncated()
                ? "Showing the latest " + d.getTrackingLimit() + " confirmed bookings."
                : "The latest milestone of each confirmed booking.";
        return new DashboardModel("Welcome back, " + firstName(user), "Your shipments and bookings at a glance.", cards,
                true, rows, note);
    }

    /**
     * @param d system-wide counts
     * @return administrator dashboard (counts only)
     */
    public static DashboardModel adminDashboard(AdminDashboard d) {
        List<StatCard> cards = new ArrayList<>();
        cards.add(new StatCard("Active users", String.valueOf(d.getUserTotal()),
                breakdown(d.getUsersByRole(), Role.values())));
        cards.add(new StatCard("Bookings", String.valueOf(d.getBookingTotal()),
                breakdown(d.getBookingsByStatus(), BookingStatus.values())));
        cards.add(new StatCard("Awaiting tracking", String.valueOf(d.getConfirmedAwaitingTracking()),
                "Confirmed bookings not yet delivered"));
        return new DashboardModel("System overview", "Counts across all customers. The disabled demo user is not counted.",
                cards, false, List.of(), "");
    }

    /**
     * @param p the signed-in user's profile
     * @return the account screen; the administrator's password card explains where it is set
     */
    public static AccountModel account(AccountProfile p) {
        boolean admin = p.getRole() == Role.ADMIN;
        return new AccountModel(p.getName(), p.getEmail(), admin ? "Administrator" : "Customer",
                admin ? ChipKind.INFO : ChipKind.NEUTRAL,
                p.getCreatedAt() == null ? Formats.NONE : Formats.date(p.getCreatedAt().toLocalDate()), !admin,
                admin ? AuthService.ADMIN_PASSWORD_FIXED
                        : "At least " + AuthService.MIN_PASSWORD_LENGTH + " characters. You stay signed in after the change.");
    }

    /** @return one shipments-table row */
    public static ShipmentRow shipmentRow(Shipment s) {
        return new ShipmentRow(s.getShipmentId(), Formats.route(s.getSource(), s.getDestination()),
                Formats.label(s.getCargoType()), Formats.weight(s.getWeightKg()), Formats.date(s.getDispatchDate()),
                Formats.label(s.getStatus()), ChipKind.of(s.getStatus()), s.getStatus() == ShipmentStatus.DRAFT,
                s.getPreference().getLabel(), StatusHelp.of(s.getStatus()));
    }

    /** @return shipments-table rows in the given order */
    public static List<ShipmentRow> shipmentRows(List<Shipment> shipments) {
        List<ShipmentRow> rows = new ArrayList<>();
        for (Shipment s : shipments) {
            rows.add(shipmentRow(s));
        }
        return rows;
    }

    /** @return one bookings-table row (route and cost from the booking's snapshot) */
    public static BookingRow bookingRow(Booking b) {
        BookingSnapshot snap = b.getSnapshot();
        return new BookingRow(b.getBookingId(), b.getShipmentId(),
                snap == null ? Formats.NONE : Formats.route(snap.getSource(), snap.getDestination()),
                snap == null ? Formats.NONE : snap.getRouteName(),
                snap == null ? Formats.NONE : Formats.money(snap.getTotalCost()),
                Formats.label(b.getStatus()), ChipKind.of(b.getStatus()), Formats.dateTime(b.getBookedAt()),
                StatusHelp.of(b.getStatus()));
    }

    /** @return one reports-table row */
    public static ReportRow reportRow(ReportRecord r) {
        String job = r.getJobId() == null ? Formats.NONE
                : r.getJobId().length() > 8 ? r.getJobId().substring(0, 8) : r.getJobId();
        String strategy = r.getStrategyKey() == null || r.getStrategyKey().isEmpty() ? Formats.NONE
                : r.getStrategyKey().charAt(0) + r.getStrategyKey().substring(1).toLowerCase();
        return new ReportRow(job, r.getShipmentId(), strategy, Formats.label(r.getStatus()), ChipKind.of(r.getStatus()),
                Formats.dateTime(r.getCreatedAt()), StatusHelp.of(r.getStatus()));
    }

    /** @return the three history tables with their notes */
    public static HistoryModel history(HistoryService.History h) {
        List<BookingRow> bookings = new ArrayList<>();
        for (Booking b : h.getBookings()) {
            bookings.add(bookingRow(b));
        }
        List<ReportRow> reports = new ArrayList<>();
        for (ReportRecord r : h.getReports()) {
            reports.add(reportRow(r));
        }
        return new HistoryModel(shipmentRows(h.getShipments()), note(h.isMoreShipments(), h.getLimit()), bookings,
                note(h.isMoreBookings(), h.getLimit()), reports, note(h.isMoreReports(), h.getLimit()));
    }

    /** @return the four preference choices of the shipment form, in display order */
    public static List<PreferenceOption> preferenceOptions() {
        List<PreferenceOption> options = new ArrayList<>();
        for (OptimisationPreference p : OptimisationPreference.values()) {
            options.add(new PreferenceOption(p.name(), p.getLabel(), p.getDescription()));
        }
        return options;
    }

    /** @return "#2 Rail direct" */
    static String routeLabel(long routeId, String name) {
        return "#" + routeId + " " + name;
    }

    /**
     * Builds the comparison screen from the services' results; no value is calculated here, the best
     * value of each column is only picked from the evaluations.
     *
     * @param shipment     the shipment
     * @param result       planning result for the preferred strategy
     * @param byStrategy   every strategy's recommendation over the same evaluations, by key
     * @param active       the shipment's active booking, if any
     * @param dateBookable whether the dispatch date allows booking
     * @return comparison model
     */
    public static ComparisonModel comparison(Shipment shipment, PlanningResult result,
                                             Map<String, Recommendation> byStrategy, Optional<Booking> active,
                                             boolean dateBookable) {
        String preferred = shipment.getPreference().getStrategyKey();
        Recommendation pick = byStrategy.get(preferred);
        long chosenId = pick != null && pick.isRecommended() ? pick.getChosen().getRouteId() : 0;
        List<RouteEvaluation> evaluations = result.getEvaluations();

        BigDecimal minCost = null;
        Double minDuration = null;
        Double minEmissions = null;
        Double maxReliability = null;
        for (RouteEvaluation e : evaluations) {
            if (e.getTotalCost() != null && (minCost == null || e.getTotalCost().compareTo(minCost) < 0)) {
                minCost = e.getTotalCost();
            }
            if (e.getDurationHours() != null && (minDuration == null || e.getDurationHours() < minDuration)) {
                minDuration = e.getDurationHours();
            }
            if (e.getEmissionsKg() != null && (minEmissions == null || e.getEmissionsKg() < minEmissions)) {
                minEmissions = e.getEmissionsKg();
            }
            if (e.getReliability() != null && (maxReliability == null || e.getReliability() > maxReliability)) {
                maxReliability = e.getReliability();
            }
        }
        List<ComparisonRow> rows = new ArrayList<>();
        Map<Long, String> legend = new LinkedHashMap<>();
        List<ChartBar> costBars = new ArrayList<>();
        List<ChartBar> durationBars = new ArrayList<>();
        List<ChartBar> emissionBars = new ArrayList<>();
        List<String> noDuration = new ArrayList<>();
        List<String> noEmissions = new ArrayList<>();
        for (RouteEvaluation e : evaluations) {
            String label = routeLabel(e.getRouteId(), e.getRouteName());
            legend.put(e.getRouteId(), label);
            rows.add(new ComparisonRow(e.getRouteId(), label, e.getMode().name(), Formats.money(e.getTotalCost()),
                    e.getTotalCost() != null && e.getTotalCost().compareTo(minCost) == 0,
                    Formats.hours(e.getDurationHours()), e.getDurationHours() != null && e.getDurationHours().equals(minDuration),
                    Formats.emissions(e.getEmissionsKg()), e.getEmissionsKg() != null && e.getEmissionsKg().equals(minEmissions),
                    Formats.reliability(e.getReliability()),
                    e.getReliability() != null && e.getReliability().equals(maxReliability), e.getRouteId() == chosenId));
            String bar = "#" + e.getRouteId();
            costBars.add(new ChartBar(e.getRouteId(), bar, e.getTotalCost().doubleValue(), Formats.money(e.getTotalCost())));
            if (e.getDurationHours() != null) {
                durationBars.add(new ChartBar(e.getRouteId(), bar, e.getDurationHours(), Formats.hours(e.getDurationHours())));
            } else {
                noDuration.add(label);
            }
            if (e.getEmissionsKg() != null) {
                emissionBars.add(new ChartBar(e.getRouteId(), bar, e.getEmissionsKg(), Formats.emissions(e.getEmissionsKg())));
            } else {
                noEmissions.add(label);
            }
        }
        List<ChartModel> charts = List.of(new ChartModel("Total cost", "INR", costBars, null),
                new ChartModel("Duration", "Hours", durationBars, missing(noDuration)),
                new ChartModel("Emissions", "kg CO2e", emissionBars, missing(noEmissions)));

        List<StrategyCard> cards = new ArrayList<>();
        for (String key : STRATEGY_ORDER) {
            Recommendation r = byStrategy.get(key);
            if (r == null) {
                continue;
            }
            OptimisationPreference p = OptimisationPreference.valueOf(key);
            cards.add(new StrategyCard(key, r.getStrategyName(), p.getDescription(),
                    r.isRecommended() ? routeLabel(r.getChosen().getRouteId(), r.getChosen().getRouteName()) : null,
                    r.isRecommended() ? highlight(key, r) : r.getReason(), key.equals(preferred)));
        }
        RecommendationCard card = pick == null || !pick.isRecommended()
                ? new RecommendationCard(shipment.getPreference().getLabel(), false, 0, null, null, null, null,
                        (pick == null ? "No recommendation is available." : pick.getReason())
                                + " You can still book any eligible route below.")
                : new RecommendationCard(pick.getStrategyName(), true, chosenId,
                        routeLabel(chosenId, pick.getChosen().getRouteName()), pick.getChosen().getMode().name(),
                        Formats.money(pick.getChosen().getTotalCost()), highlight(preferred, pick), pick.getReason());

        List<ExcludedRow> excluded = new ArrayList<>();
        if (pick != null) {
            for (ExcludedRoute x : pick.getExcluded()) {
                excluded.add(new ExcludedRow(routeLabel(x.getEvaluation().getRouteId(), x.getEvaluation().getRouteName()),
                        "Not considered by " + pick.getStrategyName() + ": " + x.getReason()));
            }
        }
        for (RejectedRoute x : result.getRejections()) {
            excluded.add(new ExcludedRow(routeLabel(x.getRoute().getRouteId(), x.getRoute().getName()),
                    "Not eligible: " + x.getReason()));
        }

        String blocked = null;
        if (active.isPresent()) {
            blocked = "This shipment already has an active booking (#" + active.get().getBookingId()
                    + "). Open it from My bookings.";
        } else if (!dateBookable) {
            blocked = "The dispatch date " + Formats.date(shipment.getDispatchDate()) + " is in the past. Saved shipments "
                    + "can still be compared, but booking needs a dispatch date of today or later.";
        } else if (shipment.getStatus() != ShipmentStatus.DRAFT && shipment.getStatus() != ShipmentStatus.PLANNED) {
            blocked = "This shipment is " + Formats.label(shipment.getStatus()).toLowerCase() + " and cannot be booked.";
        } else if (rows.isEmpty()) {
            blocked = result.getEligibility().getMessage();
        }
        return new ComparisonModel(shipment.getShipmentId(), Formats.route(shipment.getSource(), shipment.getDestination()),
                Formats.label(shipment.getCargoType()) + " \u00b7 " + Formats.weight(shipment.getWeightKg()) + " \u00b7 dispatch "
                        + Formats.date(shipment.getDispatchDate()),
                shipment.getPreference().getLabel(), preferred, card, cards, rows, excluded, charts, legend, blocked == null,
                blocked);
    }

    private static String missing(List<String> routes) {
        return routes.isEmpty() ? null : "Not available for " + String.join(", ", routes) + ".";
    }

    /** @return the deciding value of a strategy's winner */
    static String highlight(String key, Recommendation r) {
        RouteEvaluation e = r.getChosen();
        switch (key) {
            case CheapestStrategy.KEY:
                return "Total cost " + Formats.money(e.getTotalCost());
            case FastestStrategy.KEY:
                return "Duration " + Formats.hours(e.getDurationHours());
            case GreenestStrategy.KEY:
                return "Emissions " + Formats.emissions(e.getEmissionsKg());
            default:
                return "Balanced score " + Formats.score(r.getChosenScore() == null ? null
                        : r.getChosenScore().getTotalScore());
        }
    }

    /** @return the strategies a booking can be recorded under, in display order */
    public static List<StrategyOption> strategyOptions() {
        List<StrategyOption> options = new ArrayList<>();
        for (String key : STRATEGY_ORDER) {
            options.add(new StrategyOption(key, OptimisationPreference.valueOf(key).getLabel()));
        }
        return options;
    }

    /** @return "Review your order" for one evaluated route */
    public static OrderSummaryModel orderSummary(Shipment shipment, RouteEvaluation e) {
        RouteSummary summary = summary(routeLabel(e.getRouteId(), e.getRouteName()), e.getMode().name(),
                e.getRoute().getLegs(), e.getCostBreakdown(), e.getTotalCost(), e.getDurationHours(),
                e.getEstimatedArrival(), e.getEmissionsKg(), e.getReliability(), shipment.getDispatchDate(),
                shipment.getPreference().getLabel());
        return new OrderSummaryModel(shipment.getShipmentId(), e.getRouteId(),
                Formats.route(shipment.getSource(), shipment.getDestination()), summary, strategyOptions(),
                shipment.getPreference().getStrategyKey());
    }

    private static RouteSummary summary(String route, String mode, List<RouteLeg> legs, CostBreakdown cost,
                                        BigDecimal total, Double duration, LocalDateTime arrival, Double emissions,
                                        Double reliability, LocalDate dispatch, String preference) {
        List<LegRow> legRows = new ArrayList<>();
        int n = 0;
        for (RouteLeg leg : legs) {
            legRows.add(new LegRow(++n, leg.getMode().name(), Formats.km(leg.getDistanceKm()),
                    leg.getSpeedKmh() == null ? Formats.NOT_AVAILABLE
                            : BigDecimal.valueOf(leg.getSpeedKmh()).stripTrailingZeros().toPlainString() + " km/h"));
        }
        List<CostLine> costs = new ArrayList<>();
        if (cost != null) {
            costs.add(new CostLine("Transport", Formats.money(cost.getTransport())));
            costs.add(new CostLine("Handling", Formats.money(cost.getHandling())));
            costs.add(new CostLine("Transfers", Formats.money(cost.getTransfer())));
            costs.add(new CostLine("Waiting", Formats.money(cost.getWaiting())));
        }
        return new RouteSummary(route, mode, legRows, costs, Formats.money(total), Formats.hours(duration),
                arrival == null ? Formats.NOT_AVAILABLE : Formats.dateTime(arrival), Formats.emissions(emissions),
                Formats.reliability(reliability), Formats.date(dispatch), preference);
    }

    /**
     * Builds the booking detail entirely from the booking's stored snapshot.
     *
     * @param b        booking
     * @param shipment the booked shipment (for the preference), or null if it cannot be read
     * @param attempts payment attempts
     * @param admin    true for the administrator, who may view but not pay or cancel
     * @return booking detail model
     */
    public static BookingDetailModel bookingDetail(Booking b, Shipment shipment, List<Payment> attempts, boolean admin) {
        BookingSnapshot snap = b.getSnapshot();
        RouteSummary summary = summary(routeLabel(snap.getRouteId(), snap.getRouteName()), snap.getRouteMode().name(),
                snap.getLegs(), snap.getCost(), snap.getTotalCost(), snap.getDurationHours(), snap.getEstimatedArrival(),
                snap.getEmissionsKg(), snap.getReliability(), snap.getDispatchDate(),
                shipment == null ? Formats.NONE : shipment.getPreference().getLabel());
        List<PaymentRow> rows = new ArrayList<>();
        Payment latest = null;
        for (Payment p : attempts) {
            rows.add(new PaymentRow(p.getAttemptNo(), p.getMethod().getLabel(), Formats.label(p.getStatus()),
                    paymentChip(p.getStatus()), StatusHelp.of(p.getStatus()), Formats.dateTime(p.getCreatedAt())));
            if (latest == null || p.getAttemptNo() > latest.getAttemptNo()) {
                latest = p;
            }
        }
        boolean unpaid = b.getStatus() == BookingStatus.PENDING_PAYMENT;
        return new BookingDetailModel(b.getBookingId(), b.getShipmentId(), Formats.route(snap.getSource(),
                snap.getDestination()), Formats.label(b.getStatus()), ChipKind.of(b.getStatus()), StatusHelp.of(b.getStatus()),
                latest == null ? "Not paid yet" : Formats.label(latest.getStatus()),
                latest == null ? ChipKind.NEUTRAL : paymentChip(latest.getStatus()),
                StatusHelp.of(latest == null ? null : latest.getStatus()), summary, snap.getStrategyName(),
                snap.getReason(), Formats.dateTime(b.getBookedAt()), Formats.money(snap.getTotalCost()), unpaid && !admin,
                unpaid && !admin, rows, timeline(b, List.of()));
    }

    /**
     * @param b        booking
     * @param shipment its shipment (null if not found)
     * @param attempts payment attempts
     * @param admin    true for the administrator (read-only)
     * @param events   tracking events, oldest first
     * @return the booking detail with its tracking timeline
     */
    public static BookingDetailModel bookingDetail(Booking b, Shipment shipment, List<Payment> attempts, boolean admin,
                                                   List<TrackingEvent> events) {
        BookingDetailModel m = bookingDetail(b, shipment, attempts, admin);
        return new BookingDetailModel(m.bookingId(), m.shipmentId(), m.title(), m.status(), m.statusChip(),
                m.statusHint(), m.payment(), m.paymentChip(), m.paymentHint(), m.summary(), m.strategy(), m.reason(),
                m.bookedAt(), m.amount(), m.canPay(), m.canCancel(), m.attempts(), timeline(b, events));
    }

    /** Shown instead of a timeline while the booking is unpaid. */
    public static final String UNPAID_TIMELINE = "Tracking starts once this booking is paid. The Booked milestone is "
            + "recorded with the successful payment.";
    /** Shown instead of a timeline for a cancelled booking. */
    public static final String CANCELLED_TIMELINE = "This booking was cancelled before it was paid, so it has no "
            + "tracking.";

    /**
     * Milestones of the booked route in order (At transfer only on multi-leg routes): recorded ones
     * with time, source and description, the latest highlighted, the single next one pending.
     *
     * @param b      booking
     * @param events its tracking events
     * @return timeline, or the reason there is none
     */
    public static TimelineModel timeline(Booking b, List<TrackingEvent> events) {
        if (b.getStatus() == BookingStatus.PENDING_PAYMENT) {
            return new TimelineModel(false, UNPAID_TIMELINE, List.of());
        }
        if (b.getStatus() != BookingStatus.CONFIRMED) {
            return new TimelineModel(false, CANCELLED_TIMELINE, List.of());
        }
        boolean multiLeg = TrackingService.isMultiLeg(b);
        Milestone latest = TrackingService.latest(events);
        Milestone next = Milestone.next(latest, multiLeg);
        Map<Milestone, TrackingEvent> recorded = new LinkedHashMap<>();
        for (TrackingEvent e : events) {
            recorded.putIfAbsent(e.getMilestone(), e);
        }
        List<TimelineStep> steps = new ArrayList<>();
        for (Milestone m : Milestone.sequence(multiLeg)) {
            TrackingEvent e = recorded.get(m);
            if (e != null) {
                steps.add(new TimelineStep(m.getLabel(), m == latest ? StepState.CURRENT : StepState.DONE,
                        Formats.dateTime(e.getEventTime()), sourceLabel(e.getSource()), e.getDescription()));
            } else {
                steps.add(new TimelineStep(m.getLabel(), m == next ? StepState.NEXT : StepState.LATER, "", "",
                        m == next ? "Next step; waiting for this update." : ""));
            }
        }
        String legs = multiLeg ? " This route has " + b.getSnapshot().getLegs().size()
                + " legs, so it includes At transfer." : "";
        String message = next == null ? "Delivered. Tracking is complete."
                : "Now: " + (latest == null ? "not started" : latest.getLabel()) + ". Next: " + next.getLabel() + "."
                        + legs;
        return new TimelineModel(true, message, steps);
    }

    /** @return "System", "Administrator" or "Simulated" */
    static String sourceLabel(TrackingSource source) {
        if (source == null) {
            return Formats.NONE;
        }
        switch (source) {
            case ADMIN:
                return "Administrator";
            case SIMULATED:
                return "Simulated";
            default:
                return "System";
        }
    }

    /**
     * @param list the user's notifications, newest first
     * @return the notifications screen
     */
    public static NotificationsModel notifications(List<Notification> list) {
        List<NotificationRow> rows = new ArrayList<>();
        int unread = 0;
        for (Notification n : list) {
            rows.add(new NotificationRow(n.getNotificationId(), n.getBookingId(), Formats.label(n.getType()),
                    n.getMessage(), Formats.dateTime(n.getCreatedAt()), !n.isRead()));
            if (!n.isRead()) {
                unread++;
            }
        }
        String summary = list.isEmpty() ? "Nothing yet. Booking, payment and tracking updates appear here."
                : unread == 0 ? "All caught up · " + Formats.count(list.size(), "notification", "notifications")
                : unread + " unread of " + list.size();
        return new NotificationsModel(rows, summary, unread);
    }

    /** Shown for a failed report instead of any stored detail. */
    public static final String REPORT_FAILED = "The report could not be produced. Please try again later.";

    /** @return one shipment the user can report on */
    public static ShipmentChoice shipmentChoice(Shipment s) {
        return new ShipmentChoice(s.getShipmentId(), "#" + s.getShipmentId() + "  " + Formats.route(s.getSource(),
                s.getDestination()) + " · " + Formats.weight(s.getWeightKg()) + " · " + Formats.date(s.getDispatchDate()),
                s.getPreference().getStrategyKey());
    }

    /** @return one row of the user's reports */
    public static ReportJobRow reportJobRow(ReportRecord r) {
        ReportRow row = reportRow(r);
        return new ReportJobRow(r.getJobId(), row.job(), r.getShipmentId(), row.strategy(), row.status(), row.chip(),
                row.created(), r.getFinishedAt() == null ? Formats.NONE : Formats.dateTime(r.getFinishedAt()),
                r.isDone(), row.hint());
    }

    /**
     * @param shipments the user's shipments
     * @param reports   the user's reports, newest first
     * @param limit     how many reports were asked for
     * @return the reports screen
     */
    public static ReportsModel reports(List<Shipment> shipments, List<ReportRecord> reports, int limit) {
        List<ShipmentChoice> choices = new ArrayList<>();
        for (Shipment s : shipments) {
            choices.add(shipmentChoice(s));
        }
        List<ReportJobRow> rows = new ArrayList<>();
        for (ReportRecord r : reports) {
            rows.add(reportJobRow(r));
        }
        return new ReportsModel(choices, strategyOptions(), rows, note(reports.size() >= limit, limit));
    }

    /** @return plain-language progress of a report job (a failure never shows its stored detail) */
    public static ReportProgress progress(ReportRecord r) {
        String message;
        switch (r.getStatus()) {
            case DONE:
                message = "Done. Use Save a copy… to keep the file.";
                break;
            case FAILED:
                message = REPORT_FAILED;
                break;
            case RUNNING:
                message = "Writing the report…";
                break;
            default:
                message = "Waiting to start…";
        }
        return new ReportProgress(r.getJobId(), Formats.label(r.getStatus()), ChipKind.of(r.getStatus()), message,
                r.isFinished(), r.isDone());
    }

    /** @return one users-table row (no password data exists in {@link User}) */
    public static UserRow userRow(User u) {
        return new UserRow(u.getUserId(), u.getName(), u.getEmail(), Formats.label(u.getRole()),
                u.isAdmin() ? ChipKind.INFO : ChipKind.NEUTRAL, u.isEnabled() ? "Enabled" : "Disabled",
                u.isEnabled() ? ChipKind.SUCCESS : ChipKind.NEUTRAL);
    }

    /**
     * @param b      confirmed booking
     * @param events its events
     * @return its tracking row, or null if it is delivered (nothing can follow)
     */
    public static AdminTrackingRow adminTrackingRow(Booking b, List<TrackingEvent> events) {
        Milestone next = TrackingService.nextMilestone(b, events);
        if (next == null) {
            return null;
        }
        Milestone latest = TrackingService.latest(events);
        LocalDateTime updated = null;
        for (TrackingEvent e : events) {
            if (e.getMilestone() == latest) {
                updated = e.getEventTime();
            }
        }
        BookingSnapshot snap = b.getSnapshot();
        int legs = snap.getLegs().size();
        return new AdminTrackingRow(b.getBookingId(), Formats.route(snap.getSource(), snap.getDestination()),
                snap.getRouteName() + " · " + Formats.count(legs, "leg", "legs"),
                latest == null ? NONE_YET : latest.getLabel(), latest == null ? null : latest.name(), next.getLabel(),
                next.name(), updated == null ? Formats.NONE : Formats.dateTime(updated));
    }

    /** @return chip colour of a payment attempt */
    static ChipKind paymentChip(PaymentStatus status) {
        if (status == PaymentStatus.PAID) {
            return ChipKind.SUCCESS;
        }
        return status == PaymentStatus.FAILED ? ChipKind.DANGER : ChipKind.WARNING;
    }

    /** @return "Newest first." or "Newest first; showing the latest 25." */
    static String note(boolean more, int limit) {
        return more ? "Newest first; showing the latest " + limit + "." : "Newest first.";
    }

    /**
     * @return "2 draft · 1 booked", listing non-zero counts in enum order, or {@link #NONE_YET}
     */
    static <E extends Enum<E>> String breakdown(Map<E, Integer> counts, E[] order) {
        List<String> parts = new ArrayList<>();
        for (E key : order) {
            Integer n = counts.get(key);
            if (n != null && n > 0) {
                parts.add(n + " " + Formats.label(key).toLowerCase());
            }
        }
        return parts.isEmpty() ? NONE_YET : String.join(" · ", parts);
    }

    private static String firstName(User user) {
        String name = user == null || user.getName() == null ? "" : user.getName().trim();
        if (name.isEmpty()) {
            return "there";
        }
        int space = name.indexOf(' ');
        return space > 0 ? name.substring(0, space) : name;
    }
}

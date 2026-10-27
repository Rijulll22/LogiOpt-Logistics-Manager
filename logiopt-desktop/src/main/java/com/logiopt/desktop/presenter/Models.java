package com.logiopt.desktop.presenter;

import com.logiopt.desktop.support.ChipKind;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable view models handed from presenters to views. They hold display-ready text only, so the
 * views do no formatting and no business logic.
 */
public final class Models {

    private Models() {
    }

    /**
     * One dashboard number.
     *
     * @param label   what is counted
     * @param value   the count
     * @param caption breakdown or explanation
     */
    public record StatCard(String label, String value, String caption) {
    }

    /**
     * Latest milestone of one confirmed booking.
     *
     * @param booking   "#12"
     * @param route     "Mumbai → Delhi"
     * @param routeName booked route name
     * @param milestone latest milestone label
     * @param updated   time of that milestone
     */
    public record TrackingRow(String booking, String route, String routeName, String milestone, String updated) {
    }

    /**
     * Dashboard for a customer (with the tracking table) or the administrator (counts only).
     *
     * @param heading      greeting
     * @param subtitle     what the page shows
     * @param cards        stat cards
     * @param showTracking true for customers
     * @param tracking     tracking rows
     * @param trackingNote explanation under the tracking table
     */
    public record DashboardModel(String heading, String subtitle, List<StatCard> cards, boolean showTracking,
                                 List<TrackingRow> tracking, String trackingNote) {
        /** Copies the lists. */
        public DashboardModel {
            cards = List.copyOf(cards);
            tracking = List.copyOf(tracking);
        }
    }

    /**
     * One saved shipment.
     *
     * @param id       shipment id
     * @param route    "Mumbai → Delhi"
     * @param cargo    cargo type label
     * @param weight   "5000 kg"
     * @param dispatch dispatch date
     * @param status   status label
     * @param chip       chip colour
     * @param editable   true only for DRAFT
     * @param preference optimisation preference label
     * @param hint       what the status means (chip tooltip)
     */
    public record ShipmentRow(long id, String route, String cargo, String weight, String dispatch, String status,
                              ChipKind chip, boolean editable, String preference, String hint) {
    }

    /**
     * One booking.
     *
     * @param id         booking id
     * @param shipmentId shipment id
     * @param route      "Mumbai → Delhi"
     * @param routeName  booked route name
     * @param total      total cost
     * @param status     status label
     * @param chip       chip colour
     * @param bookedAt   booking time
     * @param hint       what the status means (chip tooltip)
     */
    public record BookingRow(long id, long shipmentId, String route, String routeName, String total, String status,
                             ChipKind chip, String bookedAt, String hint) {
    }

    /**
     * One report job.
     *
     * @param job        short job reference
     * @param shipmentId shipment id
     * @param strategy   strategy label
     * @param status     status label
     * @param chip       chip colour
     * @param created    creation time
     * @param hint       what the status means (chip tooltip)
     */
    public record ReportRow(String job, long shipmentId, String strategy, String status, ChipKind chip,
                            String created, String hint) {
    }

    /**
     * The three history tables.
     *
     * @param shipments     newest shipments
     * @param shipmentsNote "Newest first; showing the latest 25." or similar
     * @param bookings      newest bookings
     * @param bookingsNote  note for bookings
     * @param reports       newest report jobs
     * @param reportsNote   note for reports
     */
    public record HistoryModel(List<ShipmentRow> shipments, String shipmentsNote, List<BookingRow> bookings,
                               String bookingsNote, List<ReportRow> reports, String reportsNote) {
        /** Copies the lists. */
        public HistoryModel {
            shipments = List.copyOf(shipments);
            bookings = List.copyOf(bookings);
            reports = List.copyOf(reports);
        }
    }

    /**
     * One optimisation preference choice of the shipment form.
     *
     * @param key         preference name (CHEAPEST, FASTEST, GREENEST, BALANCED)
     * @param label       display name
     * @param description one-line description
     */
    public record PreferenceOption(String key, String label, String description) {
    }

    /**
     * The highlighted recommendation of the preferred strategy.
     *
     * @param strategy    strategy name ("Balanced")
     * @param recommended false when the strategy cannot rank any route
     * @param routeId     recommended route (0 when none)
     * @param route       "#2 Rail direct"
     * @param mode        mode label
     * @param total       total cost
     * @param highlight   the strategy's deciding value ("Balanced score 0.167971", "Total cost INR ...")
     * @param reason      the strategy's reason, or why it could not recommend
     */
    public record RecommendationCard(String strategy, boolean recommended, long routeId, String route, String mode,
                                     String total, String highlight, String reason) {
    }

    /**
     * One of the four strategy cards.
     *
     * @param key         strategy key
     * @param name        strategy name
     * @param description what it optimises
     * @param winner      "#2 Rail direct", or null when the strategy cannot recommend
     * @param value       the winner's deciding value, or the reason when there is no winner
     * @param yourChoice  true for the shipment's preferred strategy
     */
    public record StrategyCard(String key, String name, String description, String winner, String value,
                               boolean yourChoice) {
    }

    /**
     * One eligible route in the comparison table. Missing metrics read "not available"; the best
     * value of each column is flagged (lowest cost, duration and emissions, highest reliability).
     *
     * @param routeId         route id
     * @param route           "#2 Rail direct"
     * @param mode            mode label (ROAD, RAIL, MULTIMODAL)
     * @param cost            total cost
     * @param costBest        lowest cost
     * @param duration        duration
     * @param durationBest    shortest duration
     * @param emissions       emissions
     * @param emissionsBest   lowest emissions
     * @param reliability     reliability
     * @param reliabilityBest highest reliability
     * @param yourChoice      the preferred strategy's winner
     */
    public record ComparisonRow(long routeId, String route, String mode, String cost, boolean costBest, String duration,
                                boolean durationBest, String emissions, boolean emissionsBest, String reliability,
                                boolean reliabilityBest, boolean yourChoice) {
    }

    /**
     * A route that was not considered or not eligible.
     *
     * @param route  "#9 Rail freight"
     * @param reason why
     */
    public record ExcludedRow(String route, String reason) {
    }

    /**
     * One bar of a comparison chart.
     *
     * @param routeId route id (decides the colour, the same in every chart)
     * @param label   category label ("#2")
     * @param value   bar height, from the service's evaluation
     * @param text    value label
     */
    public record ChartBar(long routeId, String label, double value, String text) {
    }

    /**
     * One comparison chart.
     *
     * @param title       chart title
     * @param axis        value axis title
     * @param bars        bars for the routes that have the value
     * @param missingNote routes without the value, or null
     */
    public record ChartModel(String title, String axis, List<ChartBar> bars, String missingNote) {
        /** Copies the list. */
        public ChartModel {
            bars = List.copyOf(bars);
        }
    }

    /**
     * The comparison screen.
     *
     * @param shipmentId        shipment id
     * @param title             "Mumbai -> Delhi"
     * @param details           cargo, weight and dispatch date
     * @param preference        preference label
     * @param preferredKey      the preferred strategy's key
     * @param recommendation    the preferred strategy's recommendation card
     * @param strategies        the four strategy cards
     * @param rows              eligible routes
     * @param excluded          not-considered and not-eligible routes
     * @param charts            cost, duration and emissions charts
     * @param legend            route id to "#2 Rail direct", in table order
     * @param bookable          false when booking is not possible
     * @param bookBlockedReason why Book is disabled, or null
     */
    public record ComparisonModel(long shipmentId, String title, String details, String preference, String preferredKey,
                                  RecommendationCard recommendation, List<StrategyCard> strategies,
                                  List<ComparisonRow> rows, List<ExcludedRow> excluded, List<ChartModel> charts,
                                  Map<Long, String> legend, boolean bookable, String bookBlockedReason) {
        /** Copies the collections (the legend keeps its order). */
        public ComparisonModel {
            strategies = List.copyOf(strategies);
            rows = List.copyOf(rows);
            excluded = List.copyOf(excluded);
            charts = List.copyOf(charts);
            legend = Collections.unmodifiableMap(new LinkedHashMap<>(legend));
        }
    }

    /**
     * One leg of a route.
     *
     * @param number   1, 2, ...
     * @param mode     ROAD or RAIL
     * @param distance "1,450 km"
     * @param speed    "45 km/h", or "not available"
     */
    public record LegRow(int number, String mode, String distance, String speed) {
    }

    /**
     * A labelled amount of the cost breakdown.
     *
     * @param label  "Transport"
     * @param amount "INR 20,300.00"
     */
    public record CostLine(String label, String amount) {
    }

    /**
     * Route details shared by the order summary and the booking detail.
     *
     * @param route       "#2 Rail direct"
     * @param mode        mode label
     * @param legs        every leg
     * @param costs       cost breakdown lines
     * @param total       total cost
     * @param duration    duration
     * @param arrival     estimated arrival
     * @param emissions   emissions
     * @param reliability reliability
     * @param dispatch    dispatch date
     * @param preference  preference label
     */
    public record RouteSummary(String route, String mode, List<LegRow> legs, List<CostLine> costs, String total,
                               String duration, String arrival, String emissions, String reliability, String dispatch,
                               String preference) {
        /** Copies the lists. */
        public RouteSummary {
            legs = List.copyOf(legs);
            costs = List.copyOf(costs);
        }
    }

    /**
     * A strategy that can be chosen when booking.
     *
     * @param key  strategy key
     * @param name strategy name
     */
    public record StrategyOption(String key, String name) {
    }

    /**
     * "Review your order" before booking.
     *
     * @param shipmentId shipment id
     * @param routeId    route id
     * @param title      "Mumbai -> Delhi"
     * @param summary    route details
     * @param strategies strategies to book under
     * @param selected   pre-selected strategy (the preference)
     */
    public record OrderSummaryModel(long shipmentId, long routeId, String title, RouteSummary summary,
                                    List<StrategyOption> strategies, String selected) {
        /** Copies the list. */
        public OrderSummaryModel {
            strategies = List.copyOf(strategies);
        }
    }

    /**
     * One payment attempt.
     *
     * @param attempt attempt number
     * @param method  method label
     * @param status  status label
     * @param chip    chip colour
     * @param hint    what the status means
     * @param time    when it was made
     */
    public record PaymentRow(int attempt, String method, String status, ChipKind chip, String hint, String time) {
    }

    /**
     * The booking detail screen, entirely from the booking's stored snapshot.
     *
     * @param bookingId   booking id
     * @param shipmentId  shipment id
     * @param title       "Mumbai -> Delhi"
     * @param status      booking status label
     * @param statusChip  chip colour
     * @param statusHint  what the status means
     * @param payment     latest payment status label ("Not paid yet" when none)
     * @param paymentChip chip colour
     * @param paymentHint what it means
     * @param summary     route details from the snapshot
     * @param strategy    strategy name recorded in the snapshot
     * @param reason      the recorded recommendation reason
     * @param bookedAt    booking time
     * @param amount      amount to pay (the snapshot total)
     * @param canPay      true while unpaid (and not the administrator)
     * @param canCancel   true while unpaid (and not the administrator)
     * @param attempts    payment attempts, oldest first
     * @param timeline    tracking timeline, or the reason there is none yet
     */
    public record BookingDetailModel(long bookingId, long shipmentId, String title, String status, ChipKind statusChip,
                                     String statusHint, String payment, ChipKind paymentChip, String paymentHint,
                                     RouteSummary summary, String strategy, String reason, String bookedAt,
                                     String amount, boolean canPay, boolean canCancel, List<PaymentRow> attempts,
                                     TimelineModel timeline) {
        /** Copies the list. */
        public BookingDetailModel {
            attempts = List.copyOf(attempts);
        }
    }

    /**
     * The signed-in user's account screen.
     *
     * @param name            display name
     * @param email           email address
     * @param role            role label
     * @param roleChip        role chip colour
     * @param memberSince     registration date
     * @param canChangePassword false for the administrator (password set by LOGIOPT_ADMIN_PASSWORD)
     * @param passwordNote    line shown on the password card
     */
    public record AccountModel(String name, String email, String role, ChipKind roleChip, String memberSince,
                               boolean canChangePassword, String passwordNote) {
    }

    /**
     * Content of the new and edit shipment forms.
     *
     * @param heading    card heading
     * @param intro      line under the heading
     * @param editId     shipment being edited, or null for a new one
     * @param cities     selectable cities (cities served by a configured route)
     * @param cargoTypes cargo type names
     * @param values     field values by field name (see ShipmentFormInput)
     * @param weightHint  weight limits
     * @param saveLabel   label of the plain save button
     * @param preferences the four optimisation preferences (the shipment's or BALANCED is selected)
     */
    public record ShipmentFormModel(String heading, String intro, Long editId, List<String> cities,
                                    List<String> cargoTypes, Map<String, String> values, String weightHint,
                                    String saveLabel, List<PreferenceOption> preferences) {
        /** Copies the collections. */
        public ShipmentFormModel {
            cities = List.copyOf(cities);
            cargoTypes = List.copyOf(cargoTypes);
            values = Map.copyOf(values);
            preferences = List.copyOf(preferences);
        }
    }
    /** Where a milestone stands on a booking's timeline. */
    public enum StepState {
        /** Recorded. */
        DONE,
        /** Recorded and the latest one. */
        CURRENT,
        /** The single milestone that can follow. */
        NEXT,
        /** Further ahead. */
        LATER
    }

    /**
     * One milestone on the tracking timeline.
     *
     * @param label       "In transit"
     * @param state       done, current, next or later
     * @param time        when it was recorded (empty when not recorded)
     * @param source      who recorded it ("System", "Administrator", "Simulated"; empty when not recorded)
     * @param description what happened (or what comes next)
     */
    public record TimelineStep(String label, StepState state, String time, String source, String description) {
    }

    /**
     * The tracking timeline of one booking.
     *
     * @param tracked false when there is nothing to track yet (unpaid or cancelled)
     * @param message why there is no timeline, or a one-line summary of where it stands
     * @param steps   milestones of the booked route in order (empty when not tracked)
     */
    public record TimelineModel(boolean tracked, String message, List<TimelineStep> steps) {
        /** Copies the list. */
        public TimelineModel {
            steps = List.copyOf(steps);
        }
    }

    /**
     * One notification.
     *
     * @param id        notification id
     * @param bookingId booking it is about
     * @param type      type label ("Tracking update")
     * @param message   text
     * @param time      creation time
     * @param unread    true until marked as read
     */
    public record NotificationRow(long id, long bookingId, String type, String message, String time, boolean unread) {
    }

    /**
     * The notifications screen.
     *
     * @param rows    newest first
     * @param summary "2 unread of 5" or similar
     * @param unread  number of unread notifications
     */
    public record NotificationsModel(List<NotificationRow> rows, String summary, int unread) {
        /** Copies the list. */
        public NotificationsModel {
            rows = List.copyOf(rows);
        }
    }

    /**
     * A shipment the user can report on.
     *
     * @param id           shipment id
     * @param label        "#17 Mumbai -> Delhi, 5,000 kg, 2026-11-03"
     * @param preferredKey strategy key of the shipment's preference
     */
    public record ShipmentChoice(long id, String label, String preferredKey) {
    }

    /**
     * One of the user's report jobs.
     *
     * @param jobId      full job id (used for Save a copy)
     * @param job        short job reference
     * @param shipmentId shipment id
     * @param strategy   strategy label
     * @param status     status label
     * @param chip       chip colour
     * @param created    creation time
     * @param finished   finishing time
     * @param canSave    true when the report is done
     * @param hint       what the status means
     */
    public record ReportJobRow(String jobId, String job, long shipmentId, String strategy, String status, ChipKind chip,
                               String created, String finished, boolean canSave, String hint) {
    }

    /**
     * The reports screen.
     *
     * @param shipments shipments that can be reported on
     * @param strategies strategies to choose from
     * @param reports   the user's reports, newest first
     * @param note      line under the reports table
     */
    public record ReportsModel(List<ShipmentChoice> shipments, List<StrategyOption> strategies,
                               List<ReportJobRow> reports, String note) {
        /** Copies the lists. */
        public ReportsModel {
            shipments = List.copyOf(shipments);
            strategies = List.copyOf(strategies);
            reports = List.copyOf(reports);
        }
    }

    /**
     * Status of the report being generated.
     *
     * @param jobId    job id
     * @param status   status label
     * @param chip     chip colour
     * @param message  plain-language status (never a stack trace or a path)
     * @param finished true once Done or Failed
     * @param done     true if the report was written
     */
    public record ReportProgress(String jobId, String status, ChipKind chip, String message, boolean finished,
                                 boolean done) {
    }

    /**
     * One user account, without any password data.
     *
     * @param id       user id
     * @param name     display name
     * @param email    email address
     * @param role     role label
     * @param roleChip role chip colour
     * @param state    "Enabled" or "Disabled"
     * @param stateChip chip colour
     */
    public record UserRow(long id, String name, String email, String role, ChipKind roleChip, String state,
                          ChipKind stateChip) {
    }

    /**
     * One confirmed, undelivered booking on the administrator's tracking screen.
     *
     * @param bookingId  booking id
     * @param route      "Mumbai -> Delhi"
     * @param routeName  booked route name and leg count
     * @param current    label of the latest milestone ("None yet")
     * @param currentKey latest milestone name, or null for none (sent back so a stale screen changes nothing)
     * @param next       label of the single legal next milestone
     * @param nextKey    its name (the only milestone the form can record)
     * @param updated    time of the latest milestone
     */
    public record AdminTrackingRow(long bookingId, String route, String routeName, String current, String currentKey,
                                   String next, String nextKey, String updated) {
    }
}

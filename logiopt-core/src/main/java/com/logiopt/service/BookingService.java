package com.logiopt.service;

import com.logiopt.dao.BookingDAO;
import com.logiopt.dao.RouteDAO;
import com.logiopt.dao.ShipmentDAO;
import com.logiopt.exception.BookingException;
import com.logiopt.exception.DataAccessException;
import com.logiopt.exception.InvalidShipmentException;
import com.logiopt.exception.InvalidStrategyException;
import com.logiopt.exception.NotFoundException;
import com.logiopt.model.Booking;
import com.logiopt.model.BookingSnapshot;
import com.logiopt.model.BookingStatus;
import com.logiopt.model.NotificationType;
import com.logiopt.model.PlanningResult;
import com.logiopt.model.Recommendation;
import com.logiopt.model.RouteEvaluation;
import com.logiopt.model.ScoreBreakdown;
import com.logiopt.model.Shipment;
import com.logiopt.model.ShipmentStatus;
import com.logiopt.model.User;

import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Booking rules. A booking is made for the signed-in owner's shipment, for a route that the
 * planning engine (re-run here on the server) finds eligible, only when the dispatch date is today
 * or later, and only if the shipment has no active booking (checked under a row lock and by the
 * uk_bookings_active_shipment key). The snapshot is copied from the server's own RouteEvaluation;
 * nothing posted by the browser except the shipment id, route id and strategy key is used.
 * Only unpaid (PENDING_PAYMENT) bookings can be cancelled; cancelling frees the shipment.
 */
public class BookingService {

    /** Shipment states that allow a new booking. */
    static final Set<ShipmentStatus> BOOKABLE = EnumSet.of(ShipmentStatus.DRAFT, ShipmentStatus.PLANNED);

    private final ShipmentDAO shipments;
    private final RouteDAO routes;
    private final BookingDAO bookings;
    private final PlanningService planning;
    private final Clock clock;
    private final NotificationService notifications;

    /**
     * Creates the service.
     *
     * @param shipments shipment DAO
     * @param routes    route DAO
     * @param bookings  booking DAO
     * @param planning  planning engine
     * @param clock         clock that defines "today"
     * @param notifications in-app notifications (sent after commit)
     */
    public BookingService(ShipmentDAO shipments, RouteDAO routes, BookingDAO bookings, PlanningService planning,
                          Clock clock, NotificationService notifications) {
        if (shipments == null || routes == null || bookings == null || planning == null || clock == null
                || notifications == null) {
            throw new IllegalArgumentException("Booking dependencies must not be null");
        }
        this.shipments = shipments;
        this.routes = routes;
        this.bookings = bookings;
        this.planning = planning;
        this.clock = clock;
        this.notifications = notifications;
    }

    /**
     * Tells whether a shipment's dispatch date allows booking (today or later).
     *
     * @param shipment shipment
     * @return true if it may be booked by date
     */
    public boolean isDispatchDateBookable(Shipment shipment) {
        return shipment.getDispatchDate() != null && !shipment.getDispatchDate().isBefore(LocalDate.now(clock));
    }

    /**
     * Books a route for the user's shipment.
     *
     * @param user        signed-in user
     * @param shipmentId  shipment id
     * @param routeId     chosen route id
     * @param strategyKey strategy the comparison was shown with
     * @return new booking id
     * @throws NotFoundException        if the shipment is missing or not the user's
     * @throws BookingException         if the date, route or shipment state does not allow booking
     * @throws InvalidStrategyException if the strategy key is unknown
     * @throws InvalidShipmentException if the saved shipment is invalid
     * @throws DataAccessException      on database failure
     */
    public long book(User user, long shipmentId, long routeId, String strategyKey)
            throws NotFoundException, BookingException, InvalidStrategyException, InvalidShipmentException,
            DataAccessException {
        Shipment shipment = shipments.findByIdForUser(shipmentId, user.getUserId())
                .orElseThrow(() -> new NotFoundException("Shipment " + shipmentId));
        if (!isDispatchDateBookable(shipment)) {
            throw new BookingException("Booking needs a dispatch date of today or later. This shipment's dispatch "
                    + "date is " + shipment.getDispatchDate() + "; create a new shipment to book it.");
        }
        PlanningResult result = planning.plan(shipment,
                routes.findBySourceAndDestination(shipment.getSource(), shipment.getDestination()), strategyKey);
        RouteEvaluation chosen = null;
        for (RouteEvaluation e : result.getEvaluations()) {
            if (e.getRouteId() == routeId) {
                chosen = e;
            }
        }
        if (chosen == null) {
            throw new BookingException("Route " + routeId + " is not an eligible route for this shipment.");
        }
        BookingDAO.CreateResult created = bookings.create(shipmentId, user.getUserId(), BOOKABLE, ShipmentStatus.BOOKED,
                snapshotOf(shipment, result, chosen));
        switch (created.getOutcome()) {
            case CREATED:
                notifications.notify(user.getUserId(), NotificationType.BOOKING_CREATED, created.getBookingId(),
                        "created", "Booking " + created.getBookingId() + " for shipment " + shipmentId + " ("
                                + chosen.getRouteName() + ") was created. Pay to confirm it.");
                return created.getBookingId();
            case SHIPMENT_NOT_FOUND:
                throw new NotFoundException("Shipment " + shipmentId);
            case ALREADY_ACTIVE:
                throw new BookingException("This shipment already has an active booking.");
            default:
                throw new BookingException("This shipment cannot be booked in its current state.");
        }
    }

    /**
     * Copies the values of one server-side evaluation into a snapshot. No values are recalculated.
     *
     * @param shipment shipment
     * @param result   planning result that produced {@code chosen}
     * @param chosen   evaluation of the booked route (an instance from {@code result})
     * @return snapshot
     */
    static BookingSnapshot snapshotOf(Shipment shipment, PlanningResult result, RouteEvaluation chosen) {
        Recommendation rec = result.getRecommendation();
        String reason;
        if (rec.isRecommended() && rec.getChosen() == chosen) {
            reason = rec.getReason();
        } else if (rec.isRecommended()) {
            reason = "Chosen by the customer instead of the " + rec.getStrategyName() + " recommendation "
                    + rec.getChosen().getRouteName() + " (route " + rec.getChosen().getRouteId() + "). " + rec.getReason();
        } else {
            reason = "Chosen by the customer; the " + rec.getStrategyName() + " strategy made no recommendation. "
                    + rec.getReason();
        }
        String exclusion = result.getStrategyExclusions().get(chosen.getRouteId());
        if (exclusion != null) {
            reason += " Not considered by " + rec.getStrategyName() + ": " + exclusion + ".";
        }
        ScoreBreakdown score = rec.getScores().get(chosen.getRouteId());
        return new BookingSnapshot(shipment.getSource().trim(), shipment.getDestination().trim(), shipment.getCargoType(),
                shipment.getWeightKg(), shipment.getDispatchDate(), chosen.getRouteId(), chosen.getRouteName(),
                chosen.getMode(), chosen.getRoute().getLegs(), chosen.getCostBreakdown(), chosen.getDurationHours(),
                chosen.getEstimatedArrival(), chosen.getEmissionsKg(), chosen.getReliability(),
                score == null ? null : score.getTotalScore(), rec.getStrategyKey(), rec.getStrategyName(), reason,
                String.join(" ", chosen.getNotes()));
    }

    /**
     * Cancels an unpaid booking of the user and frees the shipment.
     *
     * @param user      signed-in user
     * @param bookingId booking id
     * @throws NotFoundException   if the booking is missing or not the user's (also for the admin)
     * @throws BookingException    if it is paid or already cancelled
     * @throws DataAccessException on database failure
     */
    public void cancel(User user, long bookingId) throws NotFoundException, BookingException, DataAccessException {
        Booking booking = bookings.findForOwner(bookingId, user.getUserId())
                .orElseThrow(() -> new NotFoundException("Booking " + bookingId));
        requireTransition(booking.getStatus(), BookingStatus.CANCELLED);
        switch (bookings.changeStatus(bookingId, user.getUserId(), BookingStatus.PENDING_PAYMENT, BookingStatus.CANCELLED)) {
            case CHANGED:
                notifications.notify(user.getUserId(), NotificationType.BOOKING_CANCELLED, bookingId, "cancelled",
                        "Booking " + bookingId + " was cancelled. The shipment can be booked again.");
                return;
            case NOT_FOUND:
                throw new NotFoundException("Booking " + bookingId);
            default:
                Booking now = bookings.findForOwner(bookingId, user.getUserId())
                        .orElseThrow(() -> new NotFoundException("Booking " + bookingId));
                requireTransition(now.getStatus(), BookingStatus.CANCELLED);
                throw new BookingException("This booking changed while you were cancelling it; reload and try again.");
        }
    }

    /**
     * Checks a booking transition and explains a refusal.
     *
     * @param from   current state
     * @param target wanted state
     * @throws BookingException if not allowed
     */
    static void requireTransition(BookingStatus from, BookingStatus target) throws BookingException {
        if (from.canTransitionTo(target)) {
            return;
        }
        if (from == BookingStatus.CONFIRMED) {
            throw new BookingException(target == BookingStatus.CANCELLED
                    ? "Paid bookings cannot be cancelled." : "This booking is already paid.");
        }
        throw new BookingException(target == BookingStatus.CANCELLED
                ? "This booking is already cancelled." : "Cancelled bookings cannot be paid.");
    }

    /**
     * Returns a booking the user may view: their own, or any booking for the administrator.
     *
     * @param user      signed-in user
     * @param bookingId booking id
     * @return booking
     * @throws NotFoundException   if missing or not viewable
     * @throws DataAccessException on database failure
     */
    public Booking view(User user, long bookingId) throws NotFoundException, DataAccessException {
        Optional<Booking> booking = user.isAdmin() ? bookings.findAnyById(bookingId)
                : bookings.findForOwner(bookingId, user.getUserId());
        return booking.orElseThrow(() -> new NotFoundException("Booking " + bookingId));
    }

    /**
     * @param user       signed-in user
     * @param shipmentId shipment id
     * @return the user's active booking for that shipment, if any
     * @throws DataAccessException on database failure
     */
    public Optional<Booking> activeBooking(User user, long shipmentId) throws DataAccessException {
        return bookings.findActiveForShipment(shipmentId, user.getUserId());
    }

    /**
     * @param user signed-in user
     * @return the user's bookings, newest first
     * @throws DataAccessException on database failure
     */
    public List<Booking> myBookings(User user) throws DataAccessException {
        return bookings.findByOwner(user.getUserId());
    }

    /**
     * @param admin signed-in administrator
     * @return all bookings, newest first
     * @throws DataAccessException on database failure
     * @throws SecurityException   if the user is not the administrator
     */
    public List<Booking> allBookings(User admin) throws DataAccessException {
        if (admin == null || !admin.isAdmin()) {
            throw new SecurityException("Administrator only");
        }
        return bookings.findAll();
    }
}

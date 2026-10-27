package com.logiopt.service;

import com.logiopt.dao.BookingDAO;
import com.logiopt.dao.TrackingDAO;
import com.logiopt.exception.DataAccessException;
import com.logiopt.exception.NotFoundException;
import com.logiopt.exception.TrackingException;
import com.logiopt.model.Booking;
import com.logiopt.model.BookingStatus;
import com.logiopt.model.Milestone;
import com.logiopt.model.NotificationType;
import com.logiopt.model.TrackingEvent;
import com.logiopt.model.TrackingSource;
import com.logiopt.model.User;
import com.logiopt.util.Strings;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Tracking rules. Events are allowed only for CONFIRMED bookings and only for the single legal next
 * milestone ({@link Milestone#next}): no skipping, no going back, DELIVERED is terminal (and sets the
 * shipment to COMPLETED). Every write names the milestone the caller saw as current; if tracking has
 * moved on in the meantime the call changes nothing, so repeated or concurrent "advance" requests and
 * the optional scheduler are idempotent. The owner is notified of each new event.
 */
public class TrackingService {

    /** Maximum description length (tracking.description VARCHAR(500)). */
    public static final int MAX_DESCRIPTION = 500;

    private static final Logger LOG = Logger.getLogger(TrackingService.class.getName());

    private final BookingDAO bookings;
    private final TrackingDAO tracking;
    private final NotificationService notifications;

    /** What happened to a record or advance request. */
    public enum Outcome {
        /** A new milestone was recorded. */
        RECORDED,
        /** Tracking had already moved past the expected milestone; nothing changed. */
        ALREADY_ADVANCED,
        /** The booking is already DELIVERED; nothing changed. */
        TERMINAL
    }

    /** Outcome and the latest milestone afterwards. */
    public static final class Result {
        private final Outcome outcome;
        private final Milestone current;

        Result(Outcome outcome, Milestone current) {
            this.outcome = outcome;
            this.current = current;
        }

        /** @return outcome */
        public Outcome getOutcome() {
            return outcome;
        }

        /** @return latest milestone after the call */
        public Milestone getCurrent() {
            return current;
        }
    }

    /**
     * Creates the service.
     *
     * @param bookings      booking DAO
     * @param tracking      tracking DAO
     * @param notifications notification service
     */
    public TrackingService(BookingDAO bookings, TrackingDAO tracking, NotificationService notifications) {
        if (bookings == null || tracking == null || notifications == null) {
            throw new IllegalArgumentException("Tracking dependencies must not be null");
        }
        this.bookings = bookings;
        this.tracking = tracking;
        this.notifications = notifications;
    }

    /**
     * @param viewable booking the caller has already been allowed to view (BookingService.view)
     * @return its events, oldest first
     * @throws DataAccessException on database failure
     */
    public List<TrackingEvent> timeline(Booking viewable) throws DataAccessException {
        return tracking.findByBooking(viewable.getBookingId());
    }

    /**
     * @param events events of one booking
     * @return the latest milestone, or null if none
     */
    public static Milestone latest(List<TrackingEvent> events) {
        Milestone latest = null;
        for (TrackingEvent e : events) {
            if (latest == null || e.getMilestone().ordinal() > latest.ordinal()) {
                latest = e.getMilestone();
            }
        }
        return latest;
    }

    /**
     * @param booking booking
     * @return true if its route has more than one leg (AT_TRANSFER applies)
     */
    public static boolean isMultiLeg(Booking booking) {
        return booking.getSnapshot().getLegs().size() > 1;
    }

    /**
     * @param booking booking
     * @param events  its events
     * @return the single legal next milestone, or null if not CONFIRMED or already DELIVERED
     */
    public static Milestone nextMilestone(Booking booking, List<TrackingEvent> events) {
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            return null;
        }
        return Milestone.next(latest(events), isMultiLeg(booking));
    }

    /**
     * Administrator records the next milestone with a description (source ADMIN).
     *
     * @param admin           signed-in administrator
     * @param bookingId       booking id
     * @param expectedCurrent milestone the form was shown with (null if none)
     * @param target          milestone requested
     * @param description     description (required)
     * @return outcome
     * @throws SecurityException   if the user is not the administrator
     * @throws NotFoundException   if the booking does not exist
     * @throws TrackingException   if not CONFIRMED, the description is missing, or the milestone skips or goes back
     * @throws DataAccessException on database failure
     */
    public Result record(User admin, long bookingId, Milestone expectedCurrent, Milestone target, String description)
            throws NotFoundException, TrackingException, DataAccessException {
        requireAdmin(admin);
        if (target == null) {
            throw new TrackingException("Choose the milestone to record.");
        }
        String text = description == null ? "" : description.trim();
        if (text.isEmpty() || text.length() > MAX_DESCRIPTION) {
            throw new TrackingException("A description of 1 to " + MAX_DESCRIPTION + " characters is required.");
        }
        return apply(bookingId, expectedCurrent, target, text, TrackingSource.ADMIN, admin.getUserId());
    }

    /**
     * The idempotent simulation step (source SIMULATED), used by the administrator's "Advance" button
     * and by the optional scheduler: records the milestone after {@code expectedCurrent}, or does
     * nothing if tracking has already moved on or is DELIVERED.
     *
     * @param bookingId       booking id
     * @param expectedCurrent milestone the caller saw as current (null if none)
     * @return outcome
     * @throws NotFoundException   if the booking does not exist
     * @throws TrackingException   if the booking is not CONFIRMED
     * @throws DataAccessException on database failure
     */
    public Result advance(long bookingId, Milestone expectedCurrent)
            throws NotFoundException, TrackingException, DataAccessException {
        Booking booking = requireConfirmed(bookingId);
        Milestone legal;
        try {
            legal = Milestone.next(expectedCurrent, isMultiLeg(booking));
        } catch (IllegalArgumentException e) {
            throw new TrackingException(expectedCurrent.getLabel() + " is not a milestone of this route.");
        }
        if (legal == null) {
            return new Result(Outcome.TERMINAL, expectedCurrent);
        }
        return apply(bookingId, expectedCurrent, legal, "Simulated progress: " + legal.getLabel() + ".",
                TrackingSource.SIMULATED, null);
    }

    /**
     * The administrator's "Advance (simulated)" action: the same idempotent step as
     * {@link #advance(long, Milestone)}, refused for anyone but the administrator.
     *
     * @param admin           signed-in administrator
     * @param bookingId       booking id
     * @param expectedCurrent milestone the caller saw as current (null if none)
     * @return outcome
     * @throws SecurityException   if the user is not the administrator
     * @throws NotFoundException   if the booking does not exist
     * @throws TrackingException   if the booking is not CONFIRMED
     * @throws DataAccessException on database failure
     */
    public Result advance(User admin, long bookingId, Milestone expectedCurrent)
            throws NotFoundException, TrackingException, DataAccessException {
        requireAdmin(admin);
        return advance(bookingId, expectedCurrent);
    }

    /**
     * One scheduler pass: advances every CONFIRMED, not yet delivered booking by one milestone through
     * {@link #advance}. A failure on one booking is logged and does not stop the others.
     *
     * @return number of milestones recorded
     * @throws DataAccessException if the bookings cannot be listed
     */
    public int advanceAll() throws DataAccessException {
        int recorded = 0;
        for (Booking b : bookings.findAll()) {
            if (b.getStatus() != BookingStatus.CONFIRMED) {
                continue;
            }
            try {
                Milestone current = latest(tracking.findByBooking(b.getBookingId()));
                if (current != Milestone.DELIVERED && advance(b.getBookingId(), current).getOutcome() == Outcome.RECORDED) {
                    recorded++;
                }
            } catch (NotFoundException | TrackingException | DataAccessException e) {
                LOG.log(Level.WARNING, "Simulated tracking for booking " + b.getBookingId() + " skipped", e);
            }
        }
        return recorded;
    }

    /**
     * @param admin signed-in administrator
     * @return CONFIRMED bookings, newest first
     * @throws DataAccessException on database failure
     * @throws SecurityException   if the user is not the administrator
     */
    public List<Booking> confirmedBookings(User admin) throws DataAccessException {
        requireAdmin(admin);
        List<Booking> confirmed = new ArrayList<>();
        for (Booking b : bookings.findAll()) {
            if (b.getStatus() == BookingStatus.CONFIRMED) {
                confirmed.add(b);
            }
        }
        return confirmed;
    }

    /**
     * @param admin     signed-in administrator
     * @param bookingId booking id
     * @return the booking's events (administrator view)
     * @throws DataAccessException on database failure
     * @throws SecurityException   if the user is not the administrator
     */
    public List<TrackingEvent> eventsForAdmin(User admin, long bookingId) throws DataAccessException {
        requireAdmin(admin);
        return tracking.findByBooking(bookingId);
    }

    private Result apply(long bookingId, Milestone expectedCurrent, Milestone target, String description,
                         TrackingSource source, Long actorId)
            throws NotFoundException, TrackingException, DataAccessException {
        Booking booking = requireConfirmed(bookingId);
        Milestone current = latest(tracking.findByBooking(bookingId));
        if (current != expectedCurrent) {
            return new Result(Outcome.ALREADY_ADVANCED, current);
        }
        Milestone legal = Milestone.next(current, isMultiLeg(booking));
        if (legal == null) {
            return new Result(Outcome.TERMINAL, current);
        }
        if (target != legal) {
            throw new TrackingException("Only " + legal.getLabel() + " can follow "
                    + (current == null ? "the start" : current.getLabel())
                    + ". Milestones cannot be skipped or repeated, and tracking cannot go back.");
        }
        switch (tracking.recordNext(bookingId, current, legal, description, source, actorId,
                legal == Milestone.DELIVERED)) {
            case RECORDED:
                notifications.notify(booking.getOwnerUserId(), NotificationType.TRACKING_UPDATE, bookingId,
                        legal.name(), "Booking " + bookingId + ": " + legal.getLabel() + ". " + description);
                return new Result(Outcome.RECORDED, legal);
            case STALE:
                return new Result(Outcome.ALREADY_ADVANCED, latest(tracking.findByBooking(bookingId)));
            case NOT_FOUND:
                throw new NotFoundException("Booking " + bookingId);
            default:
                throw new TrackingException("Tracking is only possible for confirmed (paid) bookings.");
        }
    }

    private Booking requireConfirmed(long bookingId) throws NotFoundException, TrackingException,
            DataAccessException {
        Booking booking = bookings.findAnyById(bookingId)
                .orElseThrow(() -> new NotFoundException("Booking " + bookingId));
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new TrackingException("Tracking is only possible for confirmed (paid) bookings.");
        }
        return booking;
    }

    private static void requireAdmin(User user) {
        if (user == null || !user.isAdmin()) {
            throw new SecurityException("Administrator only");
        }
    }

    /**
     * Parses a milestone form value; "NONE" or blank means no milestone yet.
     *
     * @param value form value
     * @return milestone, or null for none
     * @throws TrackingException if unknown
     */
    public static Milestone parseMilestone(String value) throws TrackingException {
        if (Strings.isBlank(value) || "NONE".equals(value.trim())) {
            return null;
        }
        for (Milestone m : Milestone.values()) {
            if (m.name().equals(value.trim())) {
                return m;
            }
        }
        throw new TrackingException("Unknown milestone.");
    }
}

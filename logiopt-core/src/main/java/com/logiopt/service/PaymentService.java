package com.logiopt.service;

import com.logiopt.dao.BookingDAO;
import com.logiopt.dao.PaymentDAO;
import com.logiopt.exception.BookingException;
import com.logiopt.exception.DataAccessException;
import com.logiopt.exception.NotFoundException;
import com.logiopt.model.Booking;
import com.logiopt.model.BookingStatus;
import com.logiopt.model.Milestone;
import com.logiopt.model.NotificationType;
import com.logiopt.model.Payment;
import com.logiopt.model.PaymentMethod;
import com.logiopt.model.PaymentStatus;
import com.logiopt.model.User;
import com.logiopt.util.Strings;

import java.util.List;

/**
 * SIMULATED payment only: no gateway and no card data. Each attempt is created PENDING and moved to
 * PAID or FAILED in the same transaction, with the amount taken from the booking snapshot. A PAID
 * attempt confirms the booking; after a FAILED attempt the booking stays unpaid and can be retried
 * (new attempt number) or cancelled. Only the booking's owner can pay; the administrator can view
 * attempts but not pay.
 */
public class PaymentService {

    /** Description of the BOOKED event recorded with a successful payment. */
    static final String BOOKED_TEXT = "Booking confirmed by a successful (simulated) payment.";

    private final BookingDAO bookings;
    private final PaymentDAO payments;
    private final NotificationService notifications;

    /**
     * Creates the service.
     *
     * @param bookings booking DAO
     * @param payments      payment DAO
     * @param notifications in-app notifications (sent after commit)
     */
    public PaymentService(BookingDAO bookings, PaymentDAO payments, NotificationService notifications) {
        if (bookings == null || payments == null || notifications == null) {
            throw new IllegalArgumentException("Booking and payment DAOs and notifications are required");
        }
        this.bookings = bookings;
        this.payments = payments;
        this.notifications = notifications;
    }

    /**
     * Parses a method from a form value.
     *
     * @param value form value (UPI, CARD or NET_BANKING)
     * @return method
     * @throws BookingException if missing or unknown
     */
    public static PaymentMethod parseMethod(String value) throws BookingException {
        if (!Strings.isBlank(value)) {
            for (PaymentMethod m : PaymentMethod.values()) {
                if (m.name().equals(value.trim())) {
                    return m;
                }
            }
        }
        throw new BookingException("Choose a payment method.");
    }

    /**
     * Records one simulated attempt for the user's booking.
     *
     * @param user      signed-in user (must own the booking)
     * @param bookingId booking id
     * @param method    method label
     * @param success   true for "Simulate success", false for "Simulate failure"
     * @return the recorded attempt
     * @throws NotFoundException   if the booking is missing or not the user's
     * @throws BookingException    if the booking is already paid or cancelled
     * @throws DataAccessException on database failure
     */
    public Payment simulate(User user, long bookingId, PaymentMethod method, boolean success)
            throws NotFoundException, BookingException, DataAccessException {
        if (method == null) {
            throw new BookingException("Choose a payment method.");
        }
        PaymentStatus result = success ? PaymentStatus.PAID : PaymentStatus.FAILED;
        if (!PaymentStatus.PENDING.canTransitionTo(result)) {
            throw new IllegalStateException("PENDING cannot become " + result);
        }
        Booking booking = bookings.findForOwner(bookingId, user.getUserId())
                .orElseThrow(() -> new NotFoundException("Booking " + bookingId));
        BookingService.requireTransition(booking.getStatus(), BookingStatus.CONFIRMED);
        PaymentDAO.AttemptResult recorded = payments.recordAttempt(bookingId, user.getUserId(), method, result,
                BookingStatus.PENDING_PAYMENT, BookingStatus.CONFIRMED, Milestone.BOOKED, BOOKED_TEXT);
        switch (recorded.getOutcome()) {
            case RECORDED:
                notifyRecorded(user, recorded.getPayment());
                return recorded.getPayment();
            case NOT_FOUND:
                throw new NotFoundException("Booking " + bookingId);
            default:
                Booking now = bookings.findForOwner(bookingId, user.getUserId())
                        .orElseThrow(() -> new NotFoundException("Booking " + bookingId));
                BookingService.requireTransition(now.getStatus(), BookingStatus.CONFIRMED);
                throw new BookingException("Another payment for this booking is in progress; reload and try again.");
        }
    }

    private void notifyRecorded(User owner, Payment p) {
        String attempt = "attempt-" + p.getAttemptNo();
        if (p.getStatus() == PaymentStatus.PAID) {
            notifications.notify(owner.getUserId(), NotificationType.PAYMENT_SUCCEEDED, p.getBookingId(), attempt,
                    "Simulated payment of INR " + p.getAmount().toPlainString() + " for booking " + p.getBookingId()
                            + " succeeded. The booking is confirmed.");
            notifications.notify(owner.getUserId(), NotificationType.TRACKING_UPDATE, p.getBookingId(),
                    Milestone.BOOKED.name(), "Booking " + p.getBookingId() + ": " + Milestone.BOOKED.getLabel() + ". "
                            + BOOKED_TEXT);
        } else {
            notifications.notify(owner.getUserId(), NotificationType.PAYMENT_FAILED, p.getBookingId(), attempt,
                    "Simulated payment attempt " + p.getAttemptNo() + " for booking " + p.getBookingId()
                            + " failed. You can try again or cancel the booking.");
        }
    }

    /**
     * Lists the attempts of a booking the caller has already been allowed to view.
     *
     * @param booking booking returned by BookingService.view
     * @return attempts in order
     * @throws DataAccessException on database failure
     */
    public List<Payment> attempts(Booking booking) throws DataAccessException {
        return payments.findByBooking(booking.getBookingId());
    }
}

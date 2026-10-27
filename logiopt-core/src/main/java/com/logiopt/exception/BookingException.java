package com.logiopt.exception;

/**
 * Thrown when a booking, payment or cancellation is not allowed (for example a past dispatch date,
 * an ineligible route, a second active booking or cancelling a paid booking). The message is shown
 * to the user.
 */
public class BookingException extends LogiOptException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message user-facing reason
     */
    public BookingException(String message) {
        super(message);
    }
}

package com.logiopt.exception;

/**
 * Thrown when a tracking event is not allowed: the booking is not CONFIRMED, or the requested
 * milestone is not the single legal next one (skipping or going back). The message is shown to the
 * administrator.
 */
public class TrackingException extends LogiOptException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message user-facing reason
     */
    public TrackingException(String message) {
        super(message);
    }
}

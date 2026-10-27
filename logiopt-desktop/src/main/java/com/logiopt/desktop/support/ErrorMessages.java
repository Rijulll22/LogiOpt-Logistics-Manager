package com.logiopt.desktop.support;

import com.logiopt.exception.AccountChangeException;
import com.logiopt.exception.BookingException;
import com.logiopt.exception.DataAccessException;
import com.logiopt.exception.InvalidRegistrationException;
import com.logiopt.exception.InvalidShipmentException;
import com.logiopt.exception.InvalidStrategyException;
import com.logiopt.exception.NotFoundException;
import com.logiopt.exception.ShipmentNotEditableException;
import com.logiopt.exception.TrackingException;

import java.util.concurrent.RejectedExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Turns exceptions into short, friendly messages for dialogs and banners. Never shows a stack trace,
 * class name, SQL or configuration detail; unexpected failures are logged with their details.
 */
public final class ErrorMessages {

    /** Database unreachable or a query failed. */
    public static final String DATABASE_UNAVAILABLE = "The database is unavailable. Please try again in a moment.";
    /**
     * Missing resource, one that belongs to another user, or one only the administrator may use (all
     * look the same, as on the web).
     */
    public static final String NOT_FOUND = "The requested item does not exist.";
    /** Anything unexpected. */
    public static final String UNEXPECTED = "Something went wrong. Please try again.";
    /** Work submitted while the client is closing. */
    public static final String CLOSING = "LogiOpt is closing.";

    private static final Logger LOG = Logger.getLogger(ErrorMessages.class.getName());

    private ErrorMessages() {
    }

    /**
     * @param error failure from a background task
     * @return message to show
     */
    public static String of(Throwable error) {
        if (error instanceof DataAccessException) {
            LOG.log(Level.WARNING, "Database operation failed", error);
            return DATABASE_UNAVAILABLE;
        }
        if (error instanceof NotFoundException || error instanceof SecurityException) {
            return NOT_FOUND;
        }
        if (error instanceof InvalidShipmentException) {
            return String.join(" ", ((InvalidShipmentException) error).getErrors());
        }
        if (error instanceof AccountChangeException) {
            return String.join(" ", ((AccountChangeException) error).getErrors());
        }
        if (error instanceof InvalidRegistrationException) {
            return String.join(" ", ((InvalidRegistrationException) error).getErrors());
        }
        if (error instanceof ShipmentNotEditableException || error instanceof BookingException
                || error instanceof TrackingException || error instanceof InvalidStrategyException) {
            return error.getMessage();
        }
        if (error instanceof RejectedExecutionException) {
            return CLOSING;
        }
        LOG.log(Level.SEVERE, "Unexpected failure in the desktop client", error);
        return UNEXPECTED;
    }
}

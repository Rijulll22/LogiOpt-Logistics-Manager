package com.logiopt.exception;

/**
 * Checked exception wrapping database failures (usually a SQLException).
 */
public class DataAccessException extends LogiOptException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message description of the failed operation (never contains credentials)
     * @param cause   underlying cause
     */
    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates the exception without a cause.
     *
     * @param message description of the failed operation
     */
    public DataAccessException(String message) {
        super(message);
    }
}

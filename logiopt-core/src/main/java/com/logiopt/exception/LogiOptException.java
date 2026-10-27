package com.logiopt.exception;

/**
 * Checked base exception for all recoverable LogiOpt business errors.
 */
public class LogiOptException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * Creates an exception with a message.
     *
     * @param message description of the problem
     */
    public LogiOptException(String message) {
        super(message);
    }

    /**
     * Creates an exception with a message and a cause.
     *
     * @param message description of the problem
     * @param cause   underlying cause
     */
    public LogiOptException(String message, Throwable cause) {
        super(message, cause);
    }
}

package com.logiopt.exception;

/**
 * Thrown when a strategy key is null, blank or unknown.
 */
public class InvalidStrategyException extends LogiOptException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message description of the invalid key
     */
    public InvalidStrategyException(String message) {
        super(message);
    }
}

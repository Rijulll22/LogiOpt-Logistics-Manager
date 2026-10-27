package com.logiopt.exception;

/**
 * Unchecked exception thrown when route or leg configuration data is invalid.
 */
public class RouteConfigurationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message description of the invalid configuration
     */
    public RouteConfigurationException(String message) {
        super(message);
    }
}

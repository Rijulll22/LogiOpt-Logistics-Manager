package com.logiopt.exception;

/**
 * Thrown when a shipment is edited that is no longer a draft. The message is shown to the user.
 */
public class ShipmentNotEditableException extends LogiOptException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message user-facing explanation
     */
    public ShipmentNotEditableException(String message) {
        super(message);
    }
}

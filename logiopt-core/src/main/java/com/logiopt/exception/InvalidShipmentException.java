package com.logiopt.exception;

import java.util.List;

/**
 * Thrown when a shipment fails validation; carries every validation error found.
 */
public class InvalidShipmentException extends LogiOptException {

    private static final long serialVersionUID = 1L;

    private final List<String> errors;

    /**
     * Creates the exception from the full list of validation errors.
     *
     * @param errors all validation errors (copied)
     */
    public InvalidShipmentException(List<String> errors) {
        super("Invalid shipment: " + String.join("; ", errors));
        this.errors = List.copyOf(errors);
    }

    /**
     * Returns all validation errors.
     *
     * @return unmodifiable list of error messages
     */
    public List<String> getErrors() {
        return errors;
    }
}

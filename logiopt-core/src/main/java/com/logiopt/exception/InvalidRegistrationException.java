package com.logiopt.exception;

import java.util.List;

/**
 * Thrown when registration input fails validation; carries every validation error found. It is never
 * thrown because an email address is already registered.
 */
public class InvalidRegistrationException extends LogiOptException {

    private static final long serialVersionUID = 1L;

    private final List<String> errors;

    /**
     * Creates the exception from the full list of validation errors.
     *
     * @param errors all validation errors (copied)
     */
    public InvalidRegistrationException(List<String> errors) {
        super("Invalid registration: " + String.join("; ", errors));
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

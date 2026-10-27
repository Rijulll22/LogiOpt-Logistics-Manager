package com.logiopt.exception;

/**
 * Thrown when a resource does not exist or belongs to another user. Both cases are reported the
 * same way (HTTP 404), so callers cannot learn whether someone else's resource exists.
 */
public class NotFoundException extends LogiOptException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message internal description (not shown to users)
     */
    public NotFoundException(String message) {
        super(message);
    }
}

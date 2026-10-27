package com.logiopt.exception;

import java.util.List;

/**
 * A change to the signed-in user's own account (name or password) was refused. The messages are
 * safe to show: they never contain a password, a hash or whether another account exists.
 */
public class AccountChangeException extends LogiOptException {

    private static final long serialVersionUID = 1L;

    private final List<String> errors;

    /**
     * @param errors user-facing reasons, in display order
     */
    public AccountChangeException(List<String> errors) {
        super(String.join(" ", errors));
        this.errors = List.copyOf(errors);
    }

    /**
     * @param error a single user-facing reason
     */
    public AccountChangeException(String error) {
        this(List.of(error));
    }

    /** @return the reasons, in display order */
    public List<String> getErrors() {
        return errors;
    }
}

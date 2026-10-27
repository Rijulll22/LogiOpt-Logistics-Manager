package com.logiopt.model;

/**
 * User roles. Registration always creates CUSTOMER; the single ADMIN is seeded from the environment.
 */
public enum Role {
    /** Registered customer; sees only their own data. */
    CUSTOMER,
    /** The one administrator account. */
    ADMIN
}

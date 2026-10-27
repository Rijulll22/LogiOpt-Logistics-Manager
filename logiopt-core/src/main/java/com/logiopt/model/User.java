package com.logiopt.model;

import java.io.Serializable;

/**
 * Immutable user identity, also stored in the HTTP session after sign-in. It never carries password data.
 */
public final class User implements Serializable {

    private static final long serialVersionUID = 1L;

    private final long userId;
    private final String email;
    private final String name;
    private final Role role;
    private final boolean enabled;

    /**
     * Creates a user.
     *
     * @param userId  user id
     * @param email   normalized (trimmed, lowercase) email
     * @param name    display name
     * @param role    role
     * @param enabled whether the account may sign in
     */
    public User(long userId, String email, String name, Role role, boolean enabled) {
        this.userId = userId;
        this.email = email;
        this.name = name;
        this.role = role;
        this.enabled = enabled;
    }

    /** @return user id */
    public long getUserId() {
        return userId;
    }

    /** @return normalized email */
    public String getEmail() {
        return email;
    }

    /** @return display name */
    public String getName() {
        return name;
    }

    /** @return role */
    public Role getRole() {
        return role;
    }

    /** @return whether the account may sign in */
    public boolean isEnabled() {
        return enabled;
    }

    /** @return true for the administrator */
    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}

package com.logiopt.model;

import java.time.LocalDateTime;

/**
 * A signed-in user's own account details for the account screen; never carries password data.
 */
public final class AccountProfile {

    private final long userId;
    private final String email;
    private final String name;
    private final Role role;
    private final LocalDateTime createdAt;

    /**
     * @param userId    user id
     * @param email     email address
     * @param name      display name
     * @param role      role
     * @param createdAt registration time
     */
    public AccountProfile(long userId, String email, String name, Role role, LocalDateTime createdAt) {
        this.userId = userId;
        this.email = email;
        this.name = name;
        this.role = role;
        this.createdAt = createdAt;
    }

    /** @return user id */
    public long getUserId() {
        return userId;
    }

    /** @return email address */
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

    /** @return registration time */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}

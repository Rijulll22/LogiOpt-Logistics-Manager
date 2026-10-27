package com.logiopt.desktop.support;

import com.logiopt.model.User;

import java.util.Optional;

/**
 * The signed-in user of this desktop client, held in memory only. Identity for every service call
 * comes from here, never from a form field.
 */
public final class Session {

    private volatile User user;

    /**
     * @param signedIn user returned by a successful sign-in
     */
    public void signIn(User signedIn) {
        if (signedIn == null) {
            throw new IllegalArgumentException("User is required");
        }
        this.user = signedIn;
    }

    /** Forgets the user. */
    public void signOut() {
        this.user = null;
    }

    /** @return the signed-in user, if any */
    public Optional<User> user() {
        return Optional.ofNullable(user);
    }

    /**
     * @return the signed-in user
     * @throws IllegalStateException if nobody is signed in
     */
    public User require() {
        User current = user;
        if (current == null) {
            throw new IllegalStateException("Nobody is signed in");
        }
        return current;
    }

    /** @return true if a user is signed in */
    public boolean isSignedIn() {
        return user != null;
    }

    /** @return true if the signed-in user is the administrator */
    public boolean isAdmin() {
        User current = user;
        return current != null && current.isAdmin();
    }
}

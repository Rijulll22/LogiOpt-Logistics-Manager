package com.logiopt.desktop.support;

import com.logiopt.model.User;

/**
 * Decides which screen is shown: public screens (sign-in, registration) without a session, pages in
 * the main shell with one, and only the pages the signed-in role may open (the services check the
 * role again). Holds no UI types; the {@link Shell} does the drawing. Called on the UI
 * thread only.
 */
public final class Navigator {

    /** Notice shown on the sign-in card after signing out. */
    public static final String SIGNED_OUT = "You have been signed out.";
    /** Notice shown when a page needs a signed-in user. */
    public static final String SIGN_IN_FIRST = "Please sign in to continue.";
    /** Notice shown when a page is not available to the signed-in role. */
    public static final String NOT_AVAILABLE = "That page is not available for this account.";

    /** Draws the screens chosen by the navigator. */
    public interface Shell {
        /**
         * Shows a full-window public screen.
         *
         * @param screen LOGIN or REGISTER
         * @param notice optional notice (null for none)
         */
        void showPublic(Screen screen, String notice);

        /**
         * Shows a page inside the main shell for the signed-in user.
         *
         * @param screen page
         * @param id     shipment (EDIT_SHIPMENT, COMPARISON) or booking (BOOKING) shown, else null
         * @param notice optional notice (null for none)
         */
        void showPage(Screen screen, Long id, String notice);
    }

    private final Session session;
    private final Shell shell;
    private Screen current;

    /**
     * @param session signed-in user holder
     * @param shell   drawing side
     */
    public Navigator(Session session, Shell shell) {
        if (session == null || shell == null) {
            throw new IllegalArgumentException("Session and shell are required");
        }
        this.session = session;
        this.shell = shell;
    }

    /** Shows the first screen: the sign-in card. */
    public void start() {
        showPublic(Screen.LOGIN, null);
    }

    /**
     * Opens a screen. Pages need a signed-in user (otherwise the sign-in card is shown); the public
     * screens lead to the dashboard while somebody is signed in.
     *
     * @param screen screen to open (not EDIT_SHIPMENT; use {@link #editShipment(long)})
     */
    public void go(Screen screen) {
        go(screen, null);
    }

    /**
     * Opens a screen with a notice.
     *
     * @param screen screen to open
     * @param notice notice to show (null for none)
     */
    public void go(Screen screen, String notice) {
        if (screen == Screen.EDIT_SHIPMENT || screen == Screen.COMPARISON || screen == Screen.BOOKING) {
            throw new IllegalArgumentException("Use editShipment, compare or openBooking with an id");
        }
        if (!screen.requiresSignIn()) {
            if (session.isSignedIn()) {
                showPage(Screen.DASHBOARD, null, null);
            } else {
                showPublic(screen, notice);
            }
            return;
        }
        if (!session.isSignedIn()) {
            showPublic(Screen.LOGIN, SIGN_IN_FIRST);
            return;
        }
        if (!screen.allows(session.isAdmin())) {
            showPage(Screen.DASHBOARD, null, NOT_AVAILABLE);
            return;
        }
        showPage(screen, null, notice);
    }

    /**
     * Opens the edit form of a shipment. Ownership and the DRAFT rule are checked by the service.
     *
     * @param shipmentId shipment id
     */
    public void editShipment(long shipmentId) {
        withId(Screen.EDIT_SHIPMENT, shipmentId, null);
    }

    /**
     * Opens the route comparison of a shipment. Ownership is checked by the services.
     *
     * @param shipmentId shipment id
     */
    public void compare(long shipmentId) {
        withId(Screen.COMPARISON, shipmentId, null);
    }

    /**
     * Opens a booking. Ownership is checked by the services.
     *
     * @param bookingId booking id
     * @param notice    notice to show (null for none)
     */
    public void openBooking(long bookingId, String notice) {
        withId(Screen.BOOKING, bookingId, notice);
    }

    private void withId(Screen screen, long id, String notice) {
        if (!session.isSignedIn()) {
            showPublic(Screen.LOGIN, SIGN_IN_FIRST);
            return;
        }
        if (!screen.allows(session.isAdmin())) {
            showPage(Screen.DASHBOARD, null, NOT_AVAILABLE);
            return;
        }
        showPage(screen, id, notice);
    }

    /**
     * Records a successful sign-in and opens the dashboard.
     *
     * @param user signed-in user
     */
    public void signedIn(User user) {
        session.signIn(user);
        showPage(Screen.DASHBOARD, null, null);
    }

    /** Forgets the user and shows the sign-in card. */
    public void signOut() {
        session.signOut();
        showPublic(Screen.LOGIN, SIGNED_OUT);
    }

    /** @return the screen shown last, or null before {@link #start()} */
    public Screen current() {
        return current;
    }

    private void showPublic(Screen screen, String notice) {
        current = screen;
        shell.showPublic(screen, notice);
    }

    private void showPage(Screen screen, Long id, String notice) {
        current = screen;
        shell.showPage(screen, id, notice);
    }
}

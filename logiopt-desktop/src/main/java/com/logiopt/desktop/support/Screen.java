package com.logiopt.desktop.support;

import java.util.ArrayList;
import java.util.List;

/** The screens of the desktop client, with their titles, who may open them and whether they are in the menu. */
public enum Screen {
    /** Sign-in card. */
    LOGIN("Sign in", false, false, Audience.EVERYONE),
    /** Registration card. */
    REGISTER("Create an account", false, false, Audience.EVERYONE),
    /** Counts and tracking overview (system-wide counts for the administrator). */
    DASHBOARD("Dashboard", true, true, Audience.EVERYONE),
    /** Saved shipments. */
    SHIPMENTS("My shipments", true, true, Audience.CUSTOMER),
    /** New shipment form. */
    NEW_SHIPMENT("New shipment", true, true, Audience.CUSTOMER),
    /** Edit form for a DRAFT shipment (opened from the shipments list). */
    EDIT_SHIPMENT("Edit shipment", true, false, Audience.CUSTOMER),
    /** The signed-in user's bookings. */
    BOOKINGS("My bookings", true, true, Audience.CUSTOMER),
    /** Route comparison of one shipment (opened from a shipment). */
    COMPARISON("Compare routes", true, false, Audience.CUSTOMER),
    /** One booking with its summary, payment, cancellation and tracking timeline (opened from a booking). */
    BOOKING("Booking", true, false, Audience.EVERYONE),
    /** The signed-in user's notifications. */
    NOTIFICATIONS("Notifications", true, true, Audience.CUSTOMER),
    /** Report generation and the user's reports. */
    REPORTS("Reports", true, true, Audience.CUSTOMER),
    /** Shipment, booking and report history. */
    HISTORY("History", true, true, Audience.CUSTOMER),
    /** Every user account (administrator only). */
    ADMIN_USERS("Users", true, true, Audience.ADMIN),
    /** Every booking, read-only (administrator only). */
    ADMIN_BOOKINGS("All bookings", true, true, Audience.ADMIN),
    /** Tracking of confirmed bookings (administrator only). */
    ADMIN_TRACKING("Tracking", true, true, Audience.ADMIN),
    /** The signed-in user's profile, name and password. */
    ACCOUNT("Account", true, true, Audience.EVERYONE);

    /** Who may open a screen. */
    public enum Audience {
        /** Customers and the administrator. */
        EVERYONE,
        /** Customers only (the administrator has no shipments, bookings or notifications). */
        CUSTOMER,
        /** The administrator only. */
        ADMIN
    }

    private final String title;
    private final boolean requiresSignIn;
    private final boolean inMenu;
    private final Audience audience;

    Screen(String title, boolean requiresSignIn, boolean inMenu, Audience audience) {
        this.title = title;
        this.requiresSignIn = requiresSignIn;
        this.inMenu = inMenu;
        this.audience = audience;
    }

    /** @return page title */
    public String getTitle() {
        return title;
    }

    /** @return true if only a signed-in user may open it */
    public boolean requiresSignIn() {
        return requiresSignIn;
    }

    /** @return true if it appears in the sidebar menu */
    public boolean isInMenu() {
        return inMenu;
    }

    /** @return true if only the administrator may open it */
    public boolean isAdminOnly() {
        return audience == Audience.ADMIN;
    }

    /**
     * @param admin true for the administrator
     * @return true if that role may open this screen
     */
    public boolean allows(boolean admin) {
        return audience == Audience.EVERYONE || (audience == Audience.ADMIN) == admin;
    }

    /** @return the customer's sidebar menu entries, in order */
    public static List<Screen> menu() {
        return menu(false);
    }

    /**
     * @param admin true for the administrator
     * @return the sidebar menu entries of that role, in order
     */
    public static List<Screen> menu(boolean admin) {
        List<Screen> menu = new ArrayList<>();
        for (Screen s : values()) {
            if (s.inMenu && s.allows(admin)) {
                menu.add(s);
            }
        }
        return menu;
    }
}

package com.logiopt.model;

import java.util.Collections;
import java.util.Map;

/**
 * System-wide counts for the administrator's dashboard. Every role and status appears in the maps
 * (zero when there are none), in declaration order.
 */
public final class AdminDashboard {

    private final Map<Role, Integer> usersByRole;
    private final Map<BookingStatus, Integer> bookingsByStatus;
    private final int confirmedAwaitingTracking;

    /**
     * Creates the dashboard.
     *
     * @param usersByRole               enabled user count per role (the disabled demo user is not counted)
     * @param bookingsByStatus          booking count per status
     * @param confirmedAwaitingTracking CONFIRMED bookings whose tracking has not reached DELIVERED
     */
    public AdminDashboard(Map<Role, Integer> usersByRole, Map<BookingStatus, Integer> bookingsByStatus,
                          int confirmedAwaitingTracking) {
        this.usersByRole = Collections.unmodifiableMap(usersByRole);
        this.bookingsByStatus = Collections.unmodifiableMap(bookingsByStatus);
        this.confirmedAwaitingTracking = confirmedAwaitingTracking;
    }

    /** @return enabled user count per role */
    public Map<Role, Integer> getUsersByRole() {
        return usersByRole;
    }

    /** @return all enabled users */
    public int getUserTotal() {
        return usersByRole.values().stream().mapToInt(Integer::intValue).sum();
    }

    /** @return booking count per status */
    public Map<BookingStatus, Integer> getBookingsByStatus() {
        return bookingsByStatus;
    }

    /** @return all bookings */
    public int getBookingTotal() {
        return bookingsByStatus.values().stream().mapToInt(Integer::intValue).sum();
    }

    /** @return CONFIRMED bookings whose tracking has not reached DELIVERED yet */
    public int getConfirmedAwaitingTracking() {
        return confirmedAwaitingTracking;
    }
}

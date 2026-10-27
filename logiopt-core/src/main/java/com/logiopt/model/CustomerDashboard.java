package com.logiopt.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Counts for a customer's dashboard, read from the database for the signed-in user only. Every
 * status appears in the maps (zero when the user has none), in declaration order.
 */
public final class CustomerDashboard {

    private final Map<ShipmentStatus, Integer> shipmentsByStatus;
    private final Map<BookingStatus, Integer> bookingsByStatus;
    private final int unreadNotifications;
    private final List<TrackingSummary> tracking;
    private final int trackingLimit;
    private final boolean trackingTruncated;

    /**
     * Creates the dashboard.
     *
     * @param shipmentsByStatus   shipment count per status
     * @param bookingsByStatus    booking count per status
     * @param unreadNotifications unread notification count
     * @param tracking            latest milestone of each confirmed booking shown (newest booking first)
     * @param trackingLimit       maximum number of confirmed bookings shown
     * @param trackingTruncated   true if more confirmed bookings exist than are shown
     */
    public CustomerDashboard(Map<ShipmentStatus, Integer> shipmentsByStatus,
                             Map<BookingStatus, Integer> bookingsByStatus, int unreadNotifications,
                             List<TrackingSummary> tracking, int trackingLimit, boolean trackingTruncated) {
        this.shipmentsByStatus = Collections.unmodifiableMap(shipmentsByStatus);
        this.bookingsByStatus = Collections.unmodifiableMap(bookingsByStatus);
        this.unreadNotifications = unreadNotifications;
        this.tracking = List.copyOf(tracking);
        this.trackingLimit = trackingLimit;
        this.trackingTruncated = trackingTruncated;
    }

    /** @return shipment count per status */
    public Map<ShipmentStatus, Integer> getShipmentsByStatus() {
        return shipmentsByStatus;
    }

    /** @return all shipments of the user */
    public int getShipmentTotal() {
        return shipmentsByStatus.values().stream().mapToInt(Integer::intValue).sum();
    }

    /** @return booking count per status */
    public Map<BookingStatus, Integer> getBookingsByStatus() {
        return bookingsByStatus;
    }

    /** @return all bookings of the user */
    public int getBookingTotal() {
        return bookingsByStatus.values().stream().mapToInt(Integer::intValue).sum();
    }

    /** @return unread notification count */
    public int getUnreadNotifications() {
        return unreadNotifications;
    }

    /** @return latest milestone of each confirmed booking shown */
    public List<TrackingSummary> getTracking() {
        return tracking;
    }

    /** @return maximum number of confirmed bookings shown */
    public int getTrackingLimit() {
        return trackingLimit;
    }

    /** @return true if more confirmed bookings exist than are shown */
    public boolean isTrackingTruncated() {
        return trackingTruncated;
    }
}

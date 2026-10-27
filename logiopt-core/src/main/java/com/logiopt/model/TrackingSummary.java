package com.logiopt.model;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Latest tracking milestone of one confirmed booking, for the dashboard.
 */
public final class TrackingSummary implements Serializable {

    private static final long serialVersionUID = 1L;

    private final long bookingId;
    private final long shipmentId;
    private final String routeName;
    private final String source;
    private final String destination;
    private final Milestone latest;
    private final LocalDateTime latestAt;

    /**
     * Creates the summary.
     *
     * @param bookingId   booking id
     * @param shipmentId  shipment id
     * @param routeName   booked route name (from the snapshot)
     * @param source      source city (from the snapshot)
     * @param destination destination city (from the snapshot)
     * @param latest      latest milestone, or null if none is recorded
     * @param latestAt    time of the latest milestone, or null
     */
    public TrackingSummary(long bookingId, long shipmentId, String routeName, String source, String destination,
                           Milestone latest, LocalDateTime latestAt) {
        this.bookingId = bookingId;
        this.shipmentId = shipmentId;
        this.routeName = routeName;
        this.source = source;
        this.destination = destination;
        this.latest = latest;
        this.latestAt = latestAt;
    }

    /** @return booking id */
    public long getBookingId() {
        return bookingId;
    }

    /** @return shipment id */
    public long getShipmentId() {
        return shipmentId;
    }

    /** @return booked route name */
    public String getRouteName() {
        return routeName;
    }

    /** @return source city */
    public String getSource() {
        return source;
    }

    /** @return destination city */
    public String getDestination() {
        return destination;
    }

    /** @return latest milestone, or null */
    public Milestone getLatest() {
        return latest;
    }

    /** @return time of the latest milestone, or null */
    public LocalDateTime getLatestAt() {
        return latestAt;
    }
}

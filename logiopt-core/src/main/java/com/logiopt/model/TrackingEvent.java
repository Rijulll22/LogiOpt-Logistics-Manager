package com.logiopt.model;

import java.time.LocalDateTime;

/**
 * One recorded tracking milestone of a booking.
 */
public final class TrackingEvent {

    private final long trackingId;
    private final long bookingId;
    private final long shipmentId;
    private final Milestone milestone;
    private final String description;
    private final TrackingSource source;
    private final Long recordedByUserId;
    private final LocalDateTime eventTime;

    /**
     * Creates an event.
     *
     * @param trackingId       event id
     * @param bookingId        booking id
     * @param shipmentId       shipment id
     * @param milestone        milestone
     * @param description      description
     * @param source           who recorded it
     * @param recordedByUserId administrator id for ADMIN events, otherwise null
     * @param eventTime        time recorded
     */
    public TrackingEvent(long trackingId, long bookingId, long shipmentId, Milestone milestone, String description,
                         TrackingSource source, Long recordedByUserId, LocalDateTime eventTime) {
        this.trackingId = trackingId;
        this.bookingId = bookingId;
        this.shipmentId = shipmentId;
        this.milestone = milestone;
        this.description = description;
        this.source = source;
        this.recordedByUserId = recordedByUserId;
        this.eventTime = eventTime;
    }

    /** @return event id */
    public long getTrackingId() {
        return trackingId;
    }

    /** @return booking id */
    public long getBookingId() {
        return bookingId;
    }

    /** @return shipment id */
    public long getShipmentId() {
        return shipmentId;
    }

    /** @return milestone */
    public Milestone getMilestone() {
        return milestone;
    }

    /** @return description */
    public String getDescription() {
        return description;
    }

    /** @return source */
    public TrackingSource getSource() {
        return source;
    }

    /** @return administrator id, or null */
    public Long getRecordedByUserId() {
        return recordedByUserId;
    }

    /** @return time recorded */
    public LocalDateTime getEventTime() {
        return eventTime;
    }
}

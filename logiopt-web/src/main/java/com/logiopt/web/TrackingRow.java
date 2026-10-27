package com.logiopt.web;

import com.logiopt.model.Booking;
import com.logiopt.model.Milestone;
import com.logiopt.model.TrackingEvent;

import java.util.List;

/**
 * One line of the administrator's tracking page: a CONFIRMED booking, its events, its latest
 * milestone and the single legal next milestone (null when DELIVERED).
 */
public final class TrackingRow {

    private final Booking booking;
    private final List<TrackingEvent> events;
    private final Milestone current;
    private final Milestone next;

    TrackingRow(Booking booking, List<TrackingEvent> events, Milestone current, Milestone next) {
        this.booking = booking;
        this.events = List.copyOf(events);
        this.current = current;
        this.next = next;
    }

    /** @return booking */
    public Booking getBooking() {
        return booking;
    }

    /** @return events, oldest first */
    public List<TrackingEvent> getEvents() {
        return events;
    }

    /** @return latest milestone, or null */
    public Milestone getCurrent() {
        return current;
    }

    /** @return form value for the latest milestone ("NONE" if none) */
    public String getCurrentKey() {
        return current == null ? "NONE" : current.name();
    }

    /** @return the single legal next milestone, or null if delivered */
    public Milestone getNext() {
        return next;
    }
}

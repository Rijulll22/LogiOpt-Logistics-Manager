package com.logiopt.model;

import java.util.Set;

/**
 * Booking states: PENDING_PAYMENT -> CONFIRMED (after a PAID attempt) or -> CANCELLED (unpaid only).
 * CONFIRMED and CANCELLED are final in Phase 3.
 */
public enum BookingStatus {
    /** Created, not yet paid; may be paid (retries allowed) or cancelled. */
    PENDING_PAYMENT,
    /** Paid; can no longer be cancelled. */
    CONFIRMED,
    /** Cancelled before payment; the shipment can be booked again. */
    CANCELLED;

    /**
     * @param target wanted next state
     * @return true if the transition is allowed
     */
    public boolean canTransitionTo(BookingStatus target) {
        return this == PENDING_PAYMENT && Set.of(CONFIRMED, CANCELLED).contains(target);
    }

    /** @return true while the booking holds its shipment (PENDING_PAYMENT or CONFIRMED) */
    public boolean isActive() {
        return this != CANCELLED;
    }
}

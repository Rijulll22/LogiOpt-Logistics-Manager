package com.logiopt.model;

/**
 * Lifecycle status of a shipment.
 */
public enum ShipmentStatus {
    /** Created, not yet planned. */
    DRAFT,
    /** Routes compared and planned. */
    PLANNED,
    /** Booked with a carrier. */
    BOOKED,
    /** Cancelled. */
    CANCELLED,
    /** Delivered. */
    COMPLETED
}

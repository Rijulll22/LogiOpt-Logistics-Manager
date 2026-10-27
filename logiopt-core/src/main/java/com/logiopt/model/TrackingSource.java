package com.logiopt.model;

/**
 * Who recorded a tracking event.
 */
public enum TrackingSource {
    /** Recorded by the application itself (the BOOKED event on payment). */
    SYSTEM,
    /** Entered by the administrator. */
    ADMIN,
    /** Produced by the progress simulation (admin "Advance" button or the optional scheduler). */
    SIMULATED
}

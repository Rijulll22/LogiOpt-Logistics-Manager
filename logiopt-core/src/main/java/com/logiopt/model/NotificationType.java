package com.logiopt.model;

/**
 * Kinds of in-app notification.
 */
public enum NotificationType {
    /** A booking was created. */
    BOOKING_CREATED,
    /** A simulated payment succeeded. */
    PAYMENT_SUCCEEDED,
    /** A simulated payment failed. */
    PAYMENT_FAILED,
    /** An unpaid booking was cancelled. */
    BOOKING_CANCELLED,
    /** A tracking milestone was recorded. */
    TRACKING_UPDATE
}

package com.logiopt.model;

import java.time.LocalDateTime;

/**
 * A booking as stored: state, timestamps, owner and its immutable snapshot.
 */
public final class Booking {

    private final long bookingId;
    private final long shipmentId;
    private final long ownerUserId;
    private final BookingStatus status;
    private final LocalDateTime bookedAt;
    private final LocalDateTime confirmedAt;
    private final LocalDateTime cancelledAt;
    private final BookingSnapshot snapshot;

    /**
     * Creates a booking.
     *
     * @param bookingId   booking id
     * @param shipmentId  shipment id
     * @param ownerUserId owner (the shipment's user)
     * @param status      state
     * @param bookedAt    creation time
     * @param confirmedAt confirmation time, or null
     * @param cancelledAt cancellation time, or null
     * @param snapshot    snapshot taken at booking time
     */
    public Booking(long bookingId, long shipmentId, long ownerUserId, BookingStatus status, LocalDateTime bookedAt,
                   LocalDateTime confirmedAt, LocalDateTime cancelledAt, BookingSnapshot snapshot) {
        this.bookingId = bookingId;
        this.shipmentId = shipmentId;
        this.ownerUserId = ownerUserId;
        this.status = status;
        this.bookedAt = bookedAt;
        this.confirmedAt = confirmedAt;
        this.cancelledAt = cancelledAt;
        this.snapshot = snapshot;
    }

    /** @return booking id */
    public long getBookingId() {
        return bookingId;
    }

    /** @return shipment id */
    public long getShipmentId() {
        return shipmentId;
    }

    /** @return owner user id */
    public long getOwnerUserId() {
        return ownerUserId;
    }

    /** @return state */
    public BookingStatus getStatus() {
        return status;
    }

    /** @return creation time */
    public LocalDateTime getBookedAt() {
        return bookedAt;
    }

    /** @return confirmation time, or null */
    public LocalDateTime getConfirmedAt() {
        return confirmedAt;
    }

    /** @return cancellation time, or null */
    public LocalDateTime getCancelledAt() {
        return cancelledAt;
    }

    /** @return snapshot */
    public BookingSnapshot getSnapshot() {
        return snapshot;
    }

    /** @return true if it can still be paid or cancelled */
    public boolean isUnpaid() {
        return status == BookingStatus.PENDING_PAYMENT;
    }
}

package com.logiopt.desktop.support;

import com.logiopt.model.BookingStatus;
import com.logiopt.model.ShipmentStatus;
import com.logiopt.report.ReportStatus;

/**
 * Colour family of a status chip, chosen the same way on every screen: DRAFT grey, BOOKED blue,
 * CONFIRMED and finished states green, CANCELLED and FAILED red, waiting states amber.
 */
public enum ChipKind {
    /** Grey: not started (DRAFT, PLANNED). */
    NEUTRAL,
    /** Blue: in progress (BOOKED, QUEUED, RUNNING). */
    INFO,
    /** Green: confirmed or finished (CONFIRMED, COMPLETED, DONE). */
    SUCCESS,
    /** Amber: waiting for the customer (PENDING_PAYMENT). */
    WARNING,
    /** Red: stopped (CANCELLED, FAILED). */
    DANGER;

    /** @return chip colour of a shipment status */
    public static ChipKind of(ShipmentStatus status) {
        if (status == null) {
            return NEUTRAL;
        }
        switch (status) {
            case BOOKED:
                return INFO;
            case COMPLETED:
                return SUCCESS;
            case CANCELLED:
                return DANGER;
            default:
                return NEUTRAL;
        }
    }

    /** @return chip colour of a booking status */
    public static ChipKind of(BookingStatus status) {
        if (status == null) {
            return NEUTRAL;
        }
        switch (status) {
            case PENDING_PAYMENT:
                return WARNING;
            case CONFIRMED:
                return SUCCESS;
            case CANCELLED:
                return DANGER;
            default:
                return NEUTRAL;
        }
    }

    /** @return chip colour of a report job status */
    public static ChipKind of(ReportStatus status) {
        if (status == null) {
            return NEUTRAL;
        }
        switch (status) {
            case DONE:
                return SUCCESS;
            case FAILED:
                return DANGER;
            default:
                return INFO;
        }
    }
}

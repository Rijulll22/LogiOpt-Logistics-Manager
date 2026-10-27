package com.logiopt.desktop.support;

import com.logiopt.model.BookingStatus;
import com.logiopt.model.PaymentStatus;
import com.logiopt.model.ShipmentStatus;
import com.logiopt.report.ReportStatus;

/** Plain-language meaning of every status, for chip tooltips and the "What do statuses mean?" note. */
public final class StatusHelp {

    /** Note shown under the My shipments table. */
    public static final String SHIPMENT_NOTE = "What do statuses mean? Draft: saved, not booked yet, still editable. "
            + "Booked: a route is booked; editing is closed. Completed: delivered. Cancelled: no longer active.";

    private StatusHelp() {
    }

    /** @return what a shipment status means */
    public static String of(ShipmentStatus status) {
        if (status == null) {
            return "";
        }
        switch (status) {
            case DRAFT:
                return "Saved, not booked yet. You can still edit it and compare routes.";
            case PLANNED:
                return "Planned, not booked yet.";
            case BOOKED:
                return "A route is booked. The shipment can no longer be edited.";
            case COMPLETED:
                return "Delivered. Tracking is complete.";
            default:
                return "Cancelled. It is no longer active.";
        }
    }

    /** @return what a booking status means */
    public static String of(BookingStatus status) {
        if (status == null) {
            return "";
        }
        switch (status) {
            case PENDING_PAYMENT:
                return "Booked and waiting for the simulated payment. You can pay or cancel it.";
            case CONFIRMED:
                return "Paid and confirmed. It can no longer be cancelled.";
            default:
                return "Cancelled before payment. The shipment can be booked again.";
        }
    }

    /** @return what a payment attempt status means */
    public static String of(PaymentStatus status) {
        if (status == null) {
            return "No payment has been attempted yet.";
        }
        switch (status) {
            case PAID:
                return "The simulated payment succeeded.";
            case FAILED:
                return "The simulated payment failed. You can try again.";
            default:
                return "The simulated payment is being processed.";
        }
    }

    /** @return what a report job status means */
    public static String of(ReportStatus status) {
        if (status == null) {
            return "";
        }
        switch (status) {
            case DONE:
                return "The report is ready to download.";
            case FAILED:
                return "The report could not be written.";
            default:
                return "The report is being written.";
        }
    }
}

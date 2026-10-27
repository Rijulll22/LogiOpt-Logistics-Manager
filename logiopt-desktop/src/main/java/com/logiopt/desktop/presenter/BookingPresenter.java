package com.logiopt.desktop.presenter;

import com.logiopt.app.AppServices;
import com.logiopt.desktop.presenter.Models.BookingDetailModel;
import com.logiopt.desktop.support.BackgroundRunner;
import com.logiopt.desktop.support.ErrorMessages;
import com.logiopt.model.Booking;
import com.logiopt.model.Payment;
import com.logiopt.model.PaymentStatus;
import com.logiopt.model.Shipment;
import com.logiopt.model.User;
import com.logiopt.service.PaymentService;

/**
 * One booking: the stored snapshot, payment attempts, the tracking timeline, the simulated payment
 * and cancellation. The
 * services check that it is the user's booking (another user's looks missing), that only unpaid
 * bookings are paid or cancelled, and that the administrator can view but not pay or cancel.
 */
public final class BookingPresenter {

    /** Shown after a successful simulated payment. */
    public static final String PAID = "Payment succeeded (simulation). Your booking is confirmed.";
    /** Shown after a failed simulated payment. */
    public static final String FAILED = "The simulated payment failed. No money was moved; you can try again.";
    /** Shown after cancelling. */
    public static final String CANCELLED = "The booking was cancelled. The shipment can be booked again.";

    /** What the booking page can show. */
    public interface View {
        /** Shows a loading state. */
        void showLoading();

        /** @param model booking detail */
        void showBooking(BookingDetailModel model);

        /** @param message error state text */
        void showError(String message);

        /** @param busy true while a payment or cancellation runs */
        void setBusy(boolean busy);

        /**
         * @param success true if the simulated payment succeeded
         * @param message result text
         */
        void showPaymentResult(boolean success, String message);

        /** @param message result of a cancellation or why an action failed */
        void showActionMessage(String message);
    }

    /** Result of a payment attempt and the reloaded booking. */
    private record Outcome(boolean paid, BookingDetailModel model) {
    }

    private final AppServices services;
    private final BackgroundRunner runner;
    private final User user;
    private final Runnable onChanged;
    private final View view;
    private long bookingId;
    private boolean busy;

    /**
     * @param services  shared services
     * @param runner    background runner
     * @param user      signed-in user
     * @param onChanged runs after a payment or cancellation (refreshes the unread badge)
     * @param view      page
     */
    public BookingPresenter(AppServices services, BackgroundRunner runner, User user, Runnable onChanged, View view) {
        this.services = services;
        this.runner = runner;
        this.user = user;
        this.onChanged = onChanged;
        this.view = view;
    }

    /** @param id one of the user's bookings */
    public void load(long id) {
        bookingId = id;
        view.showLoading();
        runner.run(this::detail, view::showBooking, error -> view.showError(ErrorMessages.of(error)));
    }

    private BookingDetailModel detail() throws Exception {
        Booking booking = services.getBookingService().view(user, bookingId);
        Shipment shipment = services.getShipmentDao().findByIdForUser(booking.getShipmentId(), booking.getOwnerUserId())
                .orElse(null);
        return ViewModels.bookingDetail(booking, shipment, services.getPaymentService().attempts(booking), user.isAdmin(),
                services.getTrackingService().timeline(booking));
    }

    /**
     * Runs one simulated payment attempt; a failed attempt can be retried.
     *
     * @param method  UPI, CARD or NET_BANKING (anything else is refused by the service)
     * @param success which outcome to simulate
     */
    public void pay(String method, boolean success) {
        if (busy) {
            return;
        }
        busy = true;
        view.setBusy(true);
        runner.run(() -> {
            Payment payment = services.getPaymentService().simulate(user, bookingId, PaymentService.parseMethod(method),
                    success);
            return new Outcome(payment.getStatus() == PaymentStatus.PAID, detail());
        }, result -> {
            busy = false;
            view.setBusy(false);
            view.showBooking(result.model());
            view.showPaymentResult(result.paid(), result.paid() ? PAID : FAILED);
            onChanged.run();
        }, error -> {
            busy = false;
            view.setBusy(false);
            view.showPaymentResult(false, ErrorMessages.of(error));
        });
    }

    /** Cancels the unpaid booking (the view asks for confirmation first). */
    public void cancel() {
        if (busy) {
            return;
        }
        busy = true;
        view.setBusy(true);
        runner.run(() -> {
            services.getBookingService().cancel(user, bookingId);
            return detail();
        }, model -> {
            busy = false;
            view.setBusy(false);
            view.showBooking(model);
            view.showActionMessage(CANCELLED);
            onChanged.run();
        }, error -> {
            busy = false;
            view.setBusy(false);
            view.showActionMessage(ErrorMessages.of(error));
        });
    }
}

package com.logiopt.desktop.view;

import com.logiopt.desktop.presenter.BookingPresenter;
import com.logiopt.desktop.presenter.Models.BookingDetailModel;
import com.logiopt.desktop.presenter.Models.PaymentRow;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.List;

/**
 * One booking: status chips (booking and payment, with tooltips), Pay now and Cancel while unpaid,
 * the tracking timeline, the summary from the stored snapshot and the payment attempts.
 */
public final class BookingDetailView implements BookingPresenter.View {

    private final VBox root = new VBox(Theme.space(3));
    private final Label notice = Theme.banner(Theme.Banner.SUCCESS);
    private PaymentView.Pay onPay = (method, ok) -> { };
    private Runnable onCancel = () -> { };
    private Runnable retry = () -> { };
    private Stage paymentStage;
    private PaymentView payment;
    private BookingDetailModel model;

    /** Builds the empty page. */
    public BookingDetailView() {
        root.setId("bookingDetail");
        notice.setId("bookingNotice");
    }

    /** @return page content */
    public VBox root() {
        return root;
    }

    /** @param action runs one simulated payment attempt */
    public void onPay(PaymentView.Pay action) {
        onPay = action;
    }

    /** @param action cancels the booking (after the view's confirmation) */
    public void onCancel(Runnable action) {
        onCancel = action;
    }

    /** @param action reloads after an error */
    public void onRetry(Runnable action) {
        retry = action;
    }

    /** @param message notice above the booking, or null */
    public void showNotice(String message) {
        Theme.setMessage(notice, message);
    }

    @Override
    public void showLoading() {
        root.getChildren().setAll(notice, PageStates.loading("Loading the booking…"));
    }

    @Override
    public void showError(String message) {
        root.getChildren().setAll(PageStates.error(message, "Try again", retry));
    }

    @Override
    public void showBooking(BookingDetailModel m) {
        this.model = m;
        VBox head = Theme.card();
        head.setId("bookingHeader");
        HBox chips = new HBox(Theme.space(1), Theme.fieldLabel("Booking"), Theme.chip(m.status(), m.statusChip(),
                m.statusHint()), Theme.fieldLabel("Payment"), Theme.chip(m.payment(), m.paymentChip(), m.paymentHint()));
        chips.setAlignment(Pos.CENTER_LEFT);
        chips.setId("bookingChips");
        Button pay = Theme.primaryButton("Pay now");
        pay.setId("payNow");
        pay.setOnAction(e -> openPayment());
        Button cancel = Theme.secondaryButton("Cancel booking");
        cancel.setId("cancelBooking");
        cancel.setOnAction(e -> {
            if (Dialogs.confirm("Cancel booking #" + m.bookingId() + "?", "Only unpaid bookings can be cancelled. The "
                    + "shipment becomes bookable again.", root)) {
                onCancel.run();
            }
        });
        HBox actions = new HBox(Theme.space(2), Theme.spacer());
        if (m.canCancel()) {
            actions.getChildren().add(cancel);
        }
        if (m.canPay()) {
            actions.getChildren().add(pay);
        }
        head.getChildren().addAll(new VBox(2, Theme.cardTitle("Booking #" + m.bookingId() + " · " + m.title()),
                Theme.muted("Booked " + m.bookedAt() + " for shipment #" + m.shipmentId() + ". Amount " + m.amount()
                        + ".")), chips, actions);
        VBox summary = Theme.card();
        summary.setId("bookingSummary");
        summary.getChildren().addAll(new VBox(2, Theme.cardTitle("Booked route"),
                Theme.muted("Stored when the booking was made; later route changes never alter it.")),
                SummaryBlock.of(m.summary(), List.of(new String[] {"Strategy", m.strategy()},
                        new String[] {"Reason", m.reason()}, new String[] {"Booked at", m.bookedAt()})));
        VBox payments = Theme.card();
        payments.setId("paymentsCard");
        TableView<PaymentRow> table = Tables.table("No payment attempts yet.");
        table.setId("paymentsTable");
        table.getColumns().add(Tables.text("Attempt", r -> "#" + r.attempt(), 0.6, 70));
        table.getColumns().add(Tables.text("Method", PaymentRow::method, 1.0, 100));
        table.getColumns().add(Tables.chip("Status", PaymentRow::status, PaymentRow::chip, PaymentRow::hint, 1.0, 96));
        table.getColumns().add(Tables.text("Time", PaymentRow::time, 1.2, 120));
        Tables.show(table, m.attempts());
        payments.getChildren().addAll(Theme.cardTitle("Payment attempts"), table);
        root.getChildren().setAll(notice, head, TimelineView.of(m.timeline()), summary, payments);
    }

    private void openPayment() {
        if (model == null) {
            return;
        }
        payment = new PaymentView(model.bookingId(), model.amount());
        payment.onPay((method, ok) -> onPay.accept(method, ok));
        Window owner = root.getScene() == null ? null : root.getScene().getWindow();
        if (owner == null) {
            return;
        }
        paymentStage = new Stage();
        paymentStage.initOwner(owner);
        paymentStage.initModality(Modality.WINDOW_MODAL);
        paymentStage.setTitle("Simulated payment");
        paymentStage.setScene(new Scene(payment.root(), 520, 470));
        payment.onClose(paymentStage::close);
        paymentStage.show();
    }

    @Override
    public void setBusy(boolean busy) {
        if (payment != null) {
            payment.setBusy(busy);
        }
        root.setDisable(busy);
    }

    @Override
    public void showPaymentResult(boolean success, String message) {
        if (payment != null) {
            payment.showResult(success, message);
        } else {
            showNotice(message);
        }
    }

    @Override
    public void showActionMessage(String message) {
        showNotice(message);
    }
}

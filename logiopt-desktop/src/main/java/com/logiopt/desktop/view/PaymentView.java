package com.logiopt.desktop.view;

import com.logiopt.model.PaymentMethod;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.FontWeight;
import javafx.util.StringConverter;

import java.util.List;

/**
 * The simulated payment: a permanent "SIMULATION – no money is moved" banner, the amount from the
 * booking's snapshot, a method dropdown and Simulate success / Simulate failure. No card fields.
 * After a failure both buttons work again, so the customer can retry.
 */
public final class PaymentView {

    /** Receives the chosen method and the outcome to simulate. */
    @FunctionalInterface
    public interface Pay {
        /**
         * @param method  UPI, CARD or NET_BANKING
         * @param success outcome to simulate
         */
        void accept(String method, boolean success);
    }

    /** The banner text, always shown. */
    public static final String SIMULATION = "SIMULATION – no money is moved";

    private final VBox root = new VBox(Theme.space(2));
    private final ComboBox<PaymentMethod> method = new ComboBox<>();
    private final Button success = Theme.primaryButton("Simulate success");
    private final Button failure = Theme.secondaryButton("Simulate failure");
    private final Button close = Theme.secondaryButton("Close");
    private final Label result = Theme.banner(Theme.Banner.SUCCESS);
    private final Label failed = Theme.banner(Theme.Banner.ERROR);
    private boolean paid;

    /**
     * @param bookingId booking being paid
     * @param amount    amount from the snapshot
     */
    public PaymentView(long bookingId, String amount) {
        Theme.applyRoot(root);
        root.setId("payment");
        root.setPadding(new Insets(Theme.space(3)));
        root.setBackground(Theme.background(Theme.SURFACE, 0));
        Label banner = Theme.text(SIMULATION, 14, FontWeight.BOLD, Color.web("#7A4B00"));
        banner.setId("simulationBanner");
        banner.setMaxWidth(Double.MAX_VALUE);
        banner.setPadding(new Insets(Theme.space(1) + 4, Theme.space(2), Theme.space(1) + 4, Theme.space(2)));
        banner.setBackground(Theme.background(Color.web("#FFF1D6"), 8));
        Label value = Theme.text(amount, 26, FontWeight.BOLD, Theme.TEXT);
        value.setId("paymentAmount");
        method.setId("paymentMethod");
        method.setItems(FXCollections.observableArrayList(List.of(PaymentMethod.values())));
        method.setConverter(new StringConverter<>() {
            @Override
            public String toString(PaymentMethod m) {
                return m == null ? "" : m.getLabel();
            }

            @Override
            public PaymentMethod fromString(String text) {
                return null;
            }
        });
        method.setValue(PaymentMethod.UPI);
        Theme.styleInput(method);
        method.setMaxWidth(260);
        success.setId("simulateSuccess");
        failure.setId("simulateFailure");
        close.setId("closePayment");
        result.setId("paymentResult");
        failed.setId("paymentFailed");
        HBox buttons = new HBox(Theme.space(2), Theme.spacer(), close, failure, success);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        root.getChildren().addAll(Theme.cardTitle("Pay for booking #" + bookingId), banner,
                new VBox(2, Theme.fieldLabel("Amount"), value, Theme.muted("Taken from the booking's stored summary.")),
                new VBox(Theme.space(1) - 2, Theme.fieldLabel("Payment method"), method), result, failed, Theme.divider(),
                buttons);
    }

    /** @return dialog content */
    public VBox root() {
        return root;
    }

    /** @param action receives the method and the outcome when a Simulate button is chosen */
    public void onPay(Pay action) {
        success.setOnAction(e -> action.accept(method.getValue() == null ? null : method.getValue().name(), true));
        failure.setOnAction(e -> action.accept(method.getValue() == null ? null : method.getValue().name(), false));
    }

    /** @param action closes the dialog */
    public void onClose(Runnable action) {
        close.setOnAction(e -> action.run());
    }

    /** @param busy true while the payment runs */
    public void setBusy(boolean busy) {
        success.setDisable(busy || paid);
        failure.setDisable(busy || paid);
        method.setDisable(busy || paid);
        close.setDisable(busy);
        success.setText(busy ? "Processing…" : "Simulate success");
    }

    /**
     * @param ok      true if the payment succeeded
     * @param message result text
     */
    public void showResult(boolean ok, String message) {
        paid = paid || ok;
        Theme.setMessage(result, ok ? message : null);
        Theme.setMessage(failed, ok ? null : message);
        setBusy(false);
    }
}

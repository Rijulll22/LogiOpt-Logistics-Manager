package com.logiopt.model;

/**
 * Payment method labels for the simulated payment. Only the label is stored; no card or account data.
 */
public enum PaymentMethod {
    /** UPI. */
    UPI("UPI"),
    /** Card (simulated; no card number is ever asked for). */
    CARD("Card"),
    /** Net banking. */
    NET_BANKING("Net banking");

    private final String label;

    PaymentMethod(String label) {
        this.label = label;
    }

    /** @return label shown to users */
    public String getLabel() {
        return label;
    }
}

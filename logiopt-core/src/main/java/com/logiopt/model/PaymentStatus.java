package com.logiopt.model;

/**
 * Simulated payment attempt states: PENDING -> PAID | FAILED. A FAILED attempt is never reused; a
 * retry is a new attempt.
 */
public enum PaymentStatus {
    /** Attempt started. */
    PENDING,
    /** Attempt succeeded; the booking is confirmed. */
    PAID,
    /** Attempt failed; the booking stays unpaid. */
    FAILED;

    /**
     * @param target wanted next state
     * @return true if the transition is allowed
     */
    public boolean canTransitionTo(PaymentStatus target) {
        return this == PENDING && (target == PAID || target == FAILED);
    }
}

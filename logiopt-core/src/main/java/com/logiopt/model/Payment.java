package com.logiopt.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One simulated payment attempt.
 */
public final class Payment {

    private final long paymentId;
    private final long bookingId;
    private final int attemptNo;
    private final BigDecimal amount;
    private final PaymentMethod method;
    private final PaymentStatus status;
    private final LocalDateTime createdAt;
    private final LocalDateTime paidAt;
    private final LocalDateTime failedAt;

    /**
     * Creates a payment attempt.
     *
     * @param paymentId payment id
     * @param bookingId booking id
     * @param attemptNo attempt number, from 1
     * @param amount    amount in INR (the booking snapshot's total)
     * @param method    method label
     * @param status    state
     * @param createdAt start time
     * @param paidAt    success time, or null
     * @param failedAt  failure time, or null
     */
    public Payment(long paymentId, long bookingId, int attemptNo, BigDecimal amount, PaymentMethod method,
                   PaymentStatus status, LocalDateTime createdAt, LocalDateTime paidAt, LocalDateTime failedAt) {
        this.paymentId = paymentId;
        this.bookingId = bookingId;
        this.attemptNo = attemptNo;
        this.amount = amount;
        this.method = method;
        this.status = status;
        this.createdAt = createdAt;
        this.paidAt = paidAt;
        this.failedAt = failedAt;
    }

    /** @return payment id */
    public long getPaymentId() {
        return paymentId;
    }

    /** @return booking id */
    public long getBookingId() {
        return bookingId;
    }

    /** @return attempt number */
    public int getAttemptNo() {
        return attemptNo;
    }

    /** @return amount in INR */
    public BigDecimal getAmount() {
        return amount;
    }

    /** @return method */
    public PaymentMethod getMethod() {
        return method;
    }

    /** @return state */
    public PaymentStatus getStatus() {
        return status;
    }

    /** @return start time */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /** @return success time, or null */
    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    /** @return failure time, or null */
    public LocalDateTime getFailedAt() {
        return failedAt;
    }
}

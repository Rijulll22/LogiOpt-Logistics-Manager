package com.logiopt.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Allowed and forbidden booking and payment state transitions.
 */
class BookingStateTest {

    @Test
    void bookingTransitions() {
        assertTrue(BookingStatus.PENDING_PAYMENT.canTransitionTo(BookingStatus.CONFIRMED));
        assertTrue(BookingStatus.PENDING_PAYMENT.canTransitionTo(BookingStatus.CANCELLED));
        for (BookingStatus target : BookingStatus.values()) {
            assertFalse(BookingStatus.CONFIRMED.canTransitionTo(target), "CONFIRMED -> " + target);
            assertFalse(BookingStatus.CANCELLED.canTransitionTo(target), "CANCELLED -> " + target);
        }
        assertFalse(BookingStatus.PENDING_PAYMENT.canTransitionTo(BookingStatus.PENDING_PAYMENT));
        assertTrue(BookingStatus.PENDING_PAYMENT.isActive());
        assertTrue(BookingStatus.CONFIRMED.isActive());
        assertFalse(BookingStatus.CANCELLED.isActive());
    }

    @Test
    void paymentTransitions() {
        assertTrue(PaymentStatus.PENDING.canTransitionTo(PaymentStatus.PAID));
        assertTrue(PaymentStatus.PENDING.canTransitionTo(PaymentStatus.FAILED));
        assertFalse(PaymentStatus.PENDING.canTransitionTo(PaymentStatus.PENDING));
        for (PaymentStatus target : PaymentStatus.values()) {
            assertFalse(PaymentStatus.PAID.canTransitionTo(target), "PAID -> " + target);
            assertFalse(PaymentStatus.FAILED.canTransitionTo(target), "FAILED -> " + target + " (retry is a new attempt)");
        }
    }

    @Test
    void paymentMethodLabels() {
        assertEquals("UPI", PaymentMethod.UPI.getLabel());
        assertEquals("Card", PaymentMethod.CARD.getLabel());
        assertEquals("Net banking", PaymentMethod.NET_BANKING.getLabel());
        assertEquals(3, PaymentMethod.values().length);
    }
}

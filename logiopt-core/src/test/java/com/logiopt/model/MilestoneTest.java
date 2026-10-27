package com.logiopt.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Milestone order: one legal next step, AT_TRANSFER only on multi-leg routes, DELIVERED terminal.
 */
class MilestoneTest {

    @Test
    void sequenceDependsOnTheNumberOfLegs() {
        assertEquals(List.of(Milestone.BOOKED, Milestone.PICKED_UP, Milestone.IN_TRANSIT, Milestone.OUT_FOR_DELIVERY,
                Milestone.DELIVERED), Milestone.sequence(false));
        assertEquals(List.of(Milestone.BOOKED, Milestone.PICKED_UP, Milestone.IN_TRANSIT, Milestone.AT_TRANSFER,
                Milestone.OUT_FOR_DELIVERY, Milestone.DELIVERED), Milestone.sequence(true));
    }

    @Test
    void nextIsTheOnlyLegalStep() {
        assertEquals(Milestone.BOOKED, Milestone.next(null, false));
        assertEquals(Milestone.OUT_FOR_DELIVERY, Milestone.next(Milestone.IN_TRANSIT, false));
        assertEquals(Milestone.AT_TRANSFER, Milestone.next(Milestone.IN_TRANSIT, true));
        assertEquals(Milestone.OUT_FOR_DELIVERY, Milestone.next(Milestone.AT_TRANSFER, true));
        assertNull(Milestone.next(Milestone.DELIVERED, false), "DELIVERED is terminal");
        assertNull(Milestone.next(Milestone.DELIVERED, true));
    }

    @Test
    void transferIsNotAMilestoneOfASingleLegRoute() {
        assertThrows(IllegalArgumentException.class, () -> Milestone.next(Milestone.AT_TRANSFER, false));
    }

    @Test
    void labels() {
        assertEquals("Out for delivery", Milestone.OUT_FOR_DELIVERY.getLabel());
        assertEquals("At transfer", Milestone.AT_TRANSFER.getLabel());
    }
}

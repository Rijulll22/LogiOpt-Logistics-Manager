package com.logiopt.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Tracking milestones in their only legal order: BOOKED, PICKED_UP, IN_TRANSIT, AT_TRANSFER (only
 * for routes with more than one leg), OUT_FOR_DELIVERY, DELIVERED. No skipping, no going back;
 * DELIVERED is terminal.
 */
public enum Milestone {
    /** Booking confirmed by payment (recorded automatically, source SYSTEM). */
    BOOKED("Booked"),
    /** Cargo picked up. */
    PICKED_UP("Picked up"),
    /** Cargo moving. */
    IN_TRANSIT("In transit"),
    /** Cargo at a transfer point; only on routes with more than one leg. */
    AT_TRANSFER("At transfer"),
    /** Final delivery leg. */
    OUT_FOR_DELIVERY("Out for delivery"),
    /** Delivered; terminal. */
    DELIVERED("Delivered");

    private final String label;

    Milestone(String label) {
        this.label = label;
    }

    /** @return label shown to users */
    public String getLabel() {
        return label;
    }

    /**
     * @param multiLeg true if the booked route has more than one leg
     * @return the milestones of such a route, in order
     */
    public static List<Milestone> sequence(boolean multiLeg) {
        List<Milestone> sequence = new ArrayList<>();
        for (Milestone m : values()) {
            if (m != AT_TRANSFER || multiLeg) {
                sequence.add(m);
            }
        }
        return sequence;
    }

    /**
     * The single legal milestone after {@code current}.
     *
     * @param current  latest recorded milestone, or null if none is recorded yet
     * @param multiLeg true if the booked route has more than one leg
     * @return next milestone, or null if {@code current} is DELIVERED (terminal)
     */
    public static Milestone next(Milestone current, boolean multiLeg) {
        List<Milestone> sequence = sequence(multiLeg);
        if (current == null) {
            return sequence.get(0);
        }
        int index = sequence.indexOf(current);
        if (index < 0) {
            throw new IllegalArgumentException(current + " is not a milestone of this route");
        }
        return index + 1 < sequence.size() ? sequence.get(index + 1) : null;
    }
}

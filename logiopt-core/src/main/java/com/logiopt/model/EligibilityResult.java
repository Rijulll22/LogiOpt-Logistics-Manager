package com.logiopt.model;

import java.util.List;

/**
 * Outcome of eligibility filtering: eligible routes and rejected routes with reasons.
 */
public final class EligibilityResult {

    private final List<Route> eligible;
    private final List<RejectedRoute> rejected;
    private final int routesForPair;

    /**
     * Creates an eligibility result.
     *
     * @param eligible      eligible routes (not null)
     * @param rejected      rejected routes with reasons (not null)
     * @param routesForPair number of candidates whose endpoints match the shipment
     */
    public EligibilityResult(List<Route> eligible, List<RejectedRoute> rejected, int routesForPair) {
        if (eligible == null || rejected == null) {
            throw new IllegalArgumentException("Eligible and rejected lists must not be null");
        }
        this.eligible = List.copyOf(eligible);
        this.rejected = List.copyOf(rejected);
        this.routesForPair = routesForPair;
    }

    /** @return true if at least one route is eligible */
    public boolean hasEligibleRoutes() {
        return !eligible.isEmpty();
    }

    /**
     * Explains the outcome, including why nothing is eligible.
     *
     * @return human-readable message
     */
    public String getMessage() {
        if (routesForPair == 0) {
            return "No routes are configured for this source and destination.";
        }
        if (eligible.isEmpty()) {
            return "No eligible routes: all " + routesForPair
                    + " routes configured for this source and destination were rejected (see reasons).";
        }
        return eligible.size() + " of " + routesForPair
                + " routes configured for this source and destination are eligible.";
    }

    /** @return eligible routes */
    public List<Route> getEligible() {
        return eligible;
    }

    /** @return rejected routes with reasons */
    public List<RejectedRoute> getRejected() {
        return rejected;
    }

    /** @return number of candidates matching the shipment's endpoints */
    public int getRoutesForPair() {
        return routesForPair;
    }
}

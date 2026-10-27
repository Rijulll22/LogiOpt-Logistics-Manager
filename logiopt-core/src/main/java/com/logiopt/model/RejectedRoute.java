package com.logiopt.model;

import com.logiopt.util.Strings;

/**
 * A route rejected by eligibility filtering, with the reason.
 */
public final class RejectedRoute {

    private final Route route;
    private final String reason;

    /**
     * Creates a rejection.
     *
     * @param route  rejected route (not null)
     * @param reason explanation (not blank)
     */
    public RejectedRoute(Route route, String reason) {
        if (route == null || Strings.isBlank(reason)) {
            throw new IllegalArgumentException("Route and reason are required");
        }
        this.route = route;
        this.reason = reason;
    }

    /** @return rejected route */
    public Route getRoute() {
        return route;
    }

    /** @return rejection reason */
    public String getReason() {
        return reason;
    }
}

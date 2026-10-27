package com.logiopt.model;

import com.logiopt.util.Strings;

/**
 * An evaluated route that a strategy did not consider, with the reason.
 */
public final class ExcludedRoute {

    private final RouteEvaluation evaluation;
    private final String reason;

    /**
     * Creates an exclusion.
     *
     * @param evaluation excluded evaluation (not null)
     * @param reason     explanation (not blank)
     */
    public ExcludedRoute(RouteEvaluation evaluation, String reason) {
        if (evaluation == null || Strings.isBlank(reason)) {
            throw new IllegalArgumentException("Evaluation and reason are required");
        }
        this.evaluation = evaluation;
        this.reason = reason;
    }

    /** @return excluded evaluation */
    public RouteEvaluation getEvaluation() {
        return evaluation;
    }

    /** @return exclusion reason */
    public String getReason() {
        return reason;
    }
}

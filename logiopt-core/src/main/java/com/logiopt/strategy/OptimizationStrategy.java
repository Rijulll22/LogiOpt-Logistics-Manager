package com.logiopt.strategy;

import com.logiopt.model.Recommendation;
import com.logiopt.model.RouteEvaluation;

import java.util.List;

/**
 * A strategy ranks existing evaluations; it never recomputes cost, duration or emissions.
 */
public interface OptimizationStrategy {

    /**
     * Returns the strategy key.
     *
     * @return key such as CHEAPEST
     */
    String getKey();

    /**
     * Returns the display name.
     *
     * @return human-readable name
     */
    String getDisplayName();

    /**
     * Ranks the evaluations and picks a route.
     *
     * @param evaluations evaluations to rank (instances are reused, never copied)
     * @return recommendation
     */
    Recommendation recommend(List<RouteEvaluation> evaluations);
}

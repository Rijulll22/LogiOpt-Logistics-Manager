package com.logiopt.model;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Complete planning outcome. Keeps three collections separate and unfiltered: eligibility
 * rejections, the full list of evaluations of all eligible routes, and the chosen strategy's
 * exclusions. Every evaluation referenced by the recommendation must be the same instance as
 * an item of the evaluations list.
 */
public final class PlanningResult {

    private final Shipment shipment;
    private final EligibilityResult eligibility;
    private final List<RouteEvaluation> evaluations;
    private final Recommendation recommendation;

    /**
     * Creates a planning result and enforces the identity invariant.
     *
     * @param shipment       planned shipment
     * @param eligibility    eligibility outcome
     * @param evaluations    evaluations of all eligible routes
     * @param recommendation chosen strategy's recommendation
     * @throws IllegalArgumentException if any argument is null or an evaluation is not shared by identity
     */
    public PlanningResult(Shipment shipment, EligibilityResult eligibility, List<RouteEvaluation> evaluations,
                          Recommendation recommendation) {
        if (shipment == null || eligibility == null || evaluations == null || recommendation == null) {
            throw new IllegalArgumentException("Planning result arguments must not be null");
        }
        Set<RouteEvaluation> known = Collections.newSetFromMap(new IdentityHashMap<>());
        known.addAll(evaluations);
        if (recommendation.getChosen() != null && !known.contains(recommendation.getChosen())) {
            throw new IllegalArgumentException("Chosen evaluation is not an instance from the evaluations list");
        }
        for (RouteEvaluation ranked : recommendation.getRanking()) {
            if (!known.contains(ranked)) {
                throw new IllegalArgumentException("Ranked evaluation is not an instance from the evaluations list");
            }
        }
        for (ExcludedRoute ex : recommendation.getExcluded()) {
            if (!known.contains(ex.getEvaluation())) {
                throw new IllegalArgumentException(
                        "Excluded evaluation is not an instance from the evaluations list");
            }
        }
        this.shipment = shipment;
        this.eligibility = eligibility;
        this.evaluations = List.copyOf(evaluations);
        this.recommendation = recommendation;
    }

    /** @return planned shipment */
    public Shipment getShipment() {
        return shipment;
    }

    /** @return eligibility outcome */
    public EligibilityResult getEligibility() {
        return eligibility;
    }

    /** @return eligibility rejections with reasons */
    public List<RejectedRoute> getRejections() {
        return eligibility.getRejected();
    }

    /** @return evaluations of ALL eligible routes (never trimmed by a strategy) */
    public List<RouteEvaluation> getEvaluations() {
        return evaluations;
    }

    /** @return chosen strategy's recommendation */
    public Recommendation getRecommendation() {
        return recommendation;
    }

    /**
     * Returns why routes in the comparison were not considered by the chosen strategy.
     *
     * @return map routeId to exclusion reason
     */
    public Map<Long, String> getStrategyExclusions() {
        return recommendation.getExclusionReasons();
    }
}

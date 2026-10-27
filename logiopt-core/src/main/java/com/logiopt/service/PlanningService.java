package com.logiopt.service;

import com.logiopt.exception.InvalidShipmentException;
import com.logiopt.exception.InvalidStrategyException;
import com.logiopt.model.EligibilityResult;
import com.logiopt.model.PlanningResult;
import com.logiopt.model.Recommendation;
import com.logiopt.model.Route;
import com.logiopt.model.RouteEvaluation;
import com.logiopt.model.Shipment;
import com.logiopt.strategy.OptimizationStrategy;

import java.util.List;

/**
 * Orchestrates one planning run: strategy lookup, validation, eligibility, a single evaluation
 * pass, and the chosen strategy on those same evaluation instances.
 */
public class PlanningService {

    private final ShipmentValidator validator;
    private final EligibilityService eligibilityService;
    private final RouteEvaluator evaluator;
    private final RecommendationService recommendationService;

    /**
     * Creates the planning service.
     *
     * @param validator             shipment validator
     * @param eligibilityService    eligibility filter
     * @param evaluator             route evaluator
     * @param recommendationService strategy registry
     */
    public PlanningService(ShipmentValidator validator, EligibilityService eligibilityService,
                           RouteEvaluator evaluator, RecommendationService recommendationService) {
        if (validator == null || eligibilityService == null || evaluator == null || recommendationService == null) {
            throw new IllegalArgumentException("Planning dependencies must not be null");
        }
        this.validator = validator;
        this.eligibilityService = eligibilityService;
        this.evaluator = evaluator;
        this.recommendationService = recommendationService;
    }

    /**
     * Plans a shipment. The new-shipment dispatch-date rule is not applied, so a saved shipment
     * whose dispatch date has passed can still be compared and reported.
     *
     * @param shipment    shipment
     * @param candidates  candidate routes
     * @param strategyKey strategy key
     * @return planning result
     * @throws InvalidStrategyException if the strategy key is invalid (checked first)
     * @throws InvalidShipmentException if the shipment is invalid
     */
    public PlanningResult plan(Shipment shipment, List<Route> candidates, String strategyKey)
            throws InvalidStrategyException, InvalidShipmentException {
        OptimizationStrategy strategy = recommendationService.requireStrategy(strategyKey);
        validator.validateForPlanning(shipment);
        EligibilityResult eligibility = eligibilityService.filter(shipment, candidates);
        List<RouteEvaluation> evaluations = evaluator.evaluateAll(shipment, eligibility.getEligible());
        Recommendation recommendation = strategy.recommend(evaluations);
        return new PlanningResult(shipment, eligibility, evaluations, recommendation);
    }
}

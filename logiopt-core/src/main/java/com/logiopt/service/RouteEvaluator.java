package com.logiopt.service;

import com.logiopt.model.CostBreakdown;
import com.logiopt.model.Route;
import com.logiopt.model.RouteEvaluation;
import com.logiopt.model.Shipment;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * The only component that calls CostService, DurationService and EmissionService.
 * Produces one immutable RouteEvaluation per route.
 */
public class RouteEvaluator {

    /** Disclaimer shown wherever estimates are displayed. */
    public static final String ESTIMATE_LABEL =
            "Estimates based on configured sample rates and factors; not live carrier data.";

    private final CostService costService;
    private final DurationService durationService;
    private final EmissionService emissionService;

    /**
     * Creates an evaluator.
     *
     * @param costService     cost calculator
     * @param durationService duration calculator
     * @param emissionService emission calculator
     */
    public RouteEvaluator(CostService costService, DurationService durationService,
                          EmissionService emissionService) {
        if (costService == null || durationService == null || emissionService == null) {
            throw new IllegalArgumentException("Calculators must not be null");
        }
        this.costService = costService;
        this.durationService = durationService;
        this.emissionService = emissionService;
    }

    /**
     * Evaluates one route for a shipment.
     *
     * @param shipment shipment
     * @param route    route
     * @return evaluation with notes for unavailable metrics
     */
    public RouteEvaluation evaluate(Shipment shipment, Route route) {
        CostBreakdown cost = costService.calculate(shipment, route);
        Double hours = durationService.calculateHours(route);
        LocalDateTime arrival = durationService.estimateArrival(shipment.getDispatchDate(), hours);
        Double emissions = emissionService.calculateKg(shipment, route);
        List<String> notes = new ArrayList<>();
        if (hours == null) {
            notes.add("Duration not available: at least one leg has no configured speed.");
        }
        if (emissions == null) {
            notes.add("Emissions not available: at least one leg has no configured emission factor.");
        }
        if (route.getReliability() == null) {
            notes.add("Reliability not available for this route.");
        }
        return new RouteEvaluation(route, cost, hours, arrival, emissions, notes);
    }

    /**
     * Evaluates every route exactly once.
     *
     * @param shipment shipment
     * @param routes   routes to evaluate
     * @return evaluations in input order
     */
    public List<RouteEvaluation> evaluateAll(Shipment shipment, List<Route> routes) {
        List<RouteEvaluation> result = new ArrayList<>(routes.size());
        for (Route route : routes) {
            result.add(evaluate(shipment, route));
        }
        return result;
    }
}

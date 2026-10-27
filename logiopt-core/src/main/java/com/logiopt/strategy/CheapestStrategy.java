package com.logiopt.strategy;

import com.logiopt.model.RouteEvaluation;

/**
 * Picks the route with the lowest total cost.
 */
public class CheapestStrategy extends AbstractRankingStrategy {

    /** Strategy key. */
    public static final String KEY = "CHEAPEST";

    /**
     * Creates the strategy.
     */
    public CheapestStrategy() {
        super(KEY, "Cheapest");
    }

    @Override
    protected Double metric(RouteEvaluation evaluation) {
        return evaluation.getTotalCost().doubleValue();
    }

    @Override
    protected String metricName() {
        return "total cost";
    }

    @Override
    protected String formatMetric(RouteEvaluation evaluation) {
        return "INR " + evaluation.getTotalCost().toPlainString();
    }

    @Override
    protected String missingReason() {
        return "Total cost not available";
    }
}

package com.logiopt.strategy;

import com.logiopt.model.RouteEvaluation;

import java.util.Locale;

/**
 * Picks the route with the lowest emissions.
 */
public class GreenestStrategy extends AbstractRankingStrategy {

    /** Strategy key. */
    public static final String KEY = "GREENEST";

    /**
     * Creates the strategy.
     */
    public GreenestStrategy() {
        super(KEY, "Greenest");
    }

    @Override
    protected Double metric(RouteEvaluation evaluation) {
        return evaluation.getEmissionsKg();
    }

    @Override
    protected String metricName() {
        return "emissions";
    }

    @Override
    protected String formatMetric(RouteEvaluation evaluation) {
        return String.format(Locale.ROOT, "%.2f kg CO2e", evaluation.getEmissionsKg());
    }

    @Override
    protected String missingReason() {
        return "Emissions not available";
    }
}

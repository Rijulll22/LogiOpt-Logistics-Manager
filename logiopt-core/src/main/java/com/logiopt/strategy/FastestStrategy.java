package com.logiopt.strategy;

import com.logiopt.model.RouteEvaluation;

import java.util.Locale;

/**
 * Picks the route with the shortest duration.
 */
public class FastestStrategy extends AbstractRankingStrategy {

    /** Strategy key. */
    public static final String KEY = "FASTEST";

    /**
     * Creates the strategy.
     */
    public FastestStrategy() {
        super(KEY, "Fastest");
    }

    @Override
    protected Double metric(RouteEvaluation evaluation) {
        return evaluation.getDurationHours();
    }

    @Override
    protected String metricName() {
        return "duration";
    }

    @Override
    protected String formatMetric(RouteEvaluation evaluation) {
        return String.format(Locale.ROOT, "%.2f h", evaluation.getDurationHours());
    }

    @Override
    protected String missingReason() {
        return "Duration not available";
    }
}

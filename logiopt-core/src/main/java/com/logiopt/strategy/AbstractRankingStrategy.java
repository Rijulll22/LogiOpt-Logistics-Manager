package com.logiopt.strategy;

import com.logiopt.model.ExcludedRoute;
import com.logiopt.model.Recommendation;
import com.logiopt.model.RouteEvaluation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Base for single-metric strategies. Evaluations lacking the required metric are excluded with
 * a reason; the rest are sorted by the metric with the common tie-break.
 */
public abstract class AbstractRankingStrategy implements OptimizationStrategy {

    /**
     * Common tie-break after the primary metric: total cost, duration, emissions, route id;
     * null values sort last.
     */
    public static final Comparator<RouteEvaluation> COMMON_TIE_BREAK =
            Comparator.comparing(RouteEvaluation::getTotalCost)
                    .thenComparing(RouteEvaluation::getDurationHours, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(RouteEvaluation::getEmissionsKg, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparingLong(RouteEvaluation::getRouteId);

    private final String key;
    private final String displayName;

    /**
     * Creates the strategy.
     *
     * @param key         strategy key
     * @param displayName display name
     */
    protected AbstractRankingStrategy(String key, String displayName) {
        this.key = key;
        this.displayName = displayName;
    }

    /**
     * Extracts the primary metric.
     *
     * @param evaluation evaluation
     * @return metric value, or null if unavailable
     */
    protected abstract Double metric(RouteEvaluation evaluation);

    /**
     * Describes the metric for reasons, e.g. "total cost".
     *
     * @return metric description
     */
    protected abstract String metricName();

    /**
     * Formats a metric value with its unit.
     *
     * @param evaluation evaluation whose metric is formatted
     * @return formatted value
     */
    protected abstract String formatMetric(RouteEvaluation evaluation);

    /**
     * Reason used when the metric is missing, e.g. "Emissions not available".
     *
     * @return exclusion reason
     */
    protected abstract String missingReason();

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public Recommendation recommend(List<RouteEvaluation> evaluations) {
        if (evaluations == null || evaluations.isEmpty()) {
            return Recommendation.none(key, displayName, List.of(), "No eligible routes to compare.");
        }
        List<RouteEvaluation> ranking = new ArrayList<>();
        List<ExcludedRoute> excluded = new ArrayList<>();
        for (RouteEvaluation evaluation : evaluations) {
            if (metric(evaluation) == null) {
                excluded.add(new ExcludedRoute(evaluation, missingReason()));
            } else {
                ranking.add(evaluation);
            }
        }
        if (ranking.isEmpty()) {
            return Recommendation.none(key, displayName, excluded,
                    "No route has " + metricName() + " available, so " + displayName + " cannot recommend a route.");
        }
        Function<RouteEvaluation, Double> primary = this::metric;
        ranking.sort(Comparator.comparing(primary, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(COMMON_TIE_BREAK));
        RouteEvaluation winner = ranking.get(0);
        StringBuilder reason = new StringBuilder();
        reason.append(displayName).append(": ").append(describe(winner)).append(" has the lowest ")
                .append(metricName()).append(", ").append(formatMetric(winner));
        if (ranking.size() > 1) {
            RouteEvaluation runnerUp = ranking.get(1);
            reason.append("; runner-up ").append(describe(runnerUp)).append(" at ").append(formatMetric(runnerUp));
        } else {
            reason.append("; no other route had this metric to compare");
        }
        reason.append('.');
        return new Recommendation(key, displayName, winner, ranking, excluded, Map.of(), reason.toString());
    }

    /**
     * Describes a route for reason texts.
     *
     * @param evaluation evaluation
     * @return text like "Rail direct (route 2)"
     */
    protected static String describe(RouteEvaluation evaluation) {
        return evaluation.getRouteName() + " (route " + evaluation.getRouteId() + ")";
    }
}

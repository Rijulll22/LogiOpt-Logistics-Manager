package com.logiopt.strategy;

import com.logiopt.model.ExcludedRoute;
import com.logiopt.model.Recommendation;
import com.logiopt.model.RouteEvaluation;
import com.logiopt.model.ScoreBreakdown;
import com.logiopt.util.Numbers;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.ToDoubleFunction;

/**
 * Weighted min-max score over cost, time, emissions and reliability; the lowest score wins.
 * Scores are relative to the routes currently compared. Routes missing any metric are excluded.
 */
public class BalancedStrategy implements OptimizationStrategy {

    /** Strategy key. */
    public static final String KEY = "BALANCED";
    /** Default cost weight. */
    public static final double W_COST = 0.35;
    /** Default time weight. */
    public static final double W_TIME = 0.30;
    /** Default emissions weight. */
    public static final double W_EMISSIONS = 0.20;
    /** Default reliability weight. */
    public static final double W_RELIABILITY = 0.15;

    private static final double WEIGHT_SUM_TOLERANCE = 1e-9;
    private static final String DISPLAY_NAME = "Balanced";

    private final double costWeight;
    private final double timeWeight;
    private final double emissionsWeight;
    private final double reliabilityWeight;

    /**
     * Creates the strategy with the default weights.
     */
    public BalancedStrategy() {
        this(W_COST, W_TIME, W_EMISSIONS, W_RELIABILITY);
    }

    /**
     * Creates the strategy with custom weights.
     *
     * @param costWeight        cost weight &gt;= 0
     * @param timeWeight        time weight &gt;= 0
     * @param emissionsWeight   emissions weight &gt;= 0
     * @param reliabilityWeight reliability weight &gt;= 0
     * @throws IllegalArgumentException if a weight is negative or not finite, or the sum is not 1.0
     */
    public BalancedStrategy(double costWeight, double timeWeight, double emissionsWeight, double reliabilityWeight) {
        double[] weights = {costWeight, timeWeight, emissionsWeight, reliabilityWeight};
        double sum = 0;
        for (double w : weights) {
            if (!Double.isFinite(w) || w < 0) {
                throw new IllegalArgumentException("Weights must be finite and >= 0");
            }
            sum += w;
        }
        if (Math.abs(sum - 1.0) > WEIGHT_SUM_TOLERANCE) {
            throw new IllegalArgumentException("Weights must sum to 1.0, was " + sum);
        }
        this.costWeight = costWeight;
        this.timeWeight = timeWeight;
        this.emissionsWeight = emissionsWeight;
        this.reliabilityWeight = reliabilityWeight;
    }

    @Override
    public String getKey() {
        return KEY;
    }

    @Override
    public String getDisplayName() {
        return DISPLAY_NAME;
    }

    @Override
    public Recommendation recommend(List<RouteEvaluation> evaluations) {
        if (evaluations == null || evaluations.isEmpty()) {
            return Recommendation.none(KEY, DISPLAY_NAME, List.of(), "No eligible routes to compare.");
        }
        List<RouteEvaluation> scorable = new ArrayList<>();
        List<ExcludedRoute> excluded = new ArrayList<>();
        for (RouteEvaluation evaluation : evaluations) {
            List<String> missing = new ArrayList<>();
            if (!evaluation.isDurationAvailable()) {
                missing.add("duration");
            }
            if (!evaluation.isEmissionsAvailable()) {
                missing.add("emissions");
            }
            if (!evaluation.isReliabilityAvailable()) {
                missing.add("reliability");
            }
            if (missing.isEmpty()) {
                scorable.add(evaluation);
            } else {
                excluded.add(new ExcludedRoute(evaluation,
                        "Balanced needs all four metrics; missing: " + String.join(", ", missing)));
            }
        }
        if (scorable.isEmpty()) {
            return Recommendation.none(KEY, DISPLAY_NAME, excluded,
                    "No route has all four metrics (cost, duration, emissions, reliability), so Balanced "
                            + "cannot recommend a route.");
        }
        ToDoubleFunction<RouteEvaluation> cost = e -> e.getTotalCost().doubleValue();
        ToDoubleFunction<RouteEvaluation> time = RouteEvaluation::getDurationHours;
        ToDoubleFunction<RouteEvaluation> emissions = RouteEvaluation::getEmissionsKg;
        ToDoubleFunction<RouteEvaluation> reliability = RouteEvaluation::getReliability;
        double[] costRange = range(scorable, cost);
        double[] timeRange = range(scorable, time);
        double[] emissionsRange = range(scorable, emissions);
        double[] reliabilityRange = range(scorable, reliability);

        Map<Long, ScoreBreakdown> scores = new LinkedHashMap<>();
        for (RouteEvaluation e : scorable) {
            double nCost = lowerIsBetter(cost.applyAsDouble(e), costRange);
            double nTime = lowerIsBetter(time.applyAsDouble(e), timeRange);
            double nEmissions = lowerIsBetter(emissions.applyAsDouble(e), emissionsRange);
            double nReliability = higherIsBetter(reliability.applyAsDouble(e), reliabilityRange);
            double wCost = costWeight * nCost;
            double wTime = timeWeight * nTime;
            double wEmissions = emissionsWeight * nEmissions;
            double wReliability = reliabilityWeight * nReliability;
            double total = Numbers.round6(wCost + wTime + wEmissions + wReliability);
            scores.put(e.getRouteId(), new ScoreBreakdown(e.getRouteId(), nCost, nTime, nEmissions, nReliability,
                    wCost, wTime, wEmissions, wReliability, total));
        }
        Comparator<RouteEvaluation> byScore =
                Comparator.comparingDouble(e -> scores.get(e.getRouteId()).getTotalScore());
        List<RouteEvaluation> ranking = new ArrayList<>(scorable);
        ranking.sort(byScore.thenComparing(AbstractRankingStrategy.COMMON_TIE_BREAK));
        RouteEvaluation winner = ranking.get(0);
        return new Recommendation(KEY, DISPLAY_NAME, winner, ranking, excluded, scores,
                reason(winner, ranking, scores));
    }

    private String reason(RouteEvaluation winner, List<RouteEvaluation> ranking, Map<Long, ScoreBreakdown> scores) {
        ScoreBreakdown s = scores.get(winner.getRouteId());
        StringBuilder text = new StringBuilder();
        text.append(String.format(Locale.ROOT,
                "Balanced: %s has the lowest score %.6f (cost %.6f + time %.6f + emissions %.6f + reliability %.6f;"
                        + " weights %.2f/%.2f/%.2f/%.2f)",
                AbstractRankingStrategy.describe(winner), s.getTotalScore(), s.getWeightedCost(), s.getWeightedTime(),
                s.getWeightedEmissions(), s.getWeightedReliability(),
                costWeight, timeWeight, emissionsWeight, reliabilityWeight));
        if (ranking.size() > 1) {
            RouteEvaluation runnerUp = ranking.get(1);
            text.append(String.format(Locale.ROOT, "; runner-up %s with %.6f",
                    AbstractRankingStrategy.describe(runnerUp), scores.get(runnerUp.getRouteId()).getTotalScore()));
        }
        text.append(". Scores are relative to the routes currently compared.");
        return text.toString();
    }

    private static double[] range(List<RouteEvaluation> items, ToDoubleFunction<RouteEvaluation> f) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (RouteEvaluation e : items) {
            double v = f.applyAsDouble(e);
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        return new double[] {min, max};
    }

    private static double lowerIsBetter(double x, double[] range) {
        double span = range[1] - range[0];
        return span == 0 ? 0 : (x - range[0]) / span;
    }

    private static double higherIsBetter(double r, double[] range) {
        double span = range[1] - range[0];
        return span == 0 ? 0 : (range[1] - r) / span;
    }
}

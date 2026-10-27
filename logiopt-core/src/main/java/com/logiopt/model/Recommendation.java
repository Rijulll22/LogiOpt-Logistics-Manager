package com.logiopt.model;

import com.logiopt.util.Strings;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Result of one strategy: chosen route (nullable), ranking, exclusions, scores and reason.
 * Invariant: if chosen is not null, ranking.get(0) is the same instance as chosen.
 */
public final class Recommendation {

    private final String strategyKey;
    private final String strategyName;
    private final RouteEvaluation chosen;
    private final List<RouteEvaluation> ranking;
    private final List<ExcludedRoute> excluded;
    private final Map<Long, ScoreBreakdown> scores;
    private final String reason;

    /**
     * Creates a recommendation.
     *
     * @param strategyKey  strategy key (not blank)
     * @param strategyName strategy display name (not blank)
     * @param chosen       chosen evaluation or null
     * @param ranking      ranked evaluations (not null)
     * @param excluded     exclusions with reasons (not null)
     * @param scores       score breakdowns by route id (not null)
     * @param reason       explanation (not blank)
     */
    public Recommendation(String strategyKey, String strategyName, RouteEvaluation chosen,
                          List<RouteEvaluation> ranking, List<ExcludedRoute> excluded,
                          Map<Long, ScoreBreakdown> scores, String reason) {
        if (Strings.isBlank(strategyKey) || Strings.isBlank(strategyName) || Strings.isBlank(reason)) {
            throw new IllegalArgumentException("Strategy key, name and reason must not be blank");
        }
        if (ranking == null || excluded == null || scores == null) {
            throw new IllegalArgumentException("Ranking, excluded and scores must not be null");
        }
        if (chosen != null && (ranking.isEmpty() || ranking.get(0) != chosen)) {
            throw new IllegalArgumentException("Chosen evaluation must be the first ranked instance");
        }
        this.strategyKey = strategyKey;
        this.strategyName = strategyName;
        this.chosen = chosen;
        this.ranking = List.copyOf(ranking);
        this.excluded = List.copyOf(excluded);
        this.scores = Collections.unmodifiableMap(new LinkedHashMap<>(scores));
        this.reason = reason;
    }

    /**
     * Creates a recommendation with no chosen route.
     *
     * @param strategyKey  strategy key
     * @param strategyName strategy display name
     * @param excluded     exclusions with reasons
     * @param reason       why nothing is recommended
     * @return recommendation without a chosen route
     */
    public static Recommendation none(String strategyKey, String strategyName, List<ExcludedRoute> excluded,
                                      String reason) {
        return new Recommendation(strategyKey, strategyName, null, List.of(), excluded, Map.of(), reason);
    }

    /** @return true if a route was chosen */
    public boolean isRecommended() {
        return chosen != null;
    }

    /**
     * Returns exclusion reasons keyed by route id, in exclusion order.
     *
     * @return unmodifiable map routeId to reason
     */
    public Map<Long, String> getExclusionReasons() {
        Map<Long, String> reasons = new LinkedHashMap<>();
        for (ExcludedRoute ex : excluded) {
            reasons.put(ex.getEvaluation().getRouteId(), ex.getReason());
        }
        return Collections.unmodifiableMap(reasons);
    }

    /**
     * Returns the score breakdown of the chosen route.
     *
     * @return breakdown, or null if none chosen or not scored
     */
    public ScoreBreakdown getChosenScore() {
        return chosen == null ? null : scores.get(chosen.getRouteId());
    }

    /** @return strategy key */
    public String getStrategyKey() {
        return strategyKey;
    }

    /** @return strategy display name */
    public String getStrategyName() {
        return strategyName;
    }

    /** @return chosen evaluation or null */
    public RouteEvaluation getChosen() {
        return chosen;
    }

    /** @return ranked evaluations */
    public List<RouteEvaluation> getRanking() {
        return ranking;
    }

    /** @return exclusions */
    public List<ExcludedRoute> getExcluded() {
        return excluded;
    }

    /** @return score breakdowns by route id */
    public Map<Long, ScoreBreakdown> getScores() {
        return scores;
    }

    /** @return explanation */
    public String getReason() {
        return reason;
    }
}

package com.logiopt.model;

/**
 * Balanced score details for one route: normalized values, weighted contributions and total.
 */
public final class ScoreBreakdown {

    private final long routeId;
    private final double normalizedCost;
    private final double normalizedTime;
    private final double normalizedEmissions;
    private final double normalizedReliability;
    private final double weightedCost;
    private final double weightedTime;
    private final double weightedEmissions;
    private final double weightedReliability;
    private final double totalScore;

    /**
     * Creates a score breakdown.
     *
     * @param routeId               route id
     * @param normalizedCost        normalized cost 0..1
     * @param normalizedTime        normalized duration 0..1
     * @param normalizedEmissions   normalized emissions 0..1
     * @param normalizedReliability normalized reliability 0..1 (0 = most reliable)
     * @param weightedCost          cost contribution
     * @param weightedTime          time contribution
     * @param weightedEmissions     emissions contribution
     * @param weightedReliability   reliability contribution
     * @param totalScore            total score (6 dp, lower is better)
     */
    public ScoreBreakdown(long routeId, double normalizedCost, double normalizedTime, double normalizedEmissions,
                          double normalizedReliability, double weightedCost, double weightedTime,
                          double weightedEmissions, double weightedReliability, double totalScore) {
        this.routeId = routeId;
        this.normalizedCost = normalizedCost;
        this.normalizedTime = normalizedTime;
        this.normalizedEmissions = normalizedEmissions;
        this.normalizedReliability = normalizedReliability;
        this.weightedCost = weightedCost;
        this.weightedTime = weightedTime;
        this.weightedEmissions = weightedEmissions;
        this.weightedReliability = weightedReliability;
        this.totalScore = totalScore;
    }

    /** @return route id */
    public long getRouteId() {
        return routeId;
    }

    /** @return normalized cost */
    public double getNormalizedCost() {
        return normalizedCost;
    }

    /** @return normalized time */
    public double getNormalizedTime() {
        return normalizedTime;
    }

    /** @return normalized emissions */
    public double getNormalizedEmissions() {
        return normalizedEmissions;
    }

    /** @return normalized reliability */
    public double getNormalizedReliability() {
        return normalizedReliability;
    }

    /** @return weighted cost contribution */
    public double getWeightedCost() {
        return weightedCost;
    }

    /** @return weighted time contribution */
    public double getWeightedTime() {
        return weightedTime;
    }

    /** @return weighted emissions contribution */
    public double getWeightedEmissions() {
        return weightedEmissions;
    }

    /** @return weighted reliability contribution */
    public double getWeightedReliability() {
        return weightedReliability;
    }

    /** @return total score */
    public double getTotalScore() {
        return totalScore;
    }
}

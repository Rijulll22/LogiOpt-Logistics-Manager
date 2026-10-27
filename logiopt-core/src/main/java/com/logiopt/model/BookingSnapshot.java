package com.logiopt.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Immutable copy, taken at booking time, of what the planning engine computed for the chosen route:
 * shipment inputs, route identity and legs, cost breakdown, duration, arrival, emissions,
 * reliability, strategy, reason and notes. Values are copied from the server-side RouteEvaluation;
 * nothing is recalculated here, and later route changes never alter it.
 */
public final class BookingSnapshot {

    private final String source;
    private final String destination;
    private final CargoType cargoType;
    private final double weightKg;
    private final LocalDate dispatchDate;
    private final long routeId;
    private final String routeName;
    private final TransportMode routeMode;
    private final List<RouteLeg> legs;
    private final CostBreakdown cost;
    private final Double durationHours;
    private final LocalDateTime estimatedArrival;
    private final Double emissionsKg;
    private final Double reliability;
    private final Double balancedScore;
    private final String strategyKey;
    private final String strategyName;
    private final String reason;
    private final String notes;

    /**
     * Creates a snapshot (all values copied by the caller from one planning run).
     *
     * @param source           shipment source
     * @param destination      shipment destination
     * @param cargoType        cargo type
     * @param weightKg         weight in kg
     * @param dispatchDate     dispatch date
     * @param routeId          route id
     * @param routeName        route name
     * @param routeMode        route mode
     * @param legs             legs in order
     * @param cost             cost breakdown (2 dp)
     * @param durationHours    duration, or null if not available
     * @param estimatedArrival arrival, or null
     * @param emissionsKg      emissions, or null
     * @param reliability      reliability, or null
     * @param balancedScore    Balanced score of this route, or null
     * @param strategyKey      strategy key
     * @param strategyName     strategy name
     * @param reason           why this route (recommendation reason, or that the user chose another route)
     * @param notes            evaluation notes joined by spaces
     */
    public BookingSnapshot(String source, String destination, CargoType cargoType, double weightKg,
                           LocalDate dispatchDate, long routeId, String routeName, TransportMode routeMode,
                           List<RouteLeg> legs, CostBreakdown cost, Double durationHours,
                           LocalDateTime estimatedArrival, Double emissionsKg, Double reliability,
                           Double balancedScore, String strategyKey, String strategyName, String reason,
                           String notes) {
        if (source == null || destination == null || cargoType == null || dispatchDate == null || routeName == null
                || routeMode == null || legs == null || legs.isEmpty() || cost == null || strategyKey == null
                || strategyName == null || reason == null) {
            throw new IllegalArgumentException("Snapshot values must not be null and need at least one leg");
        }
        this.source = source;
        this.destination = destination;
        this.cargoType = cargoType;
        this.weightKg = weightKg;
        this.dispatchDate = dispatchDate;
        this.routeId = routeId;
        this.routeName = routeName;
        this.routeMode = routeMode;
        this.legs = List.copyOf(legs);
        this.cost = cost;
        this.durationHours = durationHours;
        this.estimatedArrival = estimatedArrival;
        this.emissionsKg = emissionsKg;
        this.reliability = reliability;
        this.balancedScore = balancedScore;
        this.strategyKey = strategyKey;
        this.strategyName = strategyName;
        this.reason = reason;
        this.notes = notes == null ? "" : notes;
    }

    /** @return shipment source */
    public String getSource() {
        return source;
    }

    /** @return shipment destination */
    public String getDestination() {
        return destination;
    }

    /** @return cargo type */
    public CargoType getCargoType() {
        return cargoType;
    }

    /** @return weight in kg */
    public double getWeightKg() {
        return weightKg;
    }

    /** @return dispatch date */
    public LocalDate getDispatchDate() {
        return dispatchDate;
    }

    /** @return route id */
    public long getRouteId() {
        return routeId;
    }

    /** @return route name */
    public String getRouteName() {
        return routeName;
    }

    /** @return route mode */
    public TransportMode getRouteMode() {
        return routeMode;
    }

    /** @return legs in order (unmodifiable) */
    public List<RouteLeg> getLegs() {
        return legs;
    }

    /** @return cost breakdown */
    public CostBreakdown getCost() {
        return cost;
    }

    /** @return total cost, the amount to pay */
    public BigDecimal getTotalCost() {
        return cost.getTotal();
    }

    /** @return duration in hours, or null */
    public Double getDurationHours() {
        return durationHours;
    }

    /** @return estimated arrival, or null */
    public LocalDateTime getEstimatedArrival() {
        return estimatedArrival;
    }

    /** @return emissions in kg CO2e, or null */
    public Double getEmissionsKg() {
        return emissionsKg;
    }

    /** @return reliability, or null */
    public Double getReliability() {
        return reliability;
    }

    /** @return Balanced score, or null */
    public Double getBalancedScore() {
        return balancedScore;
    }

    /** @return strategy key */
    public String getStrategyKey() {
        return strategyKey;
    }

    /** @return strategy name */
    public String getStrategyName() {
        return strategyName;
    }

    /** @return reason */
    public String getReason() {
        return reason;
    }

    /** @return evaluation notes ("" if none) */
    public String getNotes() {
        return notes;
    }
}

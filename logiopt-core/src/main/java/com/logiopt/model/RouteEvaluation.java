package com.logiopt.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Immutable result of evaluating one route for one shipment. Missing metrics are null.
 * Identity matters: the comparison, all strategies and the report share the same instances.
 */
public final class RouteEvaluation {

    private final Route route;
    private final CostBreakdown costBreakdown;
    private final Double durationHours;
    private final LocalDateTime estimatedArrival;
    private final Double emissionsKg;
    private final List<String> notes;

    /**
     * Creates an evaluation.
     *
     * @param route            evaluated route (not null)
     * @param costBreakdown    cost breakdown (not null)
     * @param durationHours    duration in hours, or null if unavailable
     * @param estimatedArrival arrival time, allowed only when duration is present
     * @param emissionsKg      emissions in kg CO2e, or null if unavailable
     * @param notes            notes explaining unavailable metrics (not null)
     */
    public RouteEvaluation(Route route, CostBreakdown costBreakdown, Double durationHours,
                           LocalDateTime estimatedArrival, Double emissionsKg, List<String> notes) {
        if (route == null || costBreakdown == null || notes == null) {
            throw new IllegalArgumentException("Route, cost breakdown and notes must not be null");
        }
        if (durationHours == null && estimatedArrival != null) {
            throw new IllegalArgumentException("Estimated arrival requires a duration");
        }
        this.route = route;
        this.costBreakdown = costBreakdown;
        this.durationHours = durationHours;
        this.estimatedArrival = estimatedArrival;
        this.emissionsKg = emissionsKg;
        this.notes = List.copyOf(notes);
    }

    /** @return evaluated route */
    public Route getRoute() {
        return route;
    }

    /** @return cost breakdown */
    public CostBreakdown getCostBreakdown() {
        return costBreakdown;
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

    /** @return notes explaining unavailable metrics */
    public List<String> getNotes() {
        return notes;
    }

    /** @return route id */
    public long getRouteId() {
        return route.getRouteId();
    }

    /** @return route name */
    public String getRouteName() {
        return route.getName();
    }

    /** @return route mode */
    public TransportMode getMode() {
        return route.getMode();
    }

    /** @return total cost in INR */
    public BigDecimal getTotalCost() {
        return costBreakdown.getTotal();
    }

    /** @return reliability from the route, or null */
    public Double getReliability() {
        return route.getReliability();
    }

    /** @return true if duration is available */
    public boolean isDurationAvailable() {
        return durationHours != null;
    }

    /** @return true if emissions are available */
    public boolean isEmissionsAvailable() {
        return emissionsKg != null;
    }

    /** @return true if reliability is available */
    public boolean isReliabilityAvailable() {
        return route.getReliability() != null;
    }
}

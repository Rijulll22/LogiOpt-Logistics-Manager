package com.logiopt.model;

import com.logiopt.exception.RouteConfigurationException;
import com.logiopt.util.Strings;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Abstract immutable route: data and structure only, no cost/time/emission formulas.
 */
public abstract class Route implements Serializable {

    private static final long serialVersionUID = 1L;

    private final long routeId;
    private final String name;
    private final String source;
    private final String destination;
    private final List<RouteLeg> legs;
    private final BigDecimal handlingCharge;
    private final BigDecimal transferChargePerTransfer;
    private final double transferHoursPerTransfer;
    private final double waitingHours;
    private final BigDecimal waitingRatePerHour;
    private final Double reliability;
    private final double capacityKg;
    private final Set<CargoType> unsupportedCargo;

    /**
     * Validates all parameters and creates the route.
     *
     * @param params route parameters
     * @throws RouteConfigurationException if any parameter is invalid
     */
    protected Route(RouteParams params) {
        if (params == null) {
            throw new RouteConfigurationException("Route parameters must not be null");
        }
        if (params.getRouteId() <= 0) {
            throw new RouteConfigurationException("Route id must be > 0, was " + params.getRouteId());
        }
        requireText(params.getName(), "name");
        requireText(params.getSource(), "source");
        requireText(params.getDestination(), "destination");
        List<RouteLeg> legList = params.getLegs();
        if (legList == null || legList.isEmpty()) {
            throw new RouteConfigurationException("Route must have at least one leg");
        }
        if (legList.contains(null)) {
            throw new RouteConfigurationException("Route legs must not contain null");
        }
        requireNonNegative(params.getHandlingCharge(), "handling charge");
        requireNonNegative(params.getTransferChargePerTransfer(), "transfer charge per transfer");
        requireNonNegative(params.getWaitingRatePerHour(), "waiting rate per hour");
        requireFiniteNonNegative(params.getTransferHoursPerTransfer(), "transfer hours per transfer");
        requireFiniteNonNegative(params.getWaitingHours(), "waiting hours");
        if (!Double.isFinite(params.getCapacityKg()) || params.getCapacityKg() <= 0) {
            throw new RouteConfigurationException("Capacity must be finite and > 0, was " + params.getCapacityKg());
        }
        Double rel = params.getReliability();
        if (rel != null && (!Double.isFinite(rel) || rel < 0 || rel > 1)) {
            throw new RouteConfigurationException("Reliability must be null or within [0,1], was " + rel);
        }
        Set<CargoType> cargo = params.getUnsupportedCargo();
        if (cargo == null || cargo.contains(null)) {
            throw new RouteConfigurationException("Unsupported cargo must be non-null without null elements");
        }
        EnumSet<CargoType> cargoCopy = EnumSet.noneOf(CargoType.class);
        cargoCopy.addAll(cargo);

        this.routeId = params.getRouteId();
        this.name = params.getName();
        this.source = params.getSource();
        this.destination = params.getDestination();
        this.legs = List.copyOf(legList);
        this.handlingCharge = params.getHandlingCharge();
        this.transferChargePerTransfer = params.getTransferChargePerTransfer();
        this.transferHoursPerTransfer = params.getTransferHoursPerTransfer();
        this.waitingHours = params.getWaitingHours();
        this.waitingRatePerHour = params.getWaitingRatePerHour();
        this.reliability = rel;
        this.capacityKg = params.getCapacityKg();
        this.unsupportedCargo = Collections.unmodifiableSet(cargoCopy);
    }

    private static void requireText(String value, String field) {
        if (Strings.isBlank(value)) {
            throw new RouteConfigurationException("Route " + field + " must not be blank");
        }
    }

    private static void requireNonNegative(BigDecimal value, String field) {
        if (value == null || value.signum() < 0) {
            throw new RouteConfigurationException("Route " + field + " must be non-null and >= 0");
        }
    }

    private static void requireFiniteNonNegative(double value, String field) {
        if (!Double.isFinite(value) || value < 0) {
            throw new RouteConfigurationException("Route " + field + " must be finite and >= 0, was " + value);
        }
    }

    /**
     * Returns the overall mode of this route.
     *
     * @return ROAD, RAIL or MULTIMODAL
     */
    public abstract TransportMode getMode();

    /**
     * Returns the number of transfers between legs.
     *
     * @return legs.size() - 1
     */
    public int getTransferCount() {
        return legs.size() - 1;
    }

    /**
     * Returns the plain sum of all leg distances.
     *
     * @return total distance in km
     */
    public double getTotalDistanceKm() {
        double total = 0;
        for (RouteLeg leg : legs) {
            total += leg.getDistanceKm();
        }
        return total;
    }

    /** @return route id */
    public long getRouteId() {
        return routeId;
    }

    /** @return route name */
    public String getName() {
        return name;
    }

    /** @return source city */
    public String getSource() {
        return source;
    }

    /** @return destination city */
    public String getDestination() {
        return destination;
    }

    /** @return unmodifiable list of legs */
    public List<RouteLeg> getLegs() {
        return legs;
    }

    /** @return handling charge in INR (once per shipment) */
    public BigDecimal getHandlingCharge() {
        return handlingCharge;
    }

    /** @return charge in INR per transfer */
    public BigDecimal getTransferChargePerTransfer() {
        return transferChargePerTransfer;
    }

    /** @return hours per transfer */
    public double getTransferHoursPerTransfer() {
        return transferHoursPerTransfer;
    }

    /** @return waiting hours */
    public double getWaitingHours() {
        return waitingHours;
    }

    /** @return waiting rate in INR per hour */
    public BigDecimal getWaitingRatePerHour() {
        return waitingRatePerHour;
    }

    /** @return reliability 0..1, or null if unknown */
    public Double getReliability() {
        return reliability;
    }

    /** @return capacity in kg */
    public double getCapacityKg() {
        return capacityKg;
    }

    /** @return unmodifiable set of unsupported cargo types */
    public Set<CargoType> getUnsupportedCargo() {
        return unsupportedCargo;
    }
}

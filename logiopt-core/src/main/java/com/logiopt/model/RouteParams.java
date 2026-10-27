package com.logiopt.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Fluent, mutable parameter holder used to build a {@link Route}. Route validates the values.
 * Charges and hours default to zero and unsupported cargo defaults to empty.
 */
public final class RouteParams {

    private long routeId;
    private String name;
    private String source;
    private String destination;
    private List<RouteLeg> legs;
    private BigDecimal handlingCharge = BigDecimal.ZERO;
    private BigDecimal transferChargePerTransfer = BigDecimal.ZERO;
    private double transferHoursPerTransfer;
    private double waitingHours;
    private BigDecimal waitingRatePerHour = BigDecimal.ZERO;
    private Double reliability;
    private double capacityKg;
    private Set<CargoType> unsupportedCargo = new HashSet<>();

    /** @param value route id @return this */
    public RouteParams routeId(long value) {
        this.routeId = value;
        return this;
    }

    /** @param value route name @return this */
    public RouteParams name(String value) {
        this.name = value;
        return this;
    }

    /** @param value source city @return this */
    public RouteParams source(String value) {
        this.source = value;
        return this;
    }

    /** @param value destination city @return this */
    public RouteParams destination(String value) {
        this.destination = value;
        return this;
    }

    /** @param value legs (copied if not null) @return this */
    public RouteParams legs(List<RouteLeg> value) {
        this.legs = value == null ? null : new ArrayList<>(value);
        return this;
    }

    /** @param value handling charge in INR, once per shipment @return this */
    public RouteParams handlingCharge(BigDecimal value) {
        this.handlingCharge = value;
        return this;
    }

    /** @param value charge in INR per transfer @return this */
    public RouteParams transferChargePerTransfer(BigDecimal value) {
        this.transferChargePerTransfer = value;
        return this;
    }

    /** @param value hours per transfer @return this */
    public RouteParams transferHoursPerTransfer(double value) {
        this.transferHoursPerTransfer = value;
        return this;
    }

    /** @param value waiting hours @return this */
    public RouteParams waitingHours(double value) {
        this.waitingHours = value;
        return this;
    }

    /** @param value waiting rate in INR per hour @return this */
    public RouteParams waitingRatePerHour(BigDecimal value) {
        this.waitingRatePerHour = value;
        return this;
    }

    /** @param value reliability 0..1 or null @return this */
    public RouteParams reliability(Double value) {
        this.reliability = value;
        return this;
    }

    /** @param value capacity in kg @return this */
    public RouteParams capacityKg(double value) {
        this.capacityKg = value;
        return this;
    }

    /** @param value unsupported cargo types (copied if not null) @return this */
    public RouteParams unsupportedCargo(Collection<CargoType> value) {
        this.unsupportedCargo = value == null ? null : new HashSet<>(value);
        return this;
    }

    long getRouteId() {
        return routeId;
    }

    String getName() {
        return name;
    }

    String getSource() {
        return source;
    }

    String getDestination() {
        return destination;
    }

    List<RouteLeg> getLegs() {
        return legs;
    }

    BigDecimal getHandlingCharge() {
        return handlingCharge;
    }

    BigDecimal getTransferChargePerTransfer() {
        return transferChargePerTransfer;
    }

    double getTransferHoursPerTransfer() {
        return transferHoursPerTransfer;
    }

    double getWaitingHours() {
        return waitingHours;
    }

    BigDecimal getWaitingRatePerHour() {
        return waitingRatePerHour;
    }

    Double getReliability() {
        return reliability;
    }

    double getCapacityKg() {
        return capacityKg;
    }

    Set<CargoType> getUnsupportedCargo() {
        return unsupportedCargo;
    }
}

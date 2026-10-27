package com.logiopt.model;

import com.logiopt.exception.RouteConfigurationException;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Immutable single-mode leg of a route (ROAD or RAIL). Holds data only.
 */
public final class RouteLeg implements Serializable {

    private static final long serialVersionUID = 1L;

    private final TransportMode mode;
    private final double distanceKm;
    private final BigDecimal ratePerTonneKm;
    private final Double speedKmh;
    private final double bufferHours;
    private final Double emissionFactorKgPerTonneKm;

    /**
     * Creates and validates a leg.
     *
     * @param mode                       ROAD or RAIL
     * @param distanceKm                 finite distance &gt; 0
     * @param ratePerTonneKm             INR per tonne-km, non-null &gt;= 0
     * @param speedKmh                   km/h, nullable, finite &gt; 0 if present
     * @param bufferHours                finite &gt;= 0
     * @param emissionFactorKgPerTonneKm kg CO2e per tonne-km, nullable, finite &gt;= 0 if present
     * @throws RouteConfigurationException if any value is invalid
     */
    public RouteLeg(TransportMode mode, double distanceKm, BigDecimal ratePerTonneKm, Double speedKmh,
                    double bufferHours, Double emissionFactorKgPerTonneKm) {
        if (mode != TransportMode.ROAD && mode != TransportMode.RAIL) {
            throw new RouteConfigurationException("Leg mode must be ROAD or RAIL, was " + mode);
        }
        if (!Double.isFinite(distanceKm) || distanceKm <= 0) {
            throw new RouteConfigurationException("Leg distance must be finite and > 0, was " + distanceKm);
        }
        if (ratePerTonneKm == null || ratePerTonneKm.signum() < 0) {
            throw new RouteConfigurationException("Leg rate must be non-null and >= 0");
        }
        if (speedKmh != null && (!Double.isFinite(speedKmh) || speedKmh <= 0)) {
            throw new RouteConfigurationException("Leg speed must be finite and > 0 if present, was " + speedKmh);
        }
        if (!Double.isFinite(bufferHours) || bufferHours < 0) {
            throw new RouteConfigurationException("Leg buffer hours must be finite and >= 0, was " + bufferHours);
        }
        if (emissionFactorKgPerTonneKm != null
                && (!Double.isFinite(emissionFactorKgPerTonneKm) || emissionFactorKgPerTonneKm < 0)) {
            throw new RouteConfigurationException(
                    "Leg emission factor must be finite and >= 0 if present, was " + emissionFactorKgPerTonneKm);
        }
        this.mode = mode;
        this.distanceKm = distanceKm;
        this.ratePerTonneKm = ratePerTonneKm;
        this.speedKmh = speedKmh;
        this.bufferHours = bufferHours;
        this.emissionFactorKgPerTonneKm = emissionFactorKgPerTonneKm;
    }

    /** @return leg mode (ROAD or RAIL) */
    public TransportMode getMode() {
        return mode;
    }

    /** @return distance in km */
    public double getDistanceKm() {
        return distanceKm;
    }

    /** @return rate in INR per tonne-km */
    public BigDecimal getRatePerTonneKm() {
        return ratePerTonneKm;
    }

    /** @return speed in km/h, or null if not configured */
    public Double getSpeedKmh() {
        return speedKmh;
    }

    /** @return buffer hours */
    public double getBufferHours() {
        return bufferHours;
    }

    /** @return emission factor in kg CO2e per tonne-km, or null if not configured */
    public Double getEmissionFactorKgPerTonneKm() {
        return emissionFactorKgPerTonneKm;
    }
}

package com.logiopt.model;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * Immutable shipment request. The id is 0 until the shipment is saved.
 */
public final class Shipment implements Serializable {

    private static final long serialVersionUID = 1L;

    private final long shipmentId;
    private final long userId;
    private final String source;
    private final String destination;
    private final CargoType cargoType;
    private final double weightKg;
    private final String dimensions;
    private final LocalDate dispatchDate;
    private final ShipmentStatus status;
    /** Null when not chosen; shipments serialized before this field existed read it as null too. */
    private final OptimisationPreference preference;

    /**
     * Creates a shipment. Business validation is done by ShipmentValidator.
     *
     * @param shipmentId   id (0 if not saved)
     * @param userId       owning user id
     * @param source       source city
     * @param destination  destination city
     * @param cargoType    cargo type
     * @param weightKg     weight in kg
     * @param dimensions   free-text dimensions
     * @param dispatchDate planned dispatch date
     * @param status       lifecycle status
     */
    public Shipment(long shipmentId, long userId, String source, String destination, CargoType cargoType,
                    double weightKg, String dimensions, LocalDate dispatchDate, ShipmentStatus status) {
        this(shipmentId, userId, source, destination, cargoType, weightKg, dimensions, dispatchDate, status, null);
    }

    /**
     * Creates a shipment with an optimisation preference.
     *
     * @param shipmentId   id (0 if not saved)
     * @param userId       owning user id
     * @param source       source city
     * @param destination  destination city
     * @param cargoType    cargo type
     * @param weightKg     weight in kg
     * @param dimensions   free-text dimensions
     * @param dispatchDate planned dispatch date
     * @param status       lifecycle status
     * @param preference   optimisation preference, or null when not chosen (treated as BALANCED)
     */
    public Shipment(long shipmentId, long userId, String source, String destination, CargoType cargoType,
                    double weightKg, String dimensions, LocalDate dispatchDate, ShipmentStatus status,
                    OptimisationPreference preference) {
        this.shipmentId = shipmentId;
        this.userId = userId;
        this.source = source;
        this.destination = destination;
        this.cargoType = cargoType;
        this.weightKg = weightKg;
        this.dimensions = dimensions;
        this.dispatchDate = dispatchDate;
        this.status = status;
        this.preference = preference;
    }

    /**
     * Returns a copy of this shipment with the given id.
     *
     * @param id new shipment id
     * @return new shipment instance
     */
    public Shipment withShipmentId(long id) {
        return new Shipment(id, userId, source, destination, cargoType, weightKg, dimensions, dispatchDate, status,
                preference);
    }

    /** @return the optimisation preference; BALANCED when none was chosen */
    public OptimisationPreference getPreference() {
        return preference == null ? OptimisationPreference.BALANCED : preference;
    }

    /** @return true if a preference was chosen and stored (false for older and web-made shipments) */
    public boolean hasPreference() {
        return preference != null;
    }

    /** @return shipment id (0 until saved) */
    public long getShipmentId() {
        return shipmentId;
    }

    /** @return owning user id */
    public long getUserId() {
        return userId;
    }

    /** @return source city */
    public String getSource() {
        return source;
    }

    /** @return destination city */
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

    /** @return free-text dimensions */
    public String getDimensions() {
        return dimensions;
    }

    /** @return dispatch date */
    public LocalDate getDispatchDate() {
        return dispatchDate;
    }

    /** @return lifecycle status */
    public ShipmentStatus getStatus() {
        return status;
    }
}

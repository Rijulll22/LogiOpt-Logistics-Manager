package com.logiopt.service;

import com.logiopt.exception.InvalidShipmentException;
import com.logiopt.model.Shipment;
import com.logiopt.util.Numbers;
import com.logiopt.util.Strings;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates shipments, collecting all errors before throwing. New shipments get every rule
 * ({@link #validate(Shipment)}); saved shipments that are re-compared or reported skip the
 * dispatch-date rule ({@link #validateForPlanning(Shipment)}), so a shipment whose date has passed
 * stays viewable.
 */
public class ShipmentValidator {

    /** Smallest weight shipments.weight_kg DECIMAL(12,3) can hold above zero. */
    public static final BigDecimal MIN_WEIGHT_KG = new BigDecimal("0.001");
    /** Largest weight shipments.weight_kg DECIMAL(12,3) can hold. */
    public static final BigDecimal MAX_WEIGHT_KG = new BigDecimal("999999999.999");

    static final String WEIGHT_POSITIVE = "Weight must be a positive number of kg.";
    static final String WEIGHT_TOO_SMALL = "Weight must be at least 0.001 kg.";
    static final String WEIGHT_TOO_LARGE = "Weight must be at most 999,999,999.999 kg.";
    static final String WEIGHT_SCALE = "Weight can have at most 3 decimal places.";

    private final Clock clock;

    /**
     * Creates a validator.
     *
     * @param clock clock used to determine "today"
     */
    public ShipmentValidator(Clock clock) {
        if (clock == null) {
            throw new IllegalArgumentException("Clock must not be null");
        }
        this.clock = clock;
    }

    /**
     * Checks a weight exactly as entered against the DECIMAL(12,3) column: greater than zero, between
     * 0.001 and 999,999,999.999 kg and at most 3 decimal places. Nothing is rounded.
     *
     * @param weightKg exact weight (not null)
     * @return the error message, or null if the weight is valid
     */
    public static String checkWeight(BigDecimal weightKg) {
        if (weightKg.signum() <= 0) {
            return WEIGHT_POSITIVE;
        }
        if (weightKg.compareTo(MIN_WEIGHT_KG) < 0) {
            return WEIGHT_TOO_SMALL;
        }
        if (weightKg.compareTo(MAX_WEIGHT_KG) > 0) {
            return WEIGHT_TOO_LARGE;
        }
        if (weightKg.stripTrailingZeros().scale() > Numbers.WEIGHT_SCALE) {
            return WEIGHT_SCALE;
        }
        return null;
    }

    /**
     * Validates a new shipment and reports every problem together. City names are not checked
     * against the configured routes; use {@link #validate(Shipment, Collection)} for that.
     *
     * @param shipment shipment to validate
     * @throws InvalidShipmentException if any rule fails
     */
    public void validate(Shipment shipment) throws InvalidShipmentException {
        List<String> errors = commonErrors(shipment);
        checkDispatchNotPast(shipment, errors);
        throwIfAny(errors);
    }

    /**
     * Validates a new shipment and also rejects source or destination cities that no configured
     * route serves (compared after trimming, collapsing whitespace and ignoring case).
     *
     * @param shipment    shipment to validate
     * @param knownCities cities served by at least one route
     * @throws InvalidShipmentException if any rule fails
     */
    public void validate(Shipment shipment, Collection<String> knownCities) throws InvalidShipmentException {
        List<String> errors = commonErrors(shipment);
        checkDispatchNotPast(shipment, errors);
        Set<String> known = new HashSet<>();
        for (String city : knownCities) {
            known.add(Strings.normalize(city));
        }
        if (!Strings.isBlank(shipment.getSource()) && !known.contains(Strings.normalize(shipment.getSource()))) {
            errors.add("Unknown source city '" + shipment.getSource().trim() + "'. Choose a city from the list.");
        }
        if (!Strings.isBlank(shipment.getDestination())
                && !known.contains(Strings.normalize(shipment.getDestination()))) {
            errors.add("Unknown destination city '" + shipment.getDestination().trim()
                    + "'. Choose a city from the list.");
        }
        throwIfAny(errors);
    }

    /**
     * Validates a saved shipment before it is compared or reported. Same rules as a new shipment
     * except the dispatch date may be in the past.
     *
     * @param shipment shipment to validate
     * @throws InvalidShipmentException if any rule fails
     */
    public void validateForPlanning(Shipment shipment) throws InvalidShipmentException {
        throwIfAny(commonErrors(shipment));
    }

    private static List<String> commonErrors(Shipment shipment) throws InvalidShipmentException {
        List<String> errors = new ArrayList<>();
        if (shipment == null) {
            errors.add("Shipment is required.");
            throw new InvalidShipmentException(errors);
        }
        boolean sourceBlank = Strings.isBlank(shipment.getSource());
        boolean destinationBlank = Strings.isBlank(shipment.getDestination());
        if (sourceBlank) {
            errors.add("Source is required.");
        }
        if (destinationBlank) {
            errors.add("Destination is required.");
        }
        if (!sourceBlank && !destinationBlank
                && Strings.normalize(shipment.getSource()).equals(Strings.normalize(shipment.getDestination()))) {
            errors.add("Source and destination must be different.");
        }
        if (shipment.getCargoType() == null) {
            errors.add("Cargo type is required.");
        }
        double weight = shipment.getWeightKg();
        if (!Double.isFinite(weight)) {
            errors.add(WEIGHT_POSITIVE);
        } else {
            String weightError = checkWeight(BigDecimal.valueOf(weight));
            if (weightError != null) {
                errors.add(weightError);
            }
        }
        if (shipment.getDispatchDate() == null) {
            errors.add("Dispatch date is required.");
        }
        return errors;
    }

    private void checkDispatchNotPast(Shipment shipment, List<String> errors) {
        LocalDate dispatch = shipment.getDispatchDate();
        if (dispatch != null && dispatch.isBefore(LocalDate.now(clock))) {
            errors.add("Dispatch date cannot be in the past.");
        }
    }

    private static void throwIfAny(List<String> errors) throws InvalidShipmentException {
        if (!errors.isEmpty()) {
            throw new InvalidShipmentException(errors);
        }
    }
}

package com.logiopt.app;

import com.logiopt.model.CargoType;
import com.logiopt.model.OptimisationPreference;
import com.logiopt.model.Shipment;
import com.logiopt.model.ShipmentStatus;
import com.logiopt.service.ShipmentValidator;
import com.logiopt.util.Strings;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The shipment form's fields as entered, parsed the same way by every front end (web form, desktop
 * form) for creating and for editing a draft. Keeps the raw values for redisplay and the errors found
 * while parsing; business validation is the ShipmentValidator's job. {@link #fieldOf(String)} tells
 * which field a parse or validation message belongs to, for inline errors.
 */
public final class ShipmentFormInput {

    /** Field name: source city. */
    public static final String SOURCE = "source";
    /** Field name: destination city. */
    public static final String DESTINATION = "destination";
    /** Field name: cargo type (a {@link CargoType} name). */
    public static final String CARGO_TYPE = "cargoType";
    /** Field name: weight in kg as typed. */
    public static final String WEIGHT_KG = "weightKg";
    /** Field name: optional dimensions. */
    public static final String DIMENSIONS = "dimensions";
    /** Field name: dispatch date, yyyy-MM-dd. */
    public static final String DISPATCH_DATE = "dispatchDate";
    /**
     * Field name: optimisation preference (an OptimisationPreference name). Optional and not part of
     * {@link #FIELDS}: the web form does not send it, and its values map then stays as before.
     */
    public static final String PREFERENCE = "preference";
    /** All fields of the web form, in form order. */
    public static final List<String> FIELDS = List.of(SOURCE, DESTINATION, CARGO_TYPE, WEIGHT_KG, DIMENSIONS,
            DISPATCH_DATE);
    /** Key used by {@link #fieldOf(String)} for messages that belong to no single field. */
    public static final String GENERAL = "";
    /** Maximum length of the text fields. */
    public static final int MAX_TEXT_LENGTH = 100;

    static final String TEXT_TOO_LONG = "Text fields must be at most " + MAX_TEXT_LENGTH + " characters.";
    static final String UNKNOWN_CARGO = "Unknown cargo type.";
    static final String WEIGHT_NOT_NUMBER = "Weight must be a number.";
    static final String DATE_FORMAT = "Dispatch date must be in the format yyyy-MM-dd.";
    static final String UNKNOWN_PREFERENCE = "Unknown optimisation preference.";

    private final Map<String, String> values;
    private final List<String> parseErrors;
    private final Shipment shipment;

    private ShipmentFormInput(Map<String, String> values, List<String> parseErrors, Shipment shipment) {
        this.values = Collections.unmodifiableMap(values);
        this.parseErrors = List.copyOf(parseErrors);
        this.shipment = shipment;
    }

    /**
     * Parses the entered fields. The owner always comes from the signed-in session, never from input.
     *
     * @param field       raw value of a field by name (null when absent)
     * @param ownerUserId signed-in user
     * @return parsed input
     */
    public static ShipmentFormInput parse(Function<String, String> field, long ownerUserId) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String name : FIELDS) {
            String value = field.apply(name);
            values.put(name, value == null ? "" : value);
        }
        List<String> errors = new ArrayList<>();
        String source = field.apply(SOURCE);
        String destination = field.apply(DESTINATION);
        String dimensions = field.apply(DIMENSIONS);
        if (length(source) > MAX_TEXT_LENGTH || length(destination) > MAX_TEXT_LENGTH
                || length(dimensions) > MAX_TEXT_LENGTH) {
            errors.add(TEXT_TOO_LONG);
        }
        CargoType cargo = null;
        String cargoText = field.apply(CARGO_TYPE);
        if (!Strings.isBlank(cargoText)) {
            try {
                cargo = CargoType.valueOf(cargoText.trim());
            } catch (IllegalArgumentException e) {
                errors.add(UNKNOWN_CARGO);
            }
        }
        double weight = Double.NaN;
        String weightText = field.apply(WEIGHT_KG);
        if (!Strings.isBlank(weightText)) {
            try {
                BigDecimal exact = new BigDecimal(weightText.trim());
                String weightError = ShipmentValidator.checkWeight(exact);
                if (weightError != null) {
                    errors.add(weightError);
                }
                weight = exact.doubleValue();
            } catch (NumberFormatException e) {
                errors.add(WEIGHT_NOT_NUMBER);
            }
        }
        LocalDate dispatch = null;
        String dateText = field.apply(DISPATCH_DATE);
        if (!Strings.isBlank(dateText)) {
            try {
                dispatch = LocalDate.parse(dateText.trim());
            } catch (DateTimeParseException e) {
                errors.add(DATE_FORMAT);
            }
        }
        OptimisationPreference preference = null;
        String preferenceText = field.apply(PREFERENCE);
        if (preferenceText != null) {
            values.put(PREFERENCE, preferenceText);
            try {
                preference = OptimisationPreference.parseOrNull(preferenceText);
            } catch (IllegalArgumentException e) {
                errors.add(UNKNOWN_PREFERENCE);
            }
        }
        Shipment shipment = new Shipment(0, ownerUserId, source, destination, cargo, weight,
                dimensions == null ? "" : dimensions.trim(), dispatch, ShipmentStatus.DRAFT, preference);
        return new ShipmentFormInput(values, errors, shipment);
    }

    /**
     * @param s saved shipment
     * @return its values as shown in the form (weight without trailing zeros, never rounded)
     */
    public static Map<String, String> valuesOf(Shipment s) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(SOURCE, s.getSource());
        values.put(DESTINATION, s.getDestination());
        values.put(CARGO_TYPE, s.getCargoType() == null ? "" : s.getCargoType().name());
        values.put(WEIGHT_KG, BigDecimal.valueOf(s.getWeightKg()).stripTrailingZeros().toPlainString());
        values.put(DIMENSIONS, s.getDimensions() == null ? "" : s.getDimensions());
        values.put(DISPATCH_DATE, s.getDispatchDate() == null ? "" : s.getDispatchDate().toString());
        values.put(PREFERENCE, s.getPreference().name());
        return values;
    }

    /**
     * Tells which field a message from {@link #parse} or the ShipmentValidator belongs to.
     *
     * @param message error message
     * @return one of {@link #FIELDS}, {@link #PREFERENCE}, or {@link #GENERAL}
     */
    public static String fieldOf(String message) {
        if (message == null) {
            return GENERAL;
        }
        if (message.startsWith("Source and destination")) {
            return DESTINATION;
        }
        if (message.startsWith("Source") || message.startsWith("Unknown source")) {
            return SOURCE;
        }
        if (message.startsWith("Destination") || message.startsWith("Unknown destination")) {
            return DESTINATION;
        }
        if (message.startsWith("Cargo type") || message.equals(UNKNOWN_CARGO)) {
            return CARGO_TYPE;
        }
        if (message.startsWith("Weight")) {
            return WEIGHT_KG;
        }
        if (message.startsWith("Dispatch date")) {
            return DISPATCH_DATE;
        }
        if (message.equals(UNKNOWN_PREFERENCE)) {
            return PREFERENCE;
        }
        return GENERAL;
    }

    /**
     * Groups messages by {@link #fieldOf(String)}, keeping their order and dropping duplicates.
     *
     * @param messages error messages
     * @return field (or {@link #GENERAL}) to its messages, in first-seen order
     */
    public static Map<String, List<String>> byField(Collection<String> messages) {
        Map<String, List<String>> grouped = new LinkedHashMap<>();
        for (String message : messages) {
            List<String> list = grouped.computeIfAbsent(fieldOf(message), k -> new ArrayList<>());
            if (!list.contains(message)) {
                list.add(message);
            }
        }
        return grouped;
    }

    /** @return raw values for redisplay, by field name */
    public Map<String, String> getValues() {
        return values;
    }

    /** @return errors found while parsing */
    public List<String> getParseErrors() {
        return parseErrors;
    }

    /** @return the parsed shipment (id 0, status DRAFT) */
    public Shipment getShipment() {
        return shipment;
    }

    private static int length(String value) {
        return value == null ? 0 : value.length();
    }
}

package com.logiopt.app;

import com.logiopt.exception.InvalidShipmentException;
import com.logiopt.model.CargoType;
import com.logiopt.model.Shipment;
import com.logiopt.model.ShipmentStatus;
import com.logiopt.service.ShipmentValidator;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The shared shipment-form parsing and the field mapping used for inline errors; needs no database. */
class ShipmentFormInputTest {

    private static Map<String, String> valid() {
        Map<String, String> f = new HashMap<>();
        f.put("source", "Mumbai");
        f.put("destination", "Delhi");
        f.put("cargoType", "FRAGILE");
        f.put("weightKg", " 1234.500 ");
        f.put("dimensions", " 2 x 1 x 1 m ");
        f.put("dispatchDate", "2026-11-02");
        return f;
    }

    @Test
    void validInputIsParsedExactlyAndTheOwnerComesFromTheCaller() {
        ShipmentFormInput input = ShipmentFormInput.parse(valid()::get, 42);
        assertEquals(List.of(), input.getParseErrors());
        Shipment s = input.getShipment();
        assertEquals(42, s.getUserId());
        assertEquals(0, s.getShipmentId());
        assertEquals(CargoType.FRAGILE, s.getCargoType());
        assertEquals(1234.5, s.getWeightKg());
        assertEquals("2 x 1 x 1 m", s.getDimensions());
        assertEquals(LocalDate.of(2026, 11, 2), s.getDispatchDate());
        assertEquals(ShipmentStatus.DRAFT, s.getStatus());
        assertEquals(" 1234.500 ", input.getValues().get("weightKg"), "raw values are kept for redisplay");
        assertEquals(ShipmentFormInput.FIELDS, List.copyOf(input.getValues().keySet()));
    }

    @Test
    void parseErrorsMatchTheWebFormMessages() {
        Map<String, String> f = valid();
        f.put("source", "x".repeat(101));
        f.put("cargoType", "GOLD");
        f.put("weightKg", "heavy");
        f.put("dispatchDate", "02/11/2026");
        assertEquals(List.of("Text fields must be at most 100 characters.", "Unknown cargo type.",
                "Weight must be a number.", "Dispatch date must be in the format yyyy-MM-dd."),
                ShipmentFormInput.parse(f::get, 1).getParseErrors());
        f = valid();
        f.put("weightKg", "1.0005");
        assertEquals(List.of("Weight can have at most 3 decimal places."), ShipmentFormInput.parse(f::get, 1).getParseErrors());
    }

    @Test
    void missingFieldsAreEmptyAndLeftToTheValidator() {
        ShipmentFormInput input = ShipmentFormInput.parse(name -> null, 1);
        assertEquals(List.of(), input.getParseErrors());
        assertNull(input.getShipment().getCargoType());
        assertTrue(Double.isNaN(input.getShipment().getWeightKg()));
        assertEquals("", input.getValues().get("source"));
    }

    @Test
    void valuesOfShowsTheSavedWeightWithoutRounding() {
        Shipment s = new Shipment(5, 1, "Chennai", "Kolkata", CargoType.GENERAL, 999_999_999.999, null,
                LocalDate.of(2026, 12, 1), ShipmentStatus.DRAFT);
        Map<String, String> values = ShipmentFormInput.valuesOf(s);
        assertEquals("999999999.999", values.get("weightKg"));
        assertEquals("", values.get("dimensions"));
        assertEquals("2026-12-01", values.get("dispatchDate"));
    }

    @Test
    void everyValidatorAndParseMessageMapsToItsField() {
        ShipmentValidator validator = new ShipmentValidator(Clock.fixed(Instant.parse("2026-10-21T00:00:00Z"), ZoneOffset.UTC));
        Shipment bad = new Shipment(0, 1, " ", "", null, -1, "", LocalDate.of(2026, 1, 1), ShipmentStatus.DRAFT);
        InvalidShipmentException ex = assertThrows(InvalidShipmentException.class,
                () -> validator.validate(bad, List.of("Mumbai")));
        Map<String, List<String>> byField = ShipmentFormInput.byField(ex.getErrors());
        assertEquals(List.of("Source is required."), byField.get("source"));
        assertEquals(List.of("Destination is required."), byField.get("destination"));
        assertEquals(List.of("Cargo type is required."), byField.get("cargoType"));
        assertEquals(List.of("Weight must be a positive number of kg."), byField.get("weightKg"));
        assertEquals(List.of("Dispatch date cannot be in the past."), byField.get("dispatchDate"));
        assertNull(byField.get(ShipmentFormInput.GENERAL), byField.toString());

        Shipment unknown = new Shipment(0, 1, "Atlantis", "Atlantis", CargoType.GENERAL, 1, "",
                LocalDate.of(2026, 11, 2), ShipmentStatus.DRAFT);
        InvalidShipmentException ex2 = assertThrows(InvalidShipmentException.class,
                () -> validator.validate(unknown, List.of("Mumbai")));
        for (String message : ex2.getErrors()) {
            assertTrue(List.of("source", "destination").contains(ShipmentFormInput.fieldOf(message)), message);
        }
        assertEquals("destination", ShipmentFormInput.fieldOf("Source and destination must be different."));
        assertEquals("weightKg", ShipmentFormInput.fieldOf("Weight must be at least 0.001 kg."));
        assertEquals("cargoType", ShipmentFormInput.fieldOf("Unknown cargo type."));
        assertEquals("dispatchDate", ShipmentFormInput.fieldOf("Dispatch date must be in the format yyyy-MM-dd."));
        assertEquals(ShipmentFormInput.GENERAL, ShipmentFormInput.fieldOf("Text fields must be at most 100 characters."));
        assertEquals(ShipmentFormInput.GENERAL, ShipmentFormInput.fieldOf(null));
    }

    @Test
    void byFieldKeepsOrderAndDropsDuplicates() {
        Map<String, List<String>> grouped = ShipmentFormInput.byField(List.of("Weight must be a number.",
                "Source is required.", "Weight must be a number.", "Something else."));
        assertEquals(List.of("weightKg", "source", ""), List.copyOf(grouped.keySet()));
        assertEquals(List.of("Weight must be a number."), grouped.get("weightKg"));
    }
}

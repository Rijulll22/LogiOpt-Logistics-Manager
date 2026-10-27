package com.logiopt.service;

import com.logiopt.TestData;
import com.logiopt.exception.InvalidShipmentException;
import com.logiopt.model.CargoType;
import com.logiopt.model.Shipment;
import com.logiopt.model.ShipmentStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipmentValidatorTest {

    private final ShipmentValidator validator = new ShipmentValidator(TestData.CLOCK);

    private static Shipment shipment(String source, String destination, CargoType cargo, double weight,
                                     LocalDate dispatch) {
        return new Shipment(0, 1, source, destination, cargo, weight, "", dispatch, ShipmentStatus.DRAFT);
    }

    @Test
    void validShipmentAndTodayAccepted() {
        assertDoesNotThrow(() -> validator.validate(TestData.shipment()));
        assertDoesNotThrow(() -> validator.validate(
                shipment("Mumbai", "Delhi", CargoType.GENERAL, 1, LocalDate.of(2026, 10, 21))));
    }

    @Test
    void allErrorsReportedTogether() {
        InvalidShipmentException ex = assertThrows(InvalidShipmentException.class, () -> validator.validate(
                shipment(" ", "", null, -5, LocalDate.of(2026, 10, 20))));
        assertEquals(5, ex.getErrors().size(), ex.getErrors().toString());
        assertTrue(ex.getErrors().contains("Source is required."));
        assertTrue(ex.getErrors().contains("Destination is required."));
        assertTrue(ex.getErrors().contains("Cargo type is required."));
        assertTrue(ex.getErrors().contains("Weight must be a positive number of kg."));
        assertTrue(ex.getErrors().contains("Dispatch date cannot be in the past."));
    }

    @Test
    void sameNormalizedEndpointsAndNullDateRejected() {
        InvalidShipmentException ex = assertThrows(InvalidShipmentException.class, () -> validator.validate(
                shipment("Navi  Mumbai", " navi mumbai ", CargoType.GENERAL, 10, null)));
        assertEquals(2, ex.getErrors().size());
        assertTrue(ex.getErrors().contains("Source and destination must be different."));
        assertTrue(ex.getErrors().contains("Dispatch date is required."));
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, -1, Double.NaN, Double.POSITIVE_INFINITY})
    void invalidWeightRejected(double weight) {
        InvalidShipmentException ex = assertThrows(InvalidShipmentException.class, () -> validator.validate(
                shipment("Mumbai", "Delhi", CargoType.GENERAL, weight, TestData.DISPATCH)));
        assertEquals(1, ex.getErrors().size());
    }

    @Test
    void nullShipmentRejected() {
        assertThrows(InvalidShipmentException.class, () -> validator.validate(null));
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.001, 0.5, 1, 5000, 1234.567, 999_999_999.999})
    void weightWithinDecimal12x3Accepted(double weight) {
        assertDoesNotThrow(() -> validator.validate(
                shipment("Mumbai", "Delhi", CargoType.GENERAL, weight, TestData.DISPATCH)));
    }

    @ParameterizedTest
    @CsvSource({"0.0005, Weight must be at least 0.001 kg.", "0.0009, Weight must be at least 0.001 kg.",
            "1000000000, 'Weight must be at most 999,999,999.999 kg.'",
            "1e12, 'Weight must be at most 999,999,999.999 kg.'",
            "1.0001, Weight can have at most 3 decimal places.", "5000.1234, Weight can have at most 3 decimal places."})
    void weightOutsideDecimal12x3RejectedWithOneClearMessage(double weight, String message) {
        InvalidShipmentException ex = assertThrows(InvalidShipmentException.class, () -> validator.validate(
                shipment("Mumbai", "Delhi", CargoType.GENERAL, weight, TestData.DISPATCH)));
        assertEquals(List.of(message), ex.getErrors());
    }

    @ParameterizedTest
    @CsvSource(value = {"0.001|", "999999999.999|", "5.000|", "0.0010|", "1E+3|",
            "1.00000000000000001|Weight can have at most 3 decimal places.",
            "0.0001|Weight must be at least 0.001 kg.", "999999999.9991|Weight must be at most 999,999,999.999 kg.",
            "0|Weight must be a positive number of kg.", "-2|Weight must be a positive number of kg."},
            delimiter = '|')
    void exactWeightTextCheckedWithoutRounding(String text, String expected) {
        assertEquals(expected, ShipmentValidator.checkWeight(new BigDecimal(text)));
    }

    @Test
    void unknownCitiesRejectedAndKnownCitiesMatchIgnoringCaseAndSpaces() {
        List<String> cities = List.of("Mumbai", "Delhi", "New Delhi");
        assertDoesNotThrow(() -> validator.validate(
                shipment(" mumbai ", "NEW   delhi", CargoType.GENERAL, 10, TestData.DISPATCH), cities));
        InvalidShipmentException ex = assertThrows(InvalidShipmentException.class, () -> validator.validate(
                shipment("Bombay", "Bengalaru", CargoType.GENERAL, 10, TestData.DISPATCH), cities));
        assertEquals(List.of("Unknown source city 'Bombay'. Choose a city from the list.",
                "Unknown destination city 'Bengalaru'. Choose a city from the list."), ex.getErrors());
    }

    @Test
    void cityCheckReportedTogetherWithOtherErrors() {
        InvalidShipmentException ex = assertThrows(InvalidShipmentException.class, () -> validator.validate(
                shipment("Atlantis", "Delhi", CargoType.GENERAL, 0.0001, LocalDate.of(2026, 10, 1)),
                List.of("Mumbai", "Delhi")));
        assertEquals(3, ex.getErrors().size(), ex.getErrors().toString());
        assertTrue(ex.getErrors().contains("Dispatch date cannot be in the past."));
        assertTrue(ex.getErrors().contains("Weight must be at least 0.001 kg."));
    }

    @Test
    void savedShipmentWithPastDateIsValidForPlanningButNotAsNewShipment() {
        Shipment past = shipment("Mumbai", "Delhi", CargoType.GENERAL, 5000, LocalDate.of(2026, 1, 15));
        assertThrows(InvalidShipmentException.class, () -> validator.validate(past));
        assertDoesNotThrow(() -> validator.validateForPlanning(past));
    }

    @Test
    void planningValidationStillRejectsStructuralErrors() {
        InvalidShipmentException ex = assertThrows(InvalidShipmentException.class, () -> validator.validateForPlanning(
                shipment("", "Delhi", null, 0.0004, null)));
        assertEquals(List.of("Source is required.", "Cargo type is required.", "Weight must be at least 0.001 kg.",
                "Dispatch date is required."), ex.getErrors());
    }
}

package com.logiopt.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NumbersTest {

    @ParameterizedTest
    @CsvSource({"2.675, 2.68", "1.005, 1.01", "-2.675, -2.68", "38.2222222, 38.22", "47.861111, 47.86"})
    void round2UsesHalfUpOnDecimalForm(double input, double expected) {
        assertEquals(expected, Numbers.round2(input));
    }

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void nonFiniteRejected(double input) {
        assertThrows(IllegalArgumentException.class, () -> Numbers.round2(input));
        assertThrows(IllegalArgumentException.class, () -> Numbers.round6(input));
    }

    @Test
    void round6AndMoney() {
        assertEquals(0.138074, Numbers.round6(0.1380739));
        assertEquals(new BigDecimal("10.01"), Numbers.money(new BigDecimal("10.005")));
        assertThrows(IllegalArgumentException.class, () -> Numbers.money(null));
    }

    @ParameterizedTest
    @CsvSource({"217.5, 2, 217.50", "30, 2, 30.00", "38.22, 2, 38.22", "2.675, 2, 2.68", "0.9, 3, 0.900",
            "5000, 3, 5000.000", "999999999.999, 3, 999999999.999", "0.001, 3, 0.001"})
    void formatPadsAndRoundsHalfUp(double value, int scale, String expected) {
        assertEquals(expected, Numbers.format(value, scale));
    }

    @Test
    void formatRejectsNonFinite() {
        assertThrows(IllegalArgumentException.class, () -> Numbers.format(Double.NaN, 2));
    }

    @Test
    void stringsNormalize() {
        assertEquals("new delhi", Strings.normalize("  New   DELHI "));
        assertEquals("", Strings.normalize(null));
        assertEquals(true, Strings.isBlank("  "));
    }
}

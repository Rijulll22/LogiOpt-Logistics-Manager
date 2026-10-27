package com.logiopt.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Rounding helpers and shared numeric constants.
 */
public final class Numbers {

    /** Decimal places for money values (INR). */
    public static final int MONEY_SCALE = 2;
    /** Decimal places for duration and emissions. */
    public static final int METRIC_SCALE = 2;
    /** Decimal places for Balanced scores. */
    public static final int SCORE_SCALE = 6;
    /** Decimal places for weights in kg; matches shipments.weight_kg DECIMAL(12,3). */
    public static final int WEIGHT_SCALE = 3;
    /** Decimal places for reliability; matches routes.reliability DECIMAL(4,3). */
    public static final int RELIABILITY_SCALE = 3;
    /** Kilograms per tonne. */
    public static final double KG_PER_TONNE = 1000.0;

    private Numbers() {
    }

    /**
     * Rounds a money value to 2 decimals with HALF_UP.
     *
     * @param value money amount (not null)
     * @return value with scale 2
     */
    public static BigDecimal money(BigDecimal value) {
        if (value == null) {
            throw new IllegalArgumentException("Money value must not be null");
        }
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Rounds a finite double to 2 decimals with HALF_UP using its decimal string form.
     *
     * @param value finite value
     * @return rounded value
     */
    public static double round2(double value) {
        return round(value, METRIC_SCALE);
    }

    /**
     * Rounds a finite double to 6 decimals with HALF_UP using its decimal string form.
     *
     * @param value finite value
     * @return rounded value
     */
    public static double round6(double value) {
        return round(value, SCORE_SCALE);
    }

    /**
     * Formats a finite double with a fixed number of decimals (HALF_UP on its decimal string form),
     * for example 217.5 with scale 2 gives "217.50".
     *
     * @param value finite value
     * @param scale decimal places
     * @return plain decimal text
     */
    public static String format(double value, int scale) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Value must be finite: " + value);
        }
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).toPlainString();
    }

    private static double round(double value, int scale) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Value must be finite: " + value);
        }
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }
}

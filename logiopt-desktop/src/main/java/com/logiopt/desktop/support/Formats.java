package com.logiopt.desktop.support;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Display formats shared by the desktop view models (dates stay ISO, as on the web pages). */
public final class Formats {

    /** Shown for a missing value. */
    public static final String NONE = "—";
    /** Shown for a metric the services could not compute (never shown as zero). */
    public static final String NOT_AVAILABLE = "not available";

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private Formats() {
    }

    /**
     * @param weightKg stored weight
     * @return weight as stored, without trailing zeros and never rounded, with "kg"
     */
    public static String weight(double weightKg) {
        if (Double.isNaN(weightKg) || Double.isInfinite(weightKg)) {
            return NONE;
        }
        return BigDecimal.valueOf(weightKg).stripTrailingZeros().toPlainString() + " kg";
    }

    /**
     * @param amount money amount (2 decimals)
     * @return "INR 22,800.00", or {@link #NONE}
     */
    public static String money(BigDecimal amount) {
        if (amount == null) {
            return NONE;
        }
        DecimalFormat format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.ROOT));
        return "INR " + format.format(amount.setScale(2, RoundingMode.HALF_UP));
    }

    /** @return "31.00 h", or {@link #NOT_AVAILABLE} */
    public static String hours(Double hours) {
        return hours == null ? NOT_AVAILABLE : decimals(hours, 2) + " h";
    }

    /** @return "145.00 kg CO2e", or {@link #NOT_AVAILABLE} */
    public static String emissions(Double kg) {
        return kg == null ? NOT_AVAILABLE : decimals(kg, 2) + " kg CO2e";
    }

    /** @return "0.900" (the stored scale), or {@link #NOT_AVAILABLE} */
    public static String reliability(Double reliability) {
        return reliability == null ? NOT_AVAILABLE : decimals(reliability, 3);
    }

    /** @return a Balanced score with 6 decimals ("0.167971"), or {@link #NOT_AVAILABLE} */
    public static String score(Double score) {
        return score == null ? NOT_AVAILABLE : decimals(score, 6);
    }

    /** @return "1,450 km" (whole or decimal km as stored, with thousands separators) */
    public static String km(double km) {
        DecimalFormat format = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.ROOT));
        return format.format(km) + " km";
    }

    /** @return the value with exactly the given decimals (HALF_UP), plain digits */
    private static String decimals(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).toPlainString();
    }

    /** @return ISO date, or {@link #NONE} */
    public static String date(LocalDate date) {
        return date == null ? NONE : date.toString();
    }

    /** @return "yyyy-MM-dd HH:mm", or {@link #NONE} */
    public static String dateTime(LocalDateTime time) {
        return time == null ? NONE : time.format(DATE_TIME);
    }

    /** @return "Mumbai → Delhi" */
    public static String route(String source, String destination) {
        return text(source) + " → " + text(destination);
    }

    /**
     * @param constant enum constant name such as PENDING_PAYMENT
     * @return "Pending payment"
     */
    public static String label(Enum<?> constant) {
        if (constant == null) {
            return NONE;
        }
        String words = constant.name().replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    /**
     * @param count number
     * @param one   singular noun
     * @param many  plural noun
     * @return "1 booking", "3 bookings"
     */
    public static String count(int count, String one, String many) {
        return count + " " + (count == 1 ? one : many);
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? NONE : value.trim();
    }
}

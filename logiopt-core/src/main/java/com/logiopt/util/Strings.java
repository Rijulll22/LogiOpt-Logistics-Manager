package com.logiopt.util;

import java.util.Locale;

/**
 * String helpers used for validation and case/whitespace-insensitive city matching.
 */
public final class Strings {

    private Strings() {
    }

    /**
     * Returns true when the value is null, empty or only whitespace.
     *
     * @param value text to check
     * @return true if blank
     */
    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Normalizes text for comparison: trim, collapse inner whitespace, lower-case.
     *
     * @param value text to normalize (null becomes empty)
     * @return normalized text
     */
    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}

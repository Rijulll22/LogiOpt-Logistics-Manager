package com.logiopt.model;

/**
 * What the customer cares about most for a shipment. It names one of the four strategies; it never
 * hides the others. A shipment without a stored preference (saved before V10, or by the web form)
 * is treated as {@link #BALANCED}.
 */
public enum OptimisationPreference {
    /** Lowest total cost. */
    CHEAPEST("Cheapest", "Lowest total cost"),
    /** Shortest transit time. */
    FASTEST("Fastest", "Shortest transit time"),
    /** Lowest emissions. */
    GREENEST("Greenest", "Lowest emissions"),
    /** Best overall mix of cost, time, emissions and reliability. */
    BALANCED("Balanced", "Best overall mix");

    private final String label;
    private final String description;

    OptimisationPreference(String label, String description) {
        this.label = label;
        this.description = description;
    }

    /** @return display name, the same as the strategy's */
    public String getLabel() {
        return label;
    }

    /** @return one-line description */
    public String getDescription() {
        return description;
    }

    /** @return key of the strategy this preference names (CHEAPEST, FASTEST, GREENEST or BALANCED) */
    public String getStrategyKey() {
        return name();
    }

    /**
     * @param value stored or entered value; null or blank means "not chosen"
     * @return the preference, or null when not chosen
     * @throws IllegalArgumentException if the value names no preference
     */
    public static OptimisationPreference parseOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return valueOf(value.trim());
    }
}

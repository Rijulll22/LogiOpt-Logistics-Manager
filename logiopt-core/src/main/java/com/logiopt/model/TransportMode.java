package com.logiopt.model;

/**
 * Transport mode of a route or leg.
 */
public enum TransportMode {
    /** Road transport. */
    ROAD("Road"),
    /** Rail transport. */
    RAIL("Rail"),
    /** Combination of road and rail legs. */
    MULTIMODAL("Multimodal");

    private final String label;

    TransportMode(String label) {
        this.label = label;
    }

    /**
     * Returns the human-readable label.
     *
     * @return label for display
     */
    public String getLabel() {
        return label;
    }
}

package com.logiopt.model;

import com.logiopt.exception.RouteConfigurationException;

import java.util.EnumSet;
import java.util.Set;

/**
 * Multimodal route: two or more legs using at least two different modes.
 */
public final class MultimodalRoute extends Route {

    private static final long serialVersionUID = 1L;
    private static final int MIN_LEGS = 2;
    private static final int MIN_DISTINCT_MODES = 2;

    /**
     * Creates a multimodal route.
     *
     * @param params route parameters
     * @throws RouteConfigurationException if parameters or structure are invalid
     */
    public MultimodalRoute(RouteParams params) {
        super(params);
        Set<TransportMode> modes = EnumSet.noneOf(TransportMode.class);
        for (RouteLeg leg : getLegs()) {
            modes.add(leg.getMode());
        }
        if (getLegs().size() < MIN_LEGS || modes.size() < MIN_DISTINCT_MODES) {
            throw new RouteConfigurationException(
                    "MultimodalRoute requires at least 2 legs with at least 2 different modes");
        }
    }

    @Override
    public TransportMode getMode() {
        return TransportMode.MULTIMODAL;
    }
}

package com.logiopt.model;

import com.logiopt.exception.RouteConfigurationException;

/**
 * Builds the correct Route subclass for a transport mode.
 */
public final class RouteFactory {

    private RouteFactory() {
    }

    /**
     * Creates a route of the given mode.
     *
     * @param mode   ROAD, RAIL or MULTIMODAL
     * @param params route parameters
     * @return validated route
     * @throws RouteConfigurationException if mode is null or the data is invalid
     */
    public static Route create(TransportMode mode, RouteParams params) {
        if (mode == null) {
            throw new RouteConfigurationException("Route mode must not be null");
        }
        switch (mode) {
            case ROAD:
                return new RoadRoute(params);
            case RAIL:
                return new RailRoute(params);
            case MULTIMODAL:
                return new MultimodalRoute(params);
            default:
                throw new RouteConfigurationException("Unsupported route mode " + mode);
        }
    }
}

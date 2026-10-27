package com.logiopt.model;

import com.logiopt.exception.RouteConfigurationException;

/**
 * Direct road route: exactly one ROAD leg.
 */
public final class RoadRoute extends Route {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a road route.
     *
     * @param params route parameters
     * @throws RouteConfigurationException if parameters or structure are invalid
     */
    public RoadRoute(RouteParams params) {
        super(params);
        if (getLegs().size() != 1 || getLegs().get(0).getMode() != TransportMode.ROAD) {
            throw new RouteConfigurationException("RoadRoute requires exactly one ROAD leg");
        }
    }

    @Override
    public TransportMode getMode() {
        return TransportMode.ROAD;
    }
}

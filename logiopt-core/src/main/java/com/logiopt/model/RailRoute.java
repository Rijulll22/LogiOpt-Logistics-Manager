package com.logiopt.model;

import com.logiopt.exception.RouteConfigurationException;

/**
 * Direct rail route: exactly one RAIL leg.
 */
public final class RailRoute extends Route {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a rail route.
     *
     * @param params route parameters
     * @throws RouteConfigurationException if parameters or structure are invalid
     */
    public RailRoute(RouteParams params) {
        super(params);
        if (getLegs().size() != 1 || getLegs().get(0).getMode() != TransportMode.RAIL) {
            throw new RouteConfigurationException("RailRoute requires exactly one RAIL leg");
        }
    }

    @Override
    public TransportMode getMode() {
        return TransportMode.RAIL;
    }
}

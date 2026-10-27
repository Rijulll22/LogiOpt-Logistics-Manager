package com.logiopt.service;

import com.logiopt.model.EligibilityResult;
import com.logiopt.model.RejectedRoute;
import com.logiopt.model.Route;
import com.logiopt.model.Shipment;
import com.logiopt.util.Numbers;
import com.logiopt.util.Strings;

import java.util.ArrayList;
import java.util.List;

/**
 * Filters candidate routes by endpoints, capacity and supported cargo.
 */
public class EligibilityService {

    /**
     * Splits candidates into eligible and rejected routes, each rejection with a reason.
     *
     * @param shipment   shipment to plan
     * @param candidates candidate routes
     * @return eligibility result
     */
    public EligibilityResult filter(Shipment shipment, List<Route> candidates) {
        if (shipment == null || candidates == null) {
            throw new IllegalArgumentException("Shipment and candidates must not be null");
        }
        List<Route> eligible = new ArrayList<>();
        List<RejectedRoute> rejected = new ArrayList<>();
        int routesForPair = 0;
        for (Route route : candidates) {
            if (!endpointsMatch(shipment, route)) {
                rejected.add(new RejectedRoute(route, "Route serves " + route.getSource() + " -> "
                        + route.getDestination() + ", not " + shipment.getSource() + " -> "
                        + shipment.getDestination() + "."));
                continue;
            }
            routesForPair++;
            List<String> reasons = new ArrayList<>();
            if (shipment.getWeightKg() > route.getCapacityKg()) {
                reasons.add("Weight " + Numbers.format(shipment.getWeightKg(), Numbers.WEIGHT_SCALE)
                        + " kg exceeds route capacity " + Numbers.format(route.getCapacityKg(), Numbers.MONEY_SCALE)
                        + " kg.");
            }
            if (route.getUnsupportedCargo().contains(shipment.getCargoType())) {
                reasons.add("Cargo type " + shipment.getCargoType() + " is not supported on this route.");
            }
            if (reasons.isEmpty()) {
                eligible.add(route);
            } else {
                rejected.add(new RejectedRoute(route, String.join(" ", reasons)));
            }
        }
        return new EligibilityResult(eligible, rejected, routesForPair);
    }

    private static boolean endpointsMatch(Shipment shipment, Route route) {
        return Strings.normalize(shipment.getSource()).equals(Strings.normalize(route.getSource()))
                && Strings.normalize(shipment.getDestination()).equals(Strings.normalize(route.getDestination()));
    }
}

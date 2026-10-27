package com.logiopt.service;

import com.logiopt.model.Route;
import com.logiopt.model.RouteLeg;
import com.logiopt.model.Shipment;
import com.logiopt.util.Numbers;

/**
 * Calculates route emissions in kg CO2e.
 */
public class EmissionService {

    /**
     * Sum over legs of tonnes x distance x emission factor.
     *
     * @param shipment shipment (weight in kg)
     * @param route    route
     * @return emissions rounded to 2 dp, or null if any leg has no emission factor
     */
    public Double calculateKg(Shipment shipment, Route route) {
        double tonnes = shipment.getWeightKg() / Numbers.KG_PER_TONNE;
        double total = 0;
        for (RouteLeg leg : route.getLegs()) {
            if (leg.getEmissionFactorKgPerTonneKm() == null) {
                return null;
            }
            total += tonnes * leg.getDistanceKm() * leg.getEmissionFactorKgPerTonneKm();
        }
        return Numbers.round2(total);
    }
}

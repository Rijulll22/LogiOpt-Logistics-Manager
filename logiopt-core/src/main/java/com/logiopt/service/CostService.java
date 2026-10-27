package com.logiopt.service;

import com.logiopt.model.CostBreakdown;
import com.logiopt.model.Route;
import com.logiopt.model.RouteLeg;
import com.logiopt.model.Shipment;
import com.logiopt.util.Numbers;

import java.math.BigDecimal;

/**
 * Calculates the cost breakdown of a route for a shipment (INR).
 */
public class CostService {

    private static final BigDecimal KG_PER_TONNE = BigDecimal.valueOf(Numbers.KG_PER_TONNE);

    /**
     * Calculates transport, handling, transfer and waiting costs; each is rounded to 2 dp
     * by CostBreakdown and the total is the sum of the rounded components.
     *
     * @param shipment shipment (weight in kg)
     * @param route    route
     * @return cost breakdown
     */
    public CostBreakdown calculate(Shipment shipment, Route route) {
        BigDecimal tonnes = BigDecimal.valueOf(shipment.getWeightKg()).divide(KG_PER_TONNE);
        BigDecimal transport = BigDecimal.ZERO;
        for (RouteLeg leg : route.getLegs()) {
            transport = transport.add(tonnes.multiply(BigDecimal.valueOf(leg.getDistanceKm()))
                    .multiply(leg.getRatePerTonneKm()));
        }
        BigDecimal handling = route.getHandlingCharge();
        BigDecimal transfer = BigDecimal.valueOf(route.getTransferCount())
                .multiply(route.getTransferChargePerTransfer());
        BigDecimal waiting = BigDecimal.valueOf(route.getWaitingHours()).multiply(route.getWaitingRatePerHour());
        return new CostBreakdown(transport, handling, transfer, waiting);
    }
}

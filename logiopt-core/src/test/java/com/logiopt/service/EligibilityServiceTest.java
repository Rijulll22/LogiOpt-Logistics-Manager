package com.logiopt.service;

import com.logiopt.TestData;
import com.logiopt.model.CargoType;
import com.logiopt.model.EligibilityResult;
import com.logiopt.model.RejectedRoute;
import com.logiopt.model.Route;
import com.logiopt.model.RouteFactory;
import com.logiopt.model.RouteParams;
import com.logiopt.model.Shipment;
import com.logiopt.model.ShipmentStatus;
import com.logiopt.model.TransportMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.logiopt.TestData.leg;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EligibilityServiceTest {

    private final EligibilityService service = new EligibilityService();

    private static List<Long> routeIds(List<Route> routes) {
        List<Long> ids = new ArrayList<>();
        for (Route r : routes) {
            ids.add(r.getRouteId());
        }
        return ids;
    }

    private static List<Long> rejectedIds(List<RejectedRoute> rejected) {
        List<Long> ids = new ArrayList<>();
        for (RejectedRoute r : rejected) {
            ids.add(r.getRoute().getRouteId());
        }
        return ids;
    }

    @ParameterizedTest(name = "{0} kg")
    @CsvSource({"16000, '1;2', '3'", "25000, '2', '1;3'"})
    void capacityRejections(double weight, String eligible, String rejected) {
        EligibilityResult result = service.filter(TestData.shipment(weight), TestData.dataset3());
        assertEquals(TestData.parseIds(eligible), routeIds(result.getEligible()));
        assertEquals(TestData.parseIds(rejected), rejectedIds(result.getRejected()));
        for (RejectedRoute r : result.getRejected()) {
            assertTrue(r.getReason().contains("exceeds route capacity"), r.getReason());
        }
    }

    @Test
    void capacityReasonUsesStoredScales() {
        EligibilityResult result = service.filter(TestData.shipment(16000.5), TestData.dataset3());
        assertEquals("Weight 16000.500 kg exceeds route capacity 15000.00 kg.", result.getRejected().get(0).getReason());
    }

    @Test
    void tooHeavyForAllGivesMessage() {
        EligibilityResult result = service.filter(TestData.shipment(40000), TestData.dataset3());
        assertFalse(result.hasEligibleRoutes());
        assertEquals(3, result.getRejected().size());
        assertTrue(result.getMessage().startsWith("No eligible routes"), result.getMessage());
    }

    @Test
    void unsupportedCargoRejected() {
        Route noHazmat = RouteFactory.create(TransportMode.RAIL,
                TestData.params(5, "Rail no hazmat", leg(TransportMode.RAIL, 1450, "2.80", 45.0, 6.0, 0.03))
                        .capacityKg(30000).unsupportedCargo(Set.of(CargoType.HAZARDOUS)));
        EligibilityResult result = service.filter(TestData.shipment(5000, CargoType.HAZARDOUS),
                List.of(TestData.route1(), noHazmat));
        assertEquals(List.of(1L), routeIds(result.getEligible()));
        assertTrue(result.getRejected().get(0).getReason().contains("HAZARDOUS"));
    }

    @Test
    void endpointMismatchRejectedAndNoRoutesMessage() {
        RouteParams params = new RouteParams().routeId(7).name("Chennai road").source("Chennai")
                .destination("Kolkata").legs(List.of(leg(TransportMode.ROAD, 1600, "4.50", 50.0, 2.0, 0.09)))
                .capacityKg(20000);
        Route other = RouteFactory.create(TransportMode.ROAD, params);
        EligibilityResult result = service.filter(TestData.shipment(), List.of(other));
        assertFalse(result.hasEligibleRoutes());
        assertTrue(result.getRejected().get(0).getReason().contains("Chennai"));
        assertEquals("No routes are configured for this source and destination.", result.getMessage());
    }

    @Test
    void cityMatchingIgnoresCaseAndWhitespace() {
        Shipment shipment = new Shipment(0, 1, "  mumbai ", "DELHI", CargoType.GENERAL, 5000, "", TestData.DISPATCH,
                ShipmentStatus.DRAFT);
        assertEquals(3, service.filter(shipment, TestData.dataset3()).getEligible().size());
        Route newDelhi = RouteFactory.create(TransportMode.ROAD, new RouteParams().routeId(8).name("ND")
                .source("Mumbai").destination("New Delhi")
                .legs(List.of(leg(TransportMode.ROAD, 1400, "4.50", 50.0, 2.0, 0.09))).capacityKg(20000));
        Shipment spaced = new Shipment(0, 1, "MUMBAI", " new   delhi", CargoType.GENERAL, 5000, "",
                TestData.DISPATCH, ShipmentStatus.DRAFT);
        assertEquals(1, service.filter(spaced, List.of(newDelhi)).getEligible().size());
    }
}

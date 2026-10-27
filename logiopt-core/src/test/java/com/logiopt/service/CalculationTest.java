package com.logiopt.service;

import com.logiopt.TestData;
import com.logiopt.model.CostBreakdown;
import com.logiopt.model.Route;
import com.logiopt.model.RouteEvaluation;
import com.logiopt.model.RouteFactory;
import com.logiopt.model.TransportMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static com.logiopt.TestData.leg;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalculationTest {

    private final CostService costService = new CostService();
    private final DurationService durationService = new DurationService();
    private final EmissionService emissionService = new EmissionService();

    @Test
    void totalIsSumOfRoundedComponents() {
        CostBreakdown cost = new CostBreakdown(new BigDecimal("10.005"), new BigDecimal("10.005"),
                BigDecimal.ZERO, BigDecimal.ZERO);
        assertEquals(new BigDecimal("10.01"), cost.getTransport());
        assertEquals(new BigDecimal("20.02"), cost.getTotal());
    }

    @Test
    void transportRoundedExample() {
        Route route = RouteFactory.create(TransportMode.ROAD,
                TestData.params(9, "Example", leg(TransportMode.ROAD, 1400, "4.567", 50.0, 0, 0.1))
                        .capacityKg(20000));
        assertEquals(new BigDecimal("4967.98"), costService.calculate(TestData.shipment(777), route).getTransport());
    }

    @ParameterizedTest(name = "route {0}")
    @CsvSource({
        "1, 31500.00, 1500.00, 0.00, 0.00, 33000.00, 30.00, 630.00",
        "2, 20300.00, 2500.00, 0.00, 0.00, 22800.00, 38.22, 217.50",
        "3, 22075.00, 2000.00, 1600.00, 400.00, 26075.00, 47.86, 259.50"
    })
    void dataset3Values(int index, String transport, String handling, String transfer, String waiting,
                        String total, double hours, double emissions) {
        Route route = TestData.dataset3().get(index - 1);
        RouteEvaluation e = TestData.evaluator().evaluate(TestData.shipment(), route);
        CostBreakdown cost = e.getCostBreakdown();
        assertEquals(new BigDecimal(transport), cost.getTransport());
        assertEquals(new BigDecimal(handling), cost.getHandling());
        assertEquals(new BigDecimal(transfer), cost.getTransfer());
        assertEquals(new BigDecimal(waiting), cost.getWaiting());
        assertEquals(new BigDecimal(total), e.getTotalCost());
        assertEquals(hours, e.getDurationHours(), TestData.EPS);
        assertEquals(emissions, e.getEmissionsKg(), TestData.EPS);
        assertTrue(e.getNotes().isEmpty());
    }

    @Test
    void arrivalOfRoute1() {
        RouteEvaluation e = TestData.evaluator().evaluate(TestData.shipment(), TestData.route1());
        assertEquals(LocalDateTime.of(2026, 11, 3, 6, 0), e.getEstimatedArrival());
    }

    @Test
    void noTransferChargeOnSingleLegRoutes() {
        Route road = RouteFactory.create(TransportMode.ROAD,
                TestData.params(9, "Road", leg(TransportMode.ROAD, 100, "1.00", 50.0, 0, 0.1))
                        .transferChargePerTransfer(new BigDecimal("800")).transferHoursPerTransfer(3)
                        .capacityKg(20000));
        assertEquals(new BigDecimal("0.00"), costService.calculate(TestData.shipment(), road).getTransfer());
        assertEquals(2.0, durationService.calculateHours(road), TestData.EPS);
        assertEquals(new BigDecimal("0.00"),
                costService.calculate(TestData.shipment(), TestData.route2()).getTransfer());
    }

    @Test
    void missingSpeedOrFactorGivesNullAndNotes() {
        Route route = RouteFactory.create(TransportMode.MULTIMODAL,
                TestData.params(9, "Partial", leg(TransportMode.ROAD, 100, "1.00", null, 0, 0.1),
                        leg(TransportMode.RAIL, 100, "1.00", 50.0, 0, null)).capacityKg(20000));
        assertNull(durationService.calculateHours(route));
        assertNull(emissionService.calculateKg(TestData.shipment(), route));
        assertNull(durationService.estimateArrival(TestData.DISPATCH, null));
        RouteEvaluation e = TestData.evaluator().evaluate(TestData.shipment(), route);
        assertFalse(e.isDurationAvailable());
        assertFalse(e.isEmissionsAvailable());
        assertFalse(e.isReliabilityAvailable());
        assertNull(e.getEstimatedArrival());
        assertEquals(3, e.getNotes().size());
    }

    @Test
    void evaluateAllEvaluatesEachRouteOnce() {
        List<RouteEvaluation> evaluations = TestData.evaluate(TestData.dataset3());
        assertEquals(List.of(1L, 2L, 3L), TestData.ids(evaluations));
    }
}

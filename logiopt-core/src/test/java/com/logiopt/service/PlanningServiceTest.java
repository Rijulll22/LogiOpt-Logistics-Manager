package com.logiopt.service;

import com.logiopt.TestData;
import com.logiopt.exception.InvalidShipmentException;
import com.logiopt.exception.InvalidStrategyException;
import com.logiopt.model.CargoType;
import com.logiopt.model.PlanningResult;
import com.logiopt.model.RejectedRoute;
import com.logiopt.model.Recommendation;
import com.logiopt.model.Route;
import com.logiopt.model.RouteEvaluation;
import com.logiopt.model.RouteFactory;
import com.logiopt.model.RouteParams;
import com.logiopt.model.Shipment;
import com.logiopt.model.ShipmentStatus;
import com.logiopt.model.TransportMode;
import com.logiopt.strategy.GreenestStrategy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.logiopt.TestData.leg;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanningServiceTest {

    private static final int MANY_ROUTES = 100;

    private final PlanningService planning = TestData.planningService();

    private static Route otherPair() {
        return RouteFactory.create(TransportMode.ROAD, new RouteParams().routeId(9).name("Chennai road")
                .source("Chennai").destination("Kolkata")
                .legs(List.of(leg(TransportMode.ROAD, 1600, "4.50", 50.0, 2.0, 0.09))).capacityKg(20000));
    }

    private static RouteEvaluation byId(List<RouteEvaluation> evaluations, long id) {
        for (RouteEvaluation e : evaluations) {
            if (e.getRouteId() == id) {
                return e;
            }
        }
        throw new AssertionError("No evaluation for route " + id);
    }

    @Test
    void keepsRejectionsEvaluationsAndExclusionsSeparate() throws Exception {
        List<Route> candidates = List.of(TestData.route1(), TestData.route2(), TestData.route3(),
                TestData.rail(5, "2.00", null, 0.9), otherPair());
        PlanningResult result = planning.plan(TestData.shipment(16000), candidates, "greenest");

        List<Long> rejected = new ArrayList<>();
        for (RejectedRoute r : result.getRejections()) {
            rejected.add(r.getRoute().getRouteId());
        }
        assertEquals(List.of(3L, 9L), rejected);
        assertEquals(List.of(1L, 2L, 5L), TestData.ids(result.getEvaluations()));
        assertEquals(Map.of(5L, "Emissions not available"), result.getStrategyExclusions());

        Recommendation rec = result.getRecommendation();
        assertEquals(2L, rec.getChosen().getRouteId());
        assertSame(byId(result.getEvaluations(), 2), rec.getChosen());
        for (RouteEvaluation ranked : rec.getRanking()) {
            assertSame(byId(result.getEvaluations(), ranked.getRouteId()), ranked);
        }
        assertSame(byId(result.getEvaluations(), 5), rec.getExcluded().get(0).getEvaluation());
    }

    @Test
    void rejectsCopiedEvaluations() {
        Shipment shipment = TestData.shipment();
        RouteEvaluator evaluator = TestData.evaluator();
        EligibilityService eligibility = new EligibilityService();
        List<RouteEvaluation> evaluations = evaluator.evaluateAll(shipment, TestData.dataset3());
        List<RouteEvaluation> copies = evaluator.evaluateAll(shipment, TestData.dataset3());
        Recommendation fromCopies = new GreenestStrategy().recommend(copies);
        assertThrows(IllegalArgumentException.class, () -> new PlanningResult(shipment,
                eligibility.filter(shipment, TestData.dataset3()), evaluations, fromCopies));
    }

    @Test
    void strategyCheckedBeforeShipment() {
        Shipment invalid = new Shipment(0, 1, "", "", null, -1, "", null, ShipmentStatus.DRAFT);
        assertThrows(InvalidStrategyException.class, () -> planning.plan(invalid, TestData.dataset3(), "bogus"));
        assertThrows(InvalidShipmentException.class, () -> planning.plan(invalid, TestData.dataset3(), "CHEAPEST"));
    }

    @Test
    void savedShipmentWithPastDispatchDateCanStillBePlanned() throws Exception {
        Shipment past = new Shipment(53, 1, "Mumbai", "Delhi", CargoType.GENERAL, 5000, "",
                TestData.DISPATCH.minusYears(1), ShipmentStatus.DRAFT);
        PlanningResult result = planning.plan(past, List.of(TestData.route1(), TestData.route2(),
                TestData.route3(), TestData.route4()), "BALANCED");
        assertEquals(2L, result.getRecommendation().getChosen().getRouteId());
        assertEquals(TestData.DISPATCH.minusYears(1), result.getRecommendation().getChosen()
                .getEstimatedArrival().toLocalDate().minusDays(1));
    }

    @Test
    void noEligibleRoutesGivesNoRecommendation() throws Exception {
        PlanningResult result = planning.plan(TestData.shipment(40000, CargoType.GENERAL), TestData.dataset3(),
                "BALANCED");
        assertTrue(result.getEvaluations().isEmpty());
        assertFalse(result.getRecommendation().isRecommended());
        assertEquals(3, result.getRejections().size());
    }

    @Test
    void hundredRoutesPlanQuickly() {
        List<Route> routes = new ArrayList<>();
        for (int i = 1; i <= MANY_ROUTES; i++) {
            routes.add(RouteFactory.create(TransportMode.RAIL, TestData.params(i, "Rail " + i,
                    leg(TransportMode.RAIL, 1000 + i, "2.80", 40.0 + i % 10, 6.0, 0.03))
                    .handlingCharge(new BigDecimal(1000 + i)).reliability(0.5 + i % 5 / 10.0).capacityKg(30000)));
        }
        PlanningResult result = assertTimeout(Duration.ofSeconds(2),
                () -> planning.plan(TestData.shipment(), routes, "BALANCED"));
        assertEquals(MANY_ROUTES, result.getEvaluations().size());
        assertEquals(MANY_ROUTES, result.getRecommendation().getRanking().size());
    }
}

package com.logiopt;

import com.logiopt.model.CargoType;
import com.logiopt.model.Route;
import com.logiopt.model.RouteEvaluation;
import com.logiopt.model.RouteFactory;
import com.logiopt.model.RouteLeg;
import com.logiopt.model.RouteParams;
import com.logiopt.model.Shipment;
import com.logiopt.model.ShipmentStatus;
import com.logiopt.model.TransportMode;
import com.logiopt.service.CostService;
import com.logiopt.service.DurationService;
import com.logiopt.service.EligibilityService;
import com.logiopt.service.EmissionService;
import com.logiopt.service.PlanningService;
import com.logiopt.service.RecommendationService;
import com.logiopt.service.RouteEvaluator;
import com.logiopt.service.ShipmentValidator;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared fixtures: the verified routes of section 7 and a fixed clock (2026-10-21).
 */
public final class TestData {

    /** Fixed test clock: 2026-10-21. */
    public static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-21T00:00:00Z"), ZoneOffset.UTC);
    /** Dispatch date of the verified shipment. */
    public static final LocalDate DISPATCH = LocalDate.of(2026, 11, 2);
    /** Weight of the verified shipment in kg. */
    public static final double WEIGHT_KG = 5000;
    /** Money and metric tolerance. */
    public static final double EPS = 1e-9;

    private TestData() {
    }

    /** @param weightKg weight @return Mumbai to Delhi GENERAL shipment */
    public static Shipment shipment(double weightKg) {
        return shipment(weightKg, CargoType.GENERAL);
    }

    /** @param weightKg weight @param cargo cargo @return Mumbai to Delhi shipment */
    public static Shipment shipment(double weightKg, CargoType cargo) {
        return new Shipment(0, 1, "Mumbai", "Delhi", cargo, weightKg, "1x1x1 m", DISPATCH, ShipmentStatus.DRAFT);
    }

    /** @return the verified 5000 kg shipment */
    public static Shipment shipment() {
        return shipment(WEIGHT_KG);
    }

    /** Creates a leg. */
    public static RouteLeg leg(TransportMode mode, double km, String rate, Double speed, double buffer,
                               Double factor) {
        return new RouteLeg(mode, km, new BigDecimal(rate), speed, buffer, factor);
    }

    /** Creates Mumbai to Delhi params with the given legs. */
    public static RouteParams params(long id, String name, RouteLeg... legs) {
        return new RouteParams().routeId(id).name(name).source("Mumbai").destination("Delhi").legs(List.of(legs));
    }

    /** @return Route 1 "Road direct" */
    public static Route route1() {
        return RouteFactory.create(TransportMode.ROAD,
                params(1, "Road direct", leg(TransportMode.ROAD, 1400, "4.50", 50.0, 2.0, 0.09))
                        .handlingCharge(new BigDecimal("1500")).reliability(0.80).capacityKg(20000));
    }

    /** @return Route 2 "Rail direct" */
    public static Route route2() {
        return RouteFactory.create(TransportMode.RAIL,
                params(2, "Rail direct", leg(TransportMode.RAIL, 1450, "2.80", 45.0, 6.0, 0.03))
                        .handlingCharge(new BigDecimal("2500")).reliability(0.90).capacityKg(30000));
    }

    /** @return Route 3 "Road-Rail-Road" */
    public static Route route3() {
        return RouteFactory.create(TransportMode.MULTIMODAL,
                params(3, "Road-Rail-Road",
                        leg(TransportMode.ROAD, 60, "4.50", 40.0, 0, 0.09),
                        leg(TransportMode.RAIL, 1400, "2.80", 45.0, 4.0, 0.03),
                        leg(TransportMode.ROAD, 50, "4.50", 40.0, 0, 0.09))
                        .handlingCharge(new BigDecimal("2000")).transferChargePerTransfer(new BigDecimal("800"))
                        .transferHoursPerTransfer(3).waitingHours(4).waitingRatePerHour(new BigDecimal("100"))
                        .reliability(0.70).capacityKg(15000));
    }

    /** @return Route 4 "Rail electric" */
    public static Route route4() {
        return RouteFactory.create(TransportMode.RAIL,
                params(4, "Rail electric", leg(TransportMode.RAIL, 1450, "3.10", 40.0, 8.0, 0.02))
                        .handlingCharge(new BigDecimal("2800")).reliability(0.85).capacityKg(30000));
    }

    /** @return a rail route with the given id, emission factor and reliability */
    public static Route rail(long id, String rate, Double factor, Double reliability) {
        return RouteFactory.create(TransportMode.RAIL,
                params(id, "Rail " + id, leg(TransportMode.RAIL, 1450, rate, 45.0, 6.0, factor))
                        .handlingCharge(new BigDecimal("2500")).reliability(reliability).capacityKg(30000));
    }

    /** @return routes 1-3 */
    public static List<Route> dataset3() {
        return List.of(route1(), route2(), route3());
    }

    /** @return a new evaluator */
    public static RouteEvaluator evaluator() {
        return new RouteEvaluator(new CostService(), new DurationService(), new EmissionService());
    }

    /** @return a new planning service using the fixed clock */
    public static PlanningService planningService() {
        return new PlanningService(new ShipmentValidator(CLOCK), new EligibilityService(), evaluator(),
                new RecommendationService());
    }

    /** @return evaluations of the routes for the verified shipment */
    public static List<RouteEvaluation> evaluate(List<Route> routes) {
        return evaluator().evaluateAll(shipment(), routes);
    }

    /** @return route ids in list order */
    public static List<Long> ids(List<RouteEvaluation> evaluations) {
        List<Long> ids = new ArrayList<>();
        for (RouteEvaluation e : evaluations) {
            ids.add(e.getRouteId());
        }
        return ids;
    }

    /** @return parses "2;3;1" into ids */
    public static List<Long> parseIds(String text) {
        List<Long> ids = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return ids;
        }
        for (String part : text.split(";")) {
            ids.add(Long.parseLong(part.trim()));
        }
        return ids;
    }
}

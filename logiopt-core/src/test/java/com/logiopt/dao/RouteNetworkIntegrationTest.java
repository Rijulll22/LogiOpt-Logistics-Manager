package com.logiopt.dao;

import com.logiopt.TestData;
import com.logiopt.model.CargoType;
import com.logiopt.model.PlanningResult;
import com.logiopt.model.Recommendation;
import com.logiopt.model.Route;
import com.logiopt.model.RouteLeg;
import com.logiopt.model.Shipment;
import com.logiopt.model.ShipmentStatus;
import com.logiopt.model.TransportMode;
import com.logiopt.service.RecommendationService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The V9 route network against the disposable test database (LOGIOPT_TEST_DB_URL, name must end in
 * _test), rebuilt from the migrations first: every ordered pair of the six cities is plannable,
 * the generated routes match the distance table and the V3 Mumbai -> Delhi constants, V3's own
 * routes keep their verified scores, and the data passes basic quality checks.
 */
class RouteNetworkIntegrationTest {

    private static final double SCORE_EPS = 1e-5;
    private static final List<String> CITIES = List.of("Ahmedabad", "Bengaluru", "Chennai", "Delhi", "Kolkata", "Mumbai");
    /** The three corridors V3 already serves; V9 leaves them alone. */
    private static final Set<String> V3_CORRIDORS = Set.of("Mumbai>Delhi", "Chennai>Kolkata", "Ahmedabad>Bengaluru");
    /** Illustrative road distances in km, the same in both directions (Phase 7 table). */
    private static final Map<String, Integer> ROAD_KM = Map.ofEntries(
            Map.entry("Delhi|Mumbai", 1400), Map.entry("Ahmedabad|Mumbai", 530), Map.entry("Bengaluru|Mumbai", 980),
            Map.entry("Chennai|Mumbai", 1330), Map.entry("Kolkata|Mumbai", 2000), Map.entry("Ahmedabad|Delhi", 940),
            Map.entry("Bengaluru|Delhi", 2150), Map.entry("Chennai|Delhi", 2180), Map.entry("Delhi|Kolkata", 1500),
            Map.entry("Ahmedabad|Bengaluru", 1490), Map.entry("Ahmedabad|Chennai", 1830),
            Map.entry("Ahmedabad|Kolkata", 2050), Map.entry("Bengaluru|Chennai", 350),
            Map.entry("Bengaluru|Kolkata", 1870), Map.entry("Chennai|Kolkata", 1670));
    private static final List<String> GENERATED_NAMES = List.of("Road direct", "Rail direct", "Road-Rail-Road");

    private static DBConnection db;
    private static RouteDAO routes;

    @BeforeAll
    static void connect() throws Exception {
        db = TestDatabase.connect();
        TestDatabase.dropAllTables(db);
        new MigrationRunner(db).migrate();
        routes = new RouteDAO(db);
    }

    /** @return every ordered pair of distinct cities as {source, destination} */
    static List<String[]> orderedPairs() {
        List<String[]> pairs = new ArrayList<>();
        for (String source : CITIES) {
            for (String destination : CITIES) {
                if (!source.equals(destination)) {
                    pairs.add(new String[] {source, destination});
                }
            }
        }
        return pairs;
    }

    /** @return generated pairs only (all ordered pairs except V3's three corridors) */
    static List<String[]> generatedPairs() {
        List<String[]> pairs = new ArrayList<>();
        for (String[] p : orderedPairs()) {
            if (!V3_CORRIDORS.contains(p[0] + ">" + p[1])) {
                pairs.add(p);
            }
        }
        return pairs;
    }

    private static int roadKm(String a, String b) {
        String key = a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
        return ROAD_KM.get(key);
    }

    private static int railKm(int roadKm) {
        return new BigDecimal(roadKm).multiply(new BigDecimal("1.05")).setScale(0, RoundingMode.HALF_UP).intValueExact();
    }

    private static Shipment shipment(String source, String destination, CargoType cargo) {
        return new Shipment(0, 1, source, destination, cargo, TestData.WEIGHT_KG, "", TestData.DISPATCH,
                ShipmentStatus.DRAFT);
    }

    private static List<Double> legKm(Route route) {
        List<Double> km = new ArrayList<>();
        for (RouteLeg leg : route.getLegs()) {
            km.add(leg.getDistanceKm());
        }
        return km;
    }

    private static List<TransportMode> legModes(Route route) {
        List<TransportMode> modes = new ArrayList<>();
        for (RouteLeg leg : route.getLegs()) {
            modes.add(leg.getMode());
        }
        return modes;
    }

    @Test
    void thirtyOrderedPairsAndKnownCitiesUnchanged() throws Exception {
        assertEquals(30, orderedPairs().size());
        assertEquals(27, generatedPairs().size());
        assertEquals(CITIES, routes.knownCities());
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("orderedPairs")
    void everyOrderedPairHasThreeEligibleRoutesAndABalancedRecommendation(String source, String destination)
            throws Exception {
        List<Route> pairRoutes = routes.findBySourceAndDestination(source, destination);
        PlanningResult result = TestData.planningService().plan(shipment(source, destination, CargoType.GENERAL),
                pairRoutes, "BALANCED");
        assertTrue(result.getEligibility().getEligible().size() >= 3, source + " -> " + destination);
        Recommendation balanced = result.getRecommendation();
        assertTrue(balanced.isRecommended(), balanced.getReason());
        assertTrue(pairRoutes.stream().anyMatch(r -> r.getRouteId() == balanced.getChosen().getRouteId()));
    }

    @Test
    void seedRoutesLoadedFromTheDatabaseKeepTheirVerifiedScores() throws Exception {
        List<Route> core4 = routes.findBySourceAndDestination("Mumbai", "Delhi");
        assertEquals(List.of(1L, 2L, 3L, 4L), core4.stream().map(Route::getRouteId).toList());
        RecommendationService service = new RecommendationService();
        Recommendation four = service.recommend("BALANCED", TestData.evaluate(core4));
        assertEquals(0.625000, four.getScores().get(1L).getTotalScore(), SCORE_EPS);
        assertEquals(0.167971, four.getScores().get(2L).getTotalScore(), SCORE_EPS);
        assertEquals(0.609594, four.getScores().get(3L).getTotalScore(), SCORE_EPS);
        assertEquals(0.361788, four.getScores().get(4L).getTotalScore(), SCORE_EPS);
        for (String[] winner : new String[][] {{"CHEAPEST", "2"}, {"FASTEST", "1"}, {"GREENEST", "4"}, {"BALANCED", "2"}}) {
            assertEquals(Long.parseLong(winner[1]),
                    service.recommend(winner[0], TestData.evaluate(core4)).getChosen().getRouteId(), winner[0]);
        }
        Recommendation three = service.recommend("BALANCED", TestData.evaluate(core4.subList(0, 3)));
        assertEquals(0.625000, three.getScores().get(1L).getTotalScore(), SCORE_EPS);
        assertEquals(0.138074, three.getScores().get(2L).getTotalScore(), SCORE_EPS);
        assertEquals(0.582741, three.getScores().get(3L).getTotalScore(), SCORE_EPS);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("generatedPairs")
    void generatedRoutesFollowTheDistanceTableAndTheMumbaiDelhiConstants(String source, String destination)
            throws Exception {
        List<Route> pairRoutes = routes.findBySourceAndDestination(source, destination);
        assertEquals(GENERATED_NAMES, pairRoutes.stream().map(Route::getName).toList());
        long base = 1000 + 100L * (CITIES.indexOf(source) + 1) + 10L * (CITIES.indexOf(destination) + 1);
        assertEquals(List.of(base + 1, base + 2, base + 3), pairRoutes.stream().map(Route::getRouteId).toList());
        int road = roadKm(source, destination);
        int rail = railKm(road);

        Route roadDirect = pairRoutes.get(0);
        assertEquals(TransportMode.ROAD, roadDirect.getMode());
        assertEquals(List.of((double) road), legKm(roadDirect));
        assertRoute(roadDirect, 0.800, 20000, "1500", "0", 0, 0, "0", Set.of());
        assertLeg(roadDirect.getLegs().get(0), "4.50", 50.0, 2.0, 0.09);

        Route railDirect = pairRoutes.get(1);
        assertEquals(TransportMode.RAIL, railDirect.getMode());
        assertEquals(List.of((double) rail), legKm(railDirect));
        assertRoute(railDirect, 0.900, 30000, "2500", "0", 0, 0, "0", Set.of(CargoType.HAZARDOUS));
        assertLeg(railDirect.getLegs().get(0), "2.80", 45.0, 6.0, 0.03);

        Route multimodal = pairRoutes.get(2);
        assertEquals(TransportMode.MULTIMODAL, multimodal.getMode());
        assertEquals(List.of(100.0, (double) (rail - 200), 100.0), legKm(multimodal));
        assertEquals(List.of(TransportMode.ROAD, TransportMode.RAIL, TransportMode.ROAD), legModes(multimodal));
        assertRoute(multimodal, 0.700, 15000, "2000", "800", 3, 4, "100", Set.of());
        assertLeg(multimodal.getLegs().get(0), "4.50", 40.0, 0, 0.09);
        assertLeg(multimodal.getLegs().get(1), "2.80", 45.0, 4.0, 0.03);
        assertLeg(multimodal.getLegs().get(2), "4.50", 40.0, 0, 0.09);
    }

    private static void assertRoute(Route route, double reliability, double capacityKg, String handling,
                                    String transfer, double transferHours, double waitingHours, String waitingRate,
                                    Set<CargoType> unsupported) {
        String id = route.getName() + " " + route.getRouteId();
        assertEquals(reliability, route.getReliability(), TestData.EPS, id);
        assertEquals(capacityKg, route.getCapacityKg(), TestData.EPS, id);
        assertEquals(0, new BigDecimal(handling).compareTo(route.getHandlingCharge()), id);
        assertEquals(0, new BigDecimal(transfer).compareTo(route.getTransferChargePerTransfer()), id);
        assertEquals(transferHours, route.getTransferHoursPerTransfer(), TestData.EPS, id);
        assertEquals(waitingHours, route.getWaitingHours(), TestData.EPS, id);
        assertEquals(0, new BigDecimal(waitingRate).compareTo(route.getWaitingRatePerHour()), id);
        assertEquals(unsupported, route.getUnsupportedCargo(), id);
    }

    private static void assertLeg(RouteLeg leg, String rate, Double speed, double buffer, Double factor) {
        assertEquals(0, new BigDecimal(rate).compareTo(leg.getRatePerTonneKm()));
        assertEquals(speed, leg.getSpeedKmh());
        assertEquals(buffer, leg.getBufferHours(), TestData.EPS);
        assertEquals(factor, leg.getEmissionFactorKgPerTonneKm());
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("generatedPairs")
    void forwardAndReverseGeneratedCorridorsMatch(String source, String destination) throws Exception {
        if (V3_CORRIDORS.contains(destination + ">" + source)) {
            return; // the reverse is one of V3's own corridors, which keeps its own distances
        }
        List<Route> forward = routes.findBySourceAndDestination(source, destination);
        List<Route> reverse = routes.findBySourceAndDestination(destination, source);
        assertEquals(forward.size(), reverse.size());
        for (int i = 0; i < forward.size(); i++) {
            Route f = forward.get(i);
            Route r = reverse.get(i);
            assertEquals(f.getName(), r.getName());
            assertEquals(f.getMode(), r.getMode());
            List<Double> reversedKm = new ArrayList<>(legKm(r));
            Collections.reverse(reversedKm);
            assertEquals(legKm(f), reversedKm, f.getName());
            List<TransportMode> reversedModes = new ArrayList<>(legModes(r));
            Collections.reverse(reversedModes);
            assertEquals(legModes(f), reversedModes, f.getName());
            assertEquals(f.getUnsupportedCargo(), r.getUnsupportedCargo());
        }
    }

    @Test
    void everyRouteInTheDatabasePassesTheQualityChecks() throws Exception {
        int total = 0;
        for (String[] pair : orderedPairs()) {
            List<Route> pairRoutes = routes.findBySourceAndDestination(pair[0], pair[1]);
            assertTrue(pairRoutes.size() >= 3, pair[0] + " -> " + pair[1]);
            Set<String> names = new HashSet<>();
            for (Route route : pairRoutes) {
                total++;
                String id = pair[0] + " -> " + pair[1] + " " + route.getName();
                assertTrue(names.add(route.getName().trim().toLowerCase()), "duplicate name: " + id);
                assertFalse(route.getLegs().isEmpty(), id);
                for (RouteLeg leg : route.getLegs()) {
                    assertTrue(leg.getDistanceKm() > 0, id);
                }
                if (route.getMode() == TransportMode.MULTIMODAL) {
                    assertTrue(route.getLegs().size() >= 2, id);
                    assertTrue(new HashSet<>(legModes(route)).size() >= 2, id);
                }
            }
        }
        assertEquals(91, total, "every route belongs to one of the 30 ordered pairs");
        assertEquals(91, TestDatabase.queryLong(db, "SELECT COUNT(*) FROM routes"));
        assertEquals(0, TestDatabase.queryLong(db, "SELECT COUNT(*) FROM route_legs WHERE distance_km <= 0"));
        assertEquals(0, TestDatabase.queryLong(db, "SELECT COUNT(*) FROM (SELECT source, destination, LOWER(TRIM(name)) "
                + "FROM routes GROUP BY source, destination, LOWER(TRIM(name)) HAVING COUNT(*) > 1) d"));
    }

    @Test
    void generatedRailDirectRefusesHazardousCargoAndTheOthersAcceptIt() throws Exception {
        PlanningResult hazardous = TestData.planningService().plan(shipment("Delhi", "Mumbai", CargoType.HAZARDOUS),
                routes.findBySourceAndDestination("Delhi", "Mumbai"), "BALANCED");
        assertEquals(List.of(1461L, 1463L),
                hazardous.getEligibility().getEligible().stream().map(Route::getRouteId).toList());
        assertEquals(1462L, hazardous.getRejections().get(0).getRoute().getRouteId());
        assertTrue(hazardous.getRecommendation().isRecommended());
    }
}

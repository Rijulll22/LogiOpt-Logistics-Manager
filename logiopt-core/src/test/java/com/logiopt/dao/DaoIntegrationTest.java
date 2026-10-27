package com.logiopt.dao;

import com.logiopt.TestData;
import com.logiopt.model.MultimodalRoute;
import com.logiopt.model.PlanningResult;
import com.logiopt.model.RailRoute;
import com.logiopt.model.RoadRoute;
import com.logiopt.model.Route;
import com.logiopt.model.Shipment;
import com.logiopt.model.CargoType;
import com.logiopt.model.ShipmentStatus;
import com.logiopt.service.EligibilityService;
import com.logiopt.service.PlanningService;
import com.logiopt.service.RecommendationService;
import com.logiopt.service.ShipmentValidator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DAO integration tests against the disposable test database (LOGIOPT_TEST_DB_URL, name must end in
 * _test), rebuilt from the migrations first; skipped when LOGIOPT_TEST_DB_URL is unset. They never
 * use LOGIOPT_DB_URL.
 */
class DaoIntegrationTest {

    private static final int SEEDED_ROUTES = 10;

    private static DBConnection db;

    @BeforeAll
    static void connect() throws Exception {
        db = TestDatabase.connect();
        TestDatabase.dropAllTables(db);
        new MigrationRunner(db).migrate();
        assertEquals(SEEDED_ROUTES, TestDatabase.queryLong(db,
                "SELECT COUNT(*) FROM routes WHERE route_id BETWEEN 1 AND 10"));
    }

    private static void deleteShipment(long id) throws Exception {
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement("DELETE FROM shipments WHERE shipment_id = ?")) {
            DBConnection.requireTestDatabase(con);
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    @Test
    void loadsMumbaiDelhiSubclassesAndLegs() throws Exception {
        List<Route> routes = new RouteDAO(db).findBySourceAndDestination("  mumbai ", "DELHI");
        List<Long> ids = new ArrayList<>();
        for (Route r : routes) {
            ids.add(r.getRouteId());
        }
        assertEquals(List.of(1L, 2L, 3L, 4L), ids);
        assertInstanceOf(RoadRoute.class, routes.get(0));
        assertInstanceOf(RailRoute.class, routes.get(1));
        assertInstanceOf(MultimodalRoute.class, routes.get(2));
        assertEquals(3, routes.get(2).getLegs().size());
        assertEquals(0.80, routes.get(0).getReliability(), TestData.EPS);
    }

    @Test
    void otherPairsCarryNullFactorAndHazardousRestriction() throws Exception {
        RouteDAO dao = new RouteDAO(db);
        List<Route> ahmedabad = dao.findBySourceAndDestination("Ahmedabad", "Bengaluru");
        assertEquals(3, ahmedabad.size());
        assertNull(ahmedabad.get(1).getLegs().get(0).getEmissionFactorKgPerTonneKm());
        List<Route> chennai = dao.findBySourceAndDestination("Chennai", "Kolkata");
        assertEquals(3, chennai.size());
        assertTrue(chennai.get(1).getUnsupportedCargo().contains(CargoType.HAZARDOUS));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({"CHEAPEST, 2", "FASTEST, 1", "GREENEST, 4", "BALANCED, 2"})
    void daoLoadedEngineWinners(String key, long winner) throws Exception {
        PlanningService planning = TestData.planningService();
        PlanningResult result = planning.plan(TestData.shipment(),
                new RouteDAO(db).findBySourceAndDestination("Mumbai", "Delhi"), key);
        assertEquals(winner, result.getRecommendation().getChosen().getRouteId());
    }

    @Test
    void insertFindByIdAndFindByUser() throws Exception {
        ShipmentDAO dao = new ShipmentDAO(db);
        Shipment shipment = new Shipment(0, 1, "Mumbai", "Delhi", CargoType.FRAGILE, 1234.5, "2 x 1 x 1 m",
                TestData.DISPATCH, ShipmentStatus.DRAFT);
        long id = dao.insert(shipment);
        try {
            Optional<Shipment> loaded = dao.findById(id);
            assertTrue(loaded.isPresent());
            assertEquals(CargoType.FRAGILE, loaded.get().getCargoType());
            assertEquals(1234.5, loaded.get().getWeightKg(), TestData.EPS);
            assertEquals(TestData.DISPATCH, loaded.get().getDispatchDate());
            assertTrue(dao.findByUser(1).stream().anyMatch(s -> s.getShipmentId() == id));
        } finally {
            deleteShipment(id);
        }
    }

    @Test
    void knownCitiesListsEverySeededEndpointOnce() throws Exception {
        assertEquals(List.of("Ahmedabad", "Bengaluru", "Chennai", "Delhi", "Kolkata", "Mumbai"),
                new RouteDAO(db).knownCities());
    }

    @ParameterizedTest(name = "{0} kg")
    @ValueSource(doubles = {0.001, 999_999_999.999, 1234.567})
    void boundaryWeightsRoundTripExactly(double weight) throws Exception {
        ShipmentDAO dao = new ShipmentDAO(db);
        long id = dao.insert(new Shipment(0, 1, "Mumbai", "Delhi", CargoType.GENERAL, weight, "", TestData.DISPATCH,
                ShipmentStatus.DRAFT));
        try {
            assertEquals(weight, dao.findById(id).orElseThrow().getWeightKg());
        } finally {
            deleteShipment(id);
        }
    }

    @Test
    void weightWithMoreThanThreeDecimalsIsNeverRoundedOnInsert() {
        Shipment tooPrecise = new Shipment(0, 1, "Mumbai", "Delhi", CargoType.GENERAL, 1.0005, "", TestData.DISPATCH,
                ShipmentStatus.DRAFT);
        assertThrows(ArithmeticException.class, () -> new ShipmentDAO(db).insert(tooPrecise));
    }

    @Test
    void savedShipmentWithPastDispatchDateIsStillPlanned() throws Exception {
        ShipmentDAO dao = new ShipmentDAO(db);
        long id = dao.insert(new Shipment(0, 1, "Mumbai", "Delhi", CargoType.GENERAL, 5000, "",
                LocalDate.of(2025, 1, 15), ShipmentStatus.DRAFT));
        try {
            Shipment saved = dao.findById(id).orElseThrow();
            PlanningService planning = new PlanningService(new ShipmentValidator(Clock.systemDefaultZone()),
                    new EligibilityService(), TestData.evaluator(), new RecommendationService());
            PlanningResult result = planning.plan(saved,
                    new RouteDAO(db).findBySourceAndDestination(saved.getSource(), saved.getDestination()), "BALANCED");
            assertEquals(2L, result.getRecommendation().getChosen().getRouteId());
        } finally {
            deleteShipment(id);
        }
    }
}

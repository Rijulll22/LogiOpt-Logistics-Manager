package com.logiopt.dao;

import com.logiopt.TestData;
import com.logiopt.model.PlanningResult;
import com.logiopt.model.Role;
import com.logiopt.model.Shipment;
import com.logiopt.model.ShipmentStatus;
import com.logiopt.service.PasswordHasher;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs docs/demo/past-dated-shipment.sql against the disposable test database (LOGIOPT_TEST_DB_URL,
 * name ending in _test), exactly as a person would run it manually, and checks that it inserts one
 * past-dated draft for the named customer that can still be compared.
 */
class DemoScriptIntegrationTest {

    /** Repository root (set by the logiopt-core surefire configuration); tests run in the module folder. */
    private static final Path REPO_ROOT = Paths.get(System.getProperty("logiopt.repoRoot", ".."));
    private static final Path SCRIPT = REPO_ROOT.resolve(Paths.get("docs", "demo", "past-dated-shipment.sql"));
    private static final String PLACEHOLDER = "'customer@example.com'";

    private static DBConnection db;

    @BeforeAll
    static void connect() throws Exception {
        db = TestDatabase.connect();
        TestDatabase.dropAllTables(db);
        new MigrationRunner(db).migrate();
    }

    private static void runScript(String email) throws Exception {
        String sql = Files.readString(SCRIPT, StandardCharsets.UTF_8);
        assertTrue(sql.contains("SET @customer_email = " + PLACEHOLDER + ";"), "the email is set in one place");
        TestDatabase.execute(db, MigrationRunner.splitStatements(sql.replace(PLACEHOLDER, "'" + email + "'"))
                .toArray(new String[0]));
    }

    @Test
    void scriptLivesOutsideTheClasspathAndSaysToRunItManually() throws Exception {
        assertTrue(Files.isRegularFile(SCRIPT));
        assertNull(getClass().getResource("/demo/past-dated-shipment.sql"));
        assertNull(getClass().getResource("/docs/demo/past-dated-shipment.sql"));
        assertFalse(Files.exists(Paths.get("src", "main", "resources", "demo")));
        String header = Files.readString(SCRIPT, StandardCharsets.UTF_8).substring(0, 600);
        assertTrue(header.contains("NOT A MIGRATION") && header.contains("Run it MANUALLY"), header);
    }

    @Test
    void insertsOnePastDatedDraftForTheNamedCustomerOnly() throws Exception {
        TestDatabase.execute(db, "DELETE FROM reports", "DELETE FROM notifications", "DELETE FROM tracking",
                "DELETE FROM payments", "DELETE FROM booking_legs", "DELETE FROM bookings", "DELETE FROM shipments",
                "DELETE FROM users WHERE user_id <> 1");
        UserDAO users = new UserDAO(db);
        PasswordHasher hasher = new PasswordHasher(PasswordHasher.MIN_ITERATIONS, new SecureRandom());
        long demoCustomer = users.insert("demo.customer@example.test", "Demo", hasher.hash("x".toCharArray()),
                Role.CUSTOMER).orElseThrow();
        users.insert("other@example.test", "Other", hasher.hash("x".toCharArray()), Role.CUSTOMER).orElseThrow();

        runScript(" Demo.Customer@Example.test ");
        runScript("demo.customer@example.test");
        runScript("nobody@example.test");
        assertEquals(1, TestDatabase.queryLong(db, "SELECT COUNT(*) FROM shipments"), "one row, also when run again");

        List<Shipment> mine = new ShipmentDAO(db).findByUser(demoCustomer);
        assertEquals(1, mine.size());
        Shipment past = mine.get(0);
        LocalDate dbToday = LocalDate.parse(TestDatabase.queryString(db, "SELECT CURRENT_DATE"));
        assertEquals(dbToday.minusDays(30), past.getDispatchDate());
        assertEquals(ShipmentStatus.DRAFT, past.getStatus());
        assertEquals("Mumbai", past.getSource());
        assertEquals("Delhi", past.getDestination());

        PlanningResult result = TestData.planningService().plan(past,
                new RouteDAO(db).findBySourceAndDestination(past.getSource(), past.getDestination()), "BALANCED");
        assertFalse(result.getEvaluations().isEmpty(), "the past-dated shipment can still be compared");
    }
}

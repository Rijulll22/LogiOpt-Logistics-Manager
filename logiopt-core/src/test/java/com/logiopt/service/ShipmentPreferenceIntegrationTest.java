package com.logiopt.service;

import com.logiopt.app.ShipmentFormInput;
import com.logiopt.dao.DBConnection;
import com.logiopt.dao.MigrationRunner;
import com.logiopt.dao.ShipmentDAO;
import com.logiopt.dao.TestDatabase;
import com.logiopt.exception.ShipmentNotEditableException;
import com.logiopt.model.CargoType;
import com.logiopt.model.OptimisationPreference;
import com.logiopt.model.Role;
import com.logiopt.model.Shipment;
import com.logiopt.model.ShipmentStatus;
import com.logiopt.model.User;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The V10 optimisation preference through the shipment DAO and ShipmentService against the
 * disposable test database (LOGIOPT_TEST_DB_URL, name ending in _test): stored and read back,
 * missing means BALANCED, the web form (no preference) never clears a stored one, and editing is
 * refused once the shipment is booked.
 */
class ShipmentPreferenceIntegrationTest {

    private static final LocalDate DISPATCH = LocalDate.now().plusDays(10);
    private static final User OWNER = new User(1, "demo@logiopt.invalid", "Demo User", Role.CUSTOMER, true);
    private static final List<String> CITIES = List.of("Mumbai", "Delhi", "Chennai", "Kolkata");

    private static DBConnection db;
    private static ShipmentDAO shipments;
    private static ShipmentService service;

    @BeforeAll
    static void connect() throws Exception {
        db = TestDatabase.connect();
        TestDatabase.dropAllTables(db);
        new MigrationRunner(db).migrate();
        shipments = new ShipmentDAO(db);
        service = new ShipmentService(shipments, new ShipmentValidator(Clock.systemDefaultZone()));
    }

    private static Shipment draft(OptimisationPreference preference) {
        return new Shipment(0, OWNER.getUserId(), "Mumbai", "Delhi", CargoType.GENERAL, 5000, "", DISPATCH,
                ShipmentStatus.DRAFT, preference);
    }

    @Test
    void preferenceIsStoredAndMissingMeansBalanced() throws Exception {
        long fast = shipments.insert(draft(OptimisationPreference.FASTEST));
        long none = shipments.insert(draft(null));
        Shipment loadedFast = shipments.findByIdForUser(fast, OWNER.getUserId()).orElseThrow();
        assertTrue(loadedFast.hasPreference());
        assertEquals(OptimisationPreference.FASTEST, loadedFast.getPreference());
        Shipment loadedNone = shipments.findByIdForUser(none, OWNER.getUserId()).orElseThrow();
        assertFalse(loadedNone.hasPreference());
        assertEquals(OptimisationPreference.BALANCED, loadedNone.getPreference());
        assertEquals("FASTEST", TestDatabase.queryString(db,
                "SELECT optimisation_preference FROM shipments WHERE shipment_id = " + fast));
        assertEquals(1, TestDatabase.queryLong(db,
                "SELECT COUNT(*) FROM shipments WHERE optimisation_preference IS NULL AND shipment_id = " + none));
    }

    @Test
    void draftEditChangesThePreferenceButTheWebFormNeverClearsIt() throws Exception {
        long id = shipments.insert(draft(OptimisationPreference.CHEAPEST));
        Map<String, String> form = new HashMap<>(ShipmentFormInput.valuesOf(shipments.findByIdForUser(id, 1).orElseThrow()));
        assertEquals("CHEAPEST", form.get(ShipmentFormInput.PREFERENCE));
        form.put(ShipmentFormInput.PREFERENCE, "GREENEST");
        service.updateDraft(OWNER, id, ShipmentFormInput.parse(form::get, 1).getShipment(), CITIES);
        assertEquals(OptimisationPreference.GREENEST, shipments.findByIdForUser(id, 1).orElseThrow().getPreference());

        Map<String, String> web = new HashMap<>(form);
        web.remove(ShipmentFormInput.PREFERENCE);
        web.put(ShipmentFormInput.WEIGHT_KG, "1234.5");
        service.updateDraft(OWNER, id, ShipmentFormInput.parse(web::get, 1).getShipment(), CITIES);
        Shipment after = shipments.findByIdForUser(id, 1).orElseThrow();
        assertEquals(1234.5, after.getWeightKg());
        assertEquals(OptimisationPreference.GREENEST, after.getPreference(), "a form without the field keeps it");

        Map<String, String> bad = new HashMap<>(form);
        bad.put(ShipmentFormInput.PREFERENCE, "FANCIEST");
        assertEquals(List.of("Unknown optimisation preference."), ShipmentFormInput.parse(bad::get, 1).getParseErrors());
        assertEquals(ShipmentFormInput.PREFERENCE, ShipmentFormInput.fieldOf("Unknown optimisation preference."));
    }

    @Test
    void thePreferenceCannotBeChangedOnceBooked() throws Exception {
        long id = shipments.insert(draft(OptimisationPreference.FASTEST));
        TestDatabase.execute(db, "UPDATE shipments SET status = 'BOOKED' WHERE shipment_id = " + id);
        Map<String, String> form = new HashMap<>(ShipmentFormInput.valuesOf(shipments.findByIdForUser(id, 1).orElseThrow()));
        form.put(ShipmentFormInput.PREFERENCE, "BALANCED");
        assertThrows(ShipmentNotEditableException.class, () -> service.updateDraft(OWNER, id,
                ShipmentFormInput.parse(form::get, 1).getShipment(), CITIES));
        assertEquals(OptimisationPreference.FASTEST, shipments.findByIdForUser(id, 1).orElseThrow().getPreference());
    }
}

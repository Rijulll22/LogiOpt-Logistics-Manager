package com.logiopt.report;

import com.logiopt.model.CargoType;
import com.logiopt.model.OptimisationPreference;
import com.logiopt.model.Shipment;
import com.logiopt.model.ShipmentStatus;
import com.logiopt.service.ShipmentValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shipment snapshots and the V10 optimisation preference. The fixture
 * {@code snapshots/shipment-before-preference.ser} was written by the Shipment class as it was before
 * the preference field existed; it must still load, as BALANCED. Needs no database.
 */
class ShipmentPreferenceSnapshotTest {

    @TempDir
    Path dir;

    private ShipmentSnapshotService service() {
        return new ShipmentSnapshotService(dir, new ShipmentValidator(Clock.fixed(Instant.parse("2026-10-21T00:00:00Z"),
                ZoneOffset.UTC)));
    }

    @Test
    void aSnapshotWrittenBeforeThePreferenceExistedLoadsAsBalanced() throws Exception {
        Path file = dir.resolve("shipment-41.ser");
        try (InputStream in = getClass().getResourceAsStream("/snapshots/shipment-before-preference.ser")) {
            Files.copy(in, file);
        }
        Shipment s = service().load(file);
        assertEquals(41, s.getShipmentId());
        assertEquals("Mumbai", s.getSource());
        assertEquals(5000.0, s.getWeightKg());
        assertFalse(s.hasPreference());
        assertEquals(OptimisationPreference.BALANCED, s.getPreference());
    }

    @Test
    void aSnapshotWithAPreferenceRoundTripsThroughTheAllowList() throws Exception {
        Shipment s = new Shipment(42, 7, "Chennai", "Kolkata", CargoType.FRAGILE, 320, "", LocalDate.of(2026, 11, 4),
                ShipmentStatus.DRAFT, OptimisationPreference.GREENEST);
        Shipment loaded = service().load(service().save(s));
        assertTrue(loaded.hasPreference());
        assertEquals(OptimisationPreference.GREENEST, loaded.getPreference());
        assertEquals(OptimisationPreference.GREENEST, loaded.withShipmentId(43).getPreference(), "copies keep it");
    }

    @Test
    void preferencesNameTheFourStrategies() {
        assertEquals("CHEAPEST", OptimisationPreference.CHEAPEST.getStrategyKey());
        assertEquals("Lowest emissions", OptimisationPreference.GREENEST.getDescription());
        assertEquals(null, OptimisationPreference.parseOrNull(" "));
        assertEquals(OptimisationPreference.FASTEST, OptimisationPreference.parseOrNull(" FASTEST "));
    }
}

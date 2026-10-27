package com.logiopt.report;

import com.logiopt.TestData;
import com.logiopt.exception.LogiOptException;
import com.logiopt.model.CargoType;
import com.logiopt.model.Shipment;
import com.logiopt.model.ShipmentStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ObjectInputFilter;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipmentSnapshotServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void roundTrip() throws Exception {
        ShipmentSnapshotService service = new ShipmentSnapshotService(tempDir.resolve("snapshots"));
        Shipment original = TestData.shipment().withShipmentId(7);
        Path file = service.save(original);
        assertEquals("shipment-7.ser", file.getFileName().toString());
        Shipment loaded = service.load(file);
        assertNotSame(original, loaded);
        assertEquals(original.getShipmentId(), loaded.getShipmentId());
        assertEquals(original.getUserId(), loaded.getUserId());
        assertEquals(original.getSource(), loaded.getSource());
        assertEquals(original.getDestination(), loaded.getDestination());
        assertEquals(original.getCargoType(), loaded.getCargoType());
        assertEquals(original.getWeightKg(), loaded.getWeightKg());
        assertEquals(original.getDimensions(), loaded.getDimensions());
        assertEquals(original.getDispatchDate(), loaded.getDispatchDate());
        assertEquals(original.getStatus(), loaded.getStatus());
    }

    @Test
    void savedShipmentWithPastDispatchDateLoads() throws Exception {
        ShipmentSnapshotService service = new ShipmentSnapshotService(tempDir);
        Shipment past = new Shipment(8, 1, "Mumbai", "Delhi", CargoType.GENERAL, 0.001, "", LocalDate.of(2025, 1, 1),
                ShipmentStatus.DRAFT);
        assertEquals(LocalDate.of(2025, 1, 1), service.load(service.save(past)).getDispatchDate());
    }

    @Test
    void invalidShipmentContentRejectedAfterDeserialization() throws Exception {
        ShipmentSnapshotService service = new ShipmentSnapshotService(tempDir);
        Path badWeight = service.save(new Shipment(9, 1, "Mumbai", "Delhi", CargoType.GENERAL, 0.0001, "",
                TestData.DISPATCH, ShipmentStatus.DRAFT));
        LogiOptException ex = assertThrows(LogiOptException.class, () -> service.load(badWeight));
        assertTrue(ex.getMessage().contains("Weight must be at least 0.001 kg."), ex.getMessage());
        Path noSource = service.save(new Shipment(10, 1, null, "Delhi", CargoType.GENERAL, 5, "", TestData.DISPATCH,
                ShipmentStatus.DRAFT));
        assertThrows(LogiOptException.class, () -> service.load(noSource));
        Path noId = service.save(TestData.shipment());
        assertThrows(LogiOptException.class, () -> service.load(noId));
        Path noStatus = service.save(new Shipment(11, 1, "Mumbai", "Delhi", CargoType.GENERAL, 5, "",
                TestData.DISPATCH, null));
        assertThrows(LogiOptException.class, () -> service.load(noStatus));
    }

    @Test
    void oversizedSnapshotRejected() throws Exception {
        ShipmentSnapshotService service = new ShipmentSnapshotService(tempDir);
        Path file = service.save(new Shipment(12, 1, "Mumbai", "Delhi", CargoType.GENERAL, 5, "x".repeat(5000),
                TestData.DISPATCH, ShipmentStatus.DRAFT));
        assertTrue(Files.size(file) > ShipmentSnapshotService.MAX_SNAPSHOT_BYTES);
        assertThrows(LogiOptException.class, () -> service.load(file));
    }

    @Test
    void filterRejectsClassesOutsideAllowListAndDeepGraphs() throws Exception {
        ObjectInputFilter filter = ObjectInputFilter.Config.createFilter(ShipmentSnapshotService.FILTER_PATTERN);
        Path map = tempDir.resolve("map.ser");
        try (OutputStream os = Files.newOutputStream(map); ObjectOutputStream out = new ObjectOutputStream(os)) {
            out.writeObject(new HashMap<String, String>());
        }
        assertThrows(LogiOptException.class, () -> new ShipmentSnapshotService(tempDir).load(map));
        Path nested = tempDir.resolve("nested.ser");
        Object[] deep = new Object[1];
        Object[] cursor = deep;
        for (int i = 0; i < 10; i++) {
            Object[] next = new Object[1];
            cursor[0] = next;
            cursor = next;
        }
        try (OutputStream os = Files.newOutputStream(nested); ObjectOutputStream out = new ObjectOutputStream(os)) {
            out.writeObject(deep);
        }
        assertThrows(LogiOptException.class, () -> new ShipmentSnapshotService(tempDir).load(nested));
        assertTrue(ShipmentSnapshotService.FILTER_PATTERN.endsWith("!*"));
        assertEquals(ObjectInputFilter.Status.REJECTED, filter.checkInput(new FilterInfoStub(String[].class, 0, 1)));
        assertEquals(ObjectInputFilter.Status.REJECTED, filter.checkInput(new FilterInfoStub(null, 6, 1)));
        assertEquals(ObjectInputFilter.Status.REJECTED, filter.checkInput(new FilterInfoStub(null, 1, 51)));
    }

    /** Minimal FilterInfo for checking the filter limits directly. */
    private static final class FilterInfoStub implements ObjectInputFilter.FilterInfo {
        private final Class<?> clazz;
        private final long depth;
        private final long references;

        FilterInfoStub(Class<?> clazz, long depth, long references) {
            this.clazz = clazz;
            this.depth = depth;
            this.references = references;
        }

        @Override
        public Class<?> serialClass() {
            return clazz;
        }

        @Override
        public long arrayLength() {
            return -1;
        }

        @Override
        public long depth() {
            return depth;
        }

        @Override
        public long references() {
            return references;
        }

        @Override
        public long streamBytes() {
            return 0;
        }
    }

    @Test
    void foreignObjectRejected() throws Exception {
        Path file = tempDir.resolve("other.ser");
        try (OutputStream os = Files.newOutputStream(file); ObjectOutputStream out = new ObjectOutputStream(os)) {
            out.writeObject(new ArrayList<String>());
        }
        assertThrows(LogiOptException.class, () -> new ShipmentSnapshotService(tempDir).load(file));
    }
}

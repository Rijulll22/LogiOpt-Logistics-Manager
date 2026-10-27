package com.logiopt.report;

import com.logiopt.exception.InvalidShipmentException;
import com.logiopt.exception.LogiOptException;
import com.logiopt.model.Shipment;
import com.logiopt.service.ShipmentValidator;
import com.logiopt.util.Strings;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;

/**
 * Auxiliary serialization of Shipment snapshots with ObjectOutputStream/ObjectInputStream.
 * MySQL remains the primary store. Loading accepts only Shipment and its field types.
 */
public class ShipmentSnapshotService {

    /** System property naming the snapshots directory. */
    public static final String DIR_PROPERTY = "logiopt.snapshots.dir";
    /** Environment variable naming the snapshots directory. */
    public static final String DIR_ENV = "LOGIOPT_SNAPSHOTS_DIR";
    /** Default snapshots directory, relative to the working directory. */
    public static final String DEFAULT_DIR = "snapshots";

    /** Largest snapshot file accepted, in bytes; a real Shipment snapshot is well under 1 KB. */
    public static final long MAX_SNAPSHOT_BYTES = 4096;

    /**
     * Exact class allow-list (LocalDate is serialized through java.time.Ser) plus limits on depth,
     * object references, stream size and array length. Everything else is rejected.
     */
    static final String FILTER_PATTERN = "maxdepth=5;maxrefs=50;maxbytes=" + MAX_SNAPSHOT_BYTES + ";maxarray=16;"
            + "com.logiopt.model.Shipment;com.logiopt.model.CargoType;com.logiopt.model.ShipmentStatus;"
            + "com.logiopt.model.OptimisationPreference;"
            + "java.lang.Enum;java.time.Ser;java.time.LocalDate;!*";

    private static final ObjectInputFilter FILTER = ObjectInputFilter.Config.createFilter(FILTER_PATTERN);

    private final Path snapshotsDir;
    private final ShipmentValidator validator;

    /**
     * Creates the service with a default validator.
     *
     * @param snapshotsDir target directory (created on demand)
     */
    public ShipmentSnapshotService(Path snapshotsDir) {
        this(snapshotsDir, new ShipmentValidator(Clock.systemDefaultZone()));
    }

    /**
     * Creates the service.
     *
     * @param snapshotsDir target directory (created on demand)
     * @param validator    validator applied to every loaded shipment
     */
    public ShipmentSnapshotService(Path snapshotsDir, ShipmentValidator validator) {
        if (snapshotsDir == null || validator == null) {
            throw new IllegalArgumentException("Snapshots directory and validator are required");
        }
        this.snapshotsDir = snapshotsDir;
        this.validator = validator;
    }

    /**
     * Resolves the snapshots directory: system property, then environment variable, then "snapshots".
     *
     * @return snapshots directory
     */
    public static Path configuredDirectory() {
        String value = System.getProperty(DIR_PROPERTY);
        if (Strings.isBlank(value)) {
            value = System.getenv(DIR_ENV);
        }
        return Paths.get(Strings.isBlank(value) ? DEFAULT_DIR : value.trim());
    }

    /**
     * Serializes a shipment to shipment-&lt;id&gt;.ser, replacing an older snapshot.
     *
     * @param shipment shipment to save
     * @return written file
     * @throws LogiOptException if writing fails
     */
    public Path save(Shipment shipment) throws LogiOptException {
        Path file = snapshotsDir.resolve("shipment-" + shipment.getShipmentId() + ".ser");
        try {
            Files.createDirectories(snapshotsDir);
            try (OutputStream os = Files.newOutputStream(file);
                 ObjectOutputStream out = new ObjectOutputStream(os)) {
                out.writeObject(shipment);
            }
            return file;
        } catch (IOException e) {
            throw new LogiOptException("Snapshot could not be written", e);
        }
    }

    /**
     * Reads a shipment snapshot through the allow-list filter and revalidates it: ids must be
     * positive and the shipment must pass the saved-shipment rules (the dispatch date may be past).
     *
     * @param file snapshot file
     * @return shipment
     * @throws LogiOptException if reading fails, the file is too large or holds something else, or
     *                          the shipment is invalid
     */
    public Shipment load(Path file) throws LogiOptException {
        Object value;
        try {
            if (Files.size(file) > MAX_SNAPSHOT_BYTES) {
                throw new LogiOptException("Snapshot is larger than " + MAX_SNAPSHOT_BYTES + " bytes");
            }
            try (InputStream is = Files.newInputStream(file);
                 ObjectInputStream in = new ObjectInputStream(is)) {
                in.setObjectInputFilter(FILTER);
                value = in.readObject();
                if (in.read() != -1) {
                    throw new LogiOptException("Snapshot has trailing data");
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            throw new LogiOptException("Snapshot could not be read", e);
        }
        if (!(value instanceof Shipment)) {
            throw new LogiOptException("Snapshot does not contain a shipment");
        }
        Shipment shipment = (Shipment) value;
        if (shipment.getShipmentId() <= 0 || shipment.getUserId() <= 0 || shipment.getStatus() == null) {
            throw new LogiOptException("Snapshot has an invalid shipment id, user id or status");
        }
        try {
            validator.validateForPlanning(shipment);
        } catch (InvalidShipmentException e) {
            throw new LogiOptException("Snapshot holds an invalid shipment: " + String.join(" ", e.getErrors()), e);
        }
        return shipment;
    }
}

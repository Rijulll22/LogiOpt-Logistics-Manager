package com.logiopt.dao;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Argument handling of the migration command. Every case here is refused before any connection.
 */
class MigrationToolTest {

    private final ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
    private final PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
    private final PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8);

    private String err() {
        return errBytes.toString(StandardCharsets.UTF_8);
    }

    @Test
    void targetIsRequired() {
        assertEquals(MigrationTool.USAGE, MigrationTool.run(new String[0], out, err));
        assertTrue(err().contains("Usage"));
    }

    @Test
    void unknownArgumentRejected() {
        assertEquals(MigrationTool.USAGE, MigrationTool.run(new String[] {"--target=test", "--force"}, out, err));
    }

    @Test
    void mainTargetRequiresMatchingDatabaseConfirmation() {
        assertEquals(MigrationTool.USAGE, MigrationTool.run(new String[] {"--target=main"}, out, err));
        assertEquals(MigrationTool.USAGE, MigrationTool.run(
                new String[] {"--target=main", "--confirm-database=certainly_not_the_configured_db"}, out, err));
        assertTrue(err().contains("Refusing"), err());
        assertEquals("", outBytes.toString(StandardCharsets.UTF_8));
    }
}

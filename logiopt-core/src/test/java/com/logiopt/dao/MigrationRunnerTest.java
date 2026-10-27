package com.logiopt.dao;

import com.logiopt.exception.DataAccessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Migration script parsing, naming, checksums, version checks and baseline comparison without a database.
 */
class MigrationRunnerTest {

    @Test
    void splitsOnLineEndingSemicolonsAndSkipsComments() {
        String script = "-- header\r\nCREATE TABLE a (\r\n  id INT -- not a statement end\r\n);\n\n"
                + "-- between\nINSERT INTO a VALUES (1),\n-- inline comment line\n(2);\nSET @x = 'a;b';\n";
        List<String> statements = MigrationRunner.splitStatements(script);
        assertEquals(3, statements.size(), statements.toString());
        assertTrue(statements.get(0).startsWith("CREATE TABLE a ("));
        assertFalse(statements.get(0).endsWith(";"));
        assertEquals("INSERT INTO a VALUES (1),\n(2)", statements.get(1));
        assertEquals("SET @x = 'a;b'", statements.get(2));
    }

    @Test
    void unterminatedStatementRejected() {
        assertThrows(IllegalArgumentException.class, () -> MigrationRunner.splitStatements("SELECT 1"));
    }

    @Test
    void checksumIgnoresLineEndingStyle() {
        assertEquals(MigrationRunner.checksum("a\nb\n"), MigrationRunner.checksum("a\r\nb\r\n"));
        assertNotEquals(MigrationRunner.checksum("a\nb\n"), MigrationRunner.checksum("a\nc\n"));
        assertEquals(64, MigrationRunner.checksum("x").length());
    }

    @ParameterizedTest
    @CsvSource({"26.10.0, true", "8.0.19, true", "8.0.36-log, true", "8.4.0-commercial, true", "9.1.0, true",
            "8.0.18, false", "8.0.16, false", "5.7.44, false", "MariaDB, false"})
    void serverVersionCheck(String version, boolean supported) {
        assertEquals(supported, MigrationRunner.isSupportedServerVersion(version));
    }

    @Test
    void nullServerVersionUnsupported() {
        assertFalse(MigrationRunner.isSupportedServerVersion(null));
    }

    @Test
    void applicationScriptsLoadInOrderAndSplitCleanly() throws Exception {
        List<MigrationRunner.Migration> migrations = new MigrationRunner(
                DBConnection.fromTestConfiguration(Map.of(DBConnection.ENV_TEST_URL,
                        "jdbc:mysql://127.0.0.1:1/unused_test"))).getMigrations();
        assertEquals(10, migrations.size());
        for (int i = 0; i < migrations.size(); i++) {
            assertEquals(i + 1, migrations.get(i).version);
            assertFalse(MigrationRunner.splitStatements(migrations.get(i).sql).isEmpty());
        }
        List<String> v1 = MigrationRunner.splitStatements(migrations.get(0).sql);
        assertEquals(9, v1.size());
        for (String statement : v1) {
            assertTrue(statement.startsWith("CREATE TABLE "), statement);
            assertFalse(statement.toUpperCase().contains("IF NOT EXISTS"), statement);
        }
        for (MigrationRunner.Migration m : migrations) {
            for (String statement : MigrationRunner.splitStatements(m.sql)) {
                String upper = statement.toUpperCase();
                assertFalse(upper.startsWith("USE ") || upper.contains("CREATE DATABASE")
                        || upper.contains("DROP DATABASE") || upper.contains("DELETE FROM"), m.script + ": " + statement);
            }
        }
    }

    @Test
    void misnamedOrMissingScriptsRejected() {
        assertThrows(DataAccessException.class, () -> MigrationRunner.load("/db/migration/baseline.sql"));
        assertThrows(DataAccessException.class, () -> MigrationRunner.load("/db/migration/V9__does_not_exist.sql"));
    }

    @Test
    void versionGapsRejected() throws Exception {
        MigrationRunner.Migration v1 = MigrationRunner.load(MigrationRunner.SCRIPTS.get(0));
        MigrationRunner.Migration v3 = MigrationRunner.load(MigrationRunner.SCRIPTS.get(2));
        assertThrows(DataAccessException.class, () -> MigrationRunner.checkOrder(List.of(v1, v3)));
        assertThrows(DataAccessException.class, () -> MigrationRunner.checkOrder(List.of(v3)));
    }

    @Test
    void testOnlyListsMayUseASeparateHighVersionButMustStartAtOneAndIncrease() throws Exception {
        MigrationRunner.Migration v1 = MigrationRunner.load(MigrationRunner.SCRIPTS.get(0));
        MigrationRunner.Migration v2 = MigrationRunner.load(MigrationRunner.SCRIPTS.get(1));
        MigrationRunner.Migration v999 = MigrationRunner.load("/db/test-migration/V999__intentionally_failing.sql");
        MigrationRunner.checkAscending(List.of(v1, v2, v999));
        assertThrows(DataAccessException.class, () -> MigrationRunner.checkAscending(List.of(v2, v999)));
        assertThrows(DataAccessException.class, () -> MigrationRunner.checkAscending(List.of(v1, v999, v2)));
        assertThrows(DataAccessException.class, () -> MigrationRunner.checkOrder(List.of(v1, v2, v999)),
                "the application list itself stays without gaps");
        List<String> withTestScript = new ArrayList<>(MigrationRunner.SCRIPTS);
        withTestScript.add("/db/test-migration/V999__intentionally_failing.sql");
        MigrationRunner runner = new MigrationRunner(DBConnection.fromTestConfiguration(Map.of(DBConnection.ENV_TEST_URL,
                "jdbc:mysql://127.0.0.1:1/unused_test")), withTestScript);
        assertEquals(999, runner.requiredVersion());
    }

    @Test
    void expectedBaselineMatchesItself() {
        assertEquals(List.of(), BaselineSchema.compare(BaselineSchema.expected()));
    }

    @Test
    void baselineComparisonReportsStructuralDifferences() {
        BaselineSchema.Snapshot s = BaselineSchema.expected();
        s.columns.get("shipments").put("weight_kg", "decimal(10,2) NOT NULL");
        s.columns.get("users").remove("enabled");
        s.columns.get("routes").put("legacy_flag", "int NULL");
        s.foreignKeys.remove("bookings.route_id -> routes.route_id");
        s.foreignKeys.remove("route_legs.route_id -> routes.route_id ON DELETE CASCADE");
        s.foreignKeys.add("route_legs.route_id -> routes.route_id");
        s.checks.remove("chk_shipments_weight");
        s.uniqueKeys.remove("users(email)");
        List<String> diffs = BaselineSchema.compare(s);
        assertEquals(List.of(
                "users.enabled: missing (expected tinyint(1) NOT NULL)",
                "shipments.weight_kg: expected decimal(12,3) NOT NULL, found decimal(10,2) NOT NULL",
                "routes.legacy_flag: unexpected column (int NULL)",
                "missing unique key users(email)",
                "missing foreign key bookings.route_id -> routes.route_id",
                "missing foreign key route_legs.route_id -> routes.route_id ON DELETE CASCADE",
                "missing check constraint chk_shipments_weight"), diffs);
    }

    @Test
    void missingRouteLegsUniqueKeyIsAllowedBecauseV2AddsIt() {
        BaselineSchema.Snapshot s = BaselineSchema.expected();
        s.uniqueKeys.remove(BaselineSchema.OPTIONAL_UNIQUE_KEY);
        assertEquals(List.of(), BaselineSchema.compare(s));
    }

    @Test
    void missingTableReported() {
        BaselineSchema.Snapshot s = BaselineSchema.expected();
        s.columns.remove("reports");
        assertTrue(BaselineSchema.compare(s).contains("table reports is missing"));
    }
}

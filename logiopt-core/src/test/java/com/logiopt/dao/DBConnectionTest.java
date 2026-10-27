package com.logiopt.dao;

import com.logiopt.exception.DataAccessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Database-name parsing and the _test guard; no database is contacted.
 */
class DBConnectionTest {

    @ParameterizedTest
    @CsvSource({"jdbc:mysql://localhost:3306/logiopt, logiopt",
            "jdbc:mysql://localhost:3306/logiopt_test?useSSL=false, logiopt_test",
            "jdbc:mysql://127.0.0.1/logiopt_test, logiopt_test",
            "'jdbc:mysql://h1:3306,h2:3306/x_test;a=b', x_test",
            "jdbc:mysql://localhost:3306/logiopt?db=logiopt_test, logiopt"})
    void databaseNameFromUrl(String url, String expected) {
        assertEquals(expected, DBConnection.databaseName(url));
    }

    @Test
    void urlWithoutDatabaseHasNoName() {
        assertNull(DBConnection.databaseName("jdbc:mysql://localhost:3306/"));
        assertNull(DBConnection.databaseName(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"jdbc:mysql://localhost:3306/logiopt", "jdbc:mysql://localhost:3306/logiopt?x=_test",
            "jdbc:mysql://localhost:3306/logiopt_testing", "jdbc:mysql://localhost:3306/"})
    void testConfigurationRefusesNonTestDatabases(String url) {
        DataAccessException ex = assertThrows(DataAccessException.class,
                () -> DBConnection.fromTestConfiguration(Map.of(DBConnection.ENV_TEST_URL, url)));
        assertTrue(ex.getMessage().startsWith("Refusing"), ex.getMessage());
    }

    @Test
    void testConfigurationNeverFallsBackToTheMainDatabase() {
        assertThrows(DataAccessException.class, () -> DBConnection.fromTestConfiguration(
                Map.of(DBConnection.ENV_URL, "jdbc:mysql://localhost:3306/logiopt")));
    }

    @Test
    void testConfigurationAcceptsTestDatabase() throws Exception {
        DBConnection db = DBConnection.fromTestConfiguration(
                Map.of(DBConnection.ENV_TEST_URL, "jdbc:mysql://localhost:3306/logiopt_test"));
        assertEquals("logiopt_test", db.getDatabaseName());
    }
}

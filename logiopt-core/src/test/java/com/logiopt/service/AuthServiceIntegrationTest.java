package com.logiopt.service;

import com.logiopt.dao.DBConnection;
import com.logiopt.dao.MigrationRunner;
import com.logiopt.dao.TestDatabase;
import com.logiopt.dao.UserDAO;
import com.logiopt.exception.InvalidRegistrationException;
import com.logiopt.model.Role;
import com.logiopt.model.User;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Registration, sign-in, throttling, password storage and admin seeding against the disposable
 * test database (LOGIOPT_TEST_DB_URL, name ending in _test). Uses the minimum iteration count to
 * keep the run short; the application uses {@link PasswordHasher#DEFAULT_ITERATIONS}.
 */
class AuthServiceIntegrationTest {

    private static final String IP = "192.0.2.20";
    private static final String PASSWORD = "s3cret-pass";

    private static DBConnection db;

    private final LoginThrottleTest.MutableClock clock = new LoginThrottleTest.MutableClock();
    private UserDAO users;
    private AuthService auth;

    @BeforeAll
    static void connect() throws Exception {
        db = TestDatabase.connect();
        TestDatabase.dropAllTables(db);
        new MigrationRunner(db).migrate();
    }

    @BeforeEach
    void cleanUsers() throws Exception {
        TestDatabase.execute(db, "DELETE FROM shipments WHERE user_id <> 1", "DELETE FROM users WHERE user_id <> 1");
        users = new UserDAO(db);
        auth = service(PasswordHasher.MIN_ITERATIONS);
    }

    private AuthService service(int iterations) {
        return new AuthService(users, new PasswordHasher(iterations, new SecureRandom()),
                new LoginThrottle(clock, 5, Duration.ofMinutes(5), Duration.ofMinutes(5)));
    }

    private static long count(String where) throws Exception {
        return TestDatabase.queryLong(db, "SELECT COUNT(*) FROM users WHERE " + where);
    }

    @Test
    void registeredCustomerCanSignInWithNormalizedEmail() throws Exception {
        auth.register("  Alice@Example.TEST ", " Alice ", PASSWORD.toCharArray());
        assertEquals(1, count("email = 'alice@example.test' AND name = 'Alice' AND role = 'CUSTOMER' AND enabled"));
        AuthService.LoginResult result = auth.login("ALICE@example.test", PASSWORD.toCharArray(), IP);
        assertEquals(AuthService.LoginStatus.SUCCESS, result.getStatus());
        assertEquals(Role.CUSTOMER, result.getUser().getRole());
        assertEquals("alice@example.test", result.getUser().getEmail());
    }

    @Test
    void duplicateRegistrationIsSilentlyIgnoredAndChangesNothing() throws Exception {
        auth.register("bob@example.test", "Bob", PASSWORD.toCharArray());
        auth.register("BOB@example.test", "Mallory", "other-password".toCharArray());
        assertEquals(1, count("email = 'bob@example.test'"));
        assertEquals(1, count("email = 'bob@example.test' AND name = 'Bob'"));
        assertEquals(AuthService.LoginStatus.SUCCESS, auth.login("bob@example.test", PASSWORD.toCharArray(), IP).getStatus());
        assertEquals(AuthService.LoginStatus.INVALID,
                auth.login("bob@example.test", "other-password".toCharArray(), IP).getStatus());
    }

    @Test
    void reservedAdminEmailCannotBeRegistered() throws Exception {
        auth.register(" Admin@LogiOpt.local", "Not admin", PASSWORD.toCharArray());
        assertEquals(0, count("email = 'admin@logiopt.local'"));
    }

    @Test
    void invalidRegistrationReportsAllErrorsAndStoresNothing() throws Exception {
        InvalidRegistrationException ex = assertThrows(InvalidRegistrationException.class,
                () -> auth.register("not-an-email", " ", "short".toCharArray()));
        assertEquals(List.of("Enter a valid email address.", "Name is required.",
                "Password must be at least 8 characters."), ex.getErrors());
        assertThrows(InvalidRegistrationException.class, () -> auth.register("x@example.test", "X", null));
        assertThrows(InvalidRegistrationException.class,
                () -> auth.register("x@example.test", "X", "a".repeat(129).toCharArray()));
        assertEquals(0, count("user_id <> 1"));
        auth.register("eight@example.test", "Eight", "12345678".toCharArray());
        assertEquals(1, count("email = 'eight@example.test'"));
    }

    @Test
    void passwordIsStoredOnlyAsSaltedPbkdf2() throws Exception {
        auth.register("p1@example.test", "P1", PASSWORD.toCharArray());
        auth.register("p2@example.test", "P2", PASSWORD.toCharArray());
        String row = TestDatabase.queryString(db, "SELECT CONCAT_WS('|', password_hash, password_salt, "
                + "password_iterations, password_algorithm) FROM users WHERE email = 'p1@example.test'");
        assertFalse(row.contains(PASSWORD), row);
        String[] parts = row.split("\\|");
        assertEquals(4, parts.length);
        assertEquals(32, java.util.Base64.getDecoder().decode(parts[0]).length);
        assertEquals(16, java.util.Base64.getDecoder().decode(parts[1]).length);
        assertEquals(String.valueOf(PasswordHasher.MIN_ITERATIONS), parts[2]);
        assertEquals(PasswordHasher.ALGORITHM, parts[3]);
        assertEquals(0, count("password_hash LIKE '%" + PASSWORD + "%' OR name LIKE '%" + PASSWORD + "%'"));
        assertEquals(1, TestDatabase.queryLong(db, "SELECT COUNT(DISTINCT password_salt) = 2 AND "
                + "COUNT(DISTINCT password_hash) = 2 FROM users WHERE email IN ('p1@example.test', 'p2@example.test')"));
    }

    @Test
    void everyFailureLooksTheSame() throws Exception {
        auth.register("carol@example.test", "Carol", PASSWORD.toCharArray());
        assertEquals(AuthService.LoginStatus.INVALID, auth.login("carol@example.test", "wrong-pass".toCharArray(), IP).getStatus());
        assertEquals(AuthService.LoginStatus.INVALID, auth.login("nobody@example.test", PASSWORD.toCharArray(), IP).getStatus());
        assertEquals(AuthService.LoginStatus.INVALID, auth.login("demo@logiopt.invalid", PASSWORD.toCharArray(), IP).getStatus());
        assertEquals(AuthService.LoginStatus.INVALID, auth.login("", PASSWORD.toCharArray(), IP).getStatus());
        assertNull(auth.login("carol@example.test", null, IP).getUser());
        TestDatabase.execute(db, "UPDATE users SET enabled = FALSE WHERE email = 'carol@example.test'");
        assertEquals(AuthService.LoginStatus.INVALID, auth.login("carol@example.test", PASSWORD.toCharArray(), IP).getStatus());
    }

    @Test
    void fiveFailuresLockThatEmailAndAddressForFiveMinutes() throws Exception {
        auth.register("dave@example.test", "Dave", PASSWORD.toCharArray());
        for (int i = 1; i <= 4; i++) {
            assertEquals(AuthService.LoginStatus.INVALID, auth.login("dave@example.test", "bad".toCharArray(), IP).getStatus());
        }
        assertEquals(AuthService.LoginStatus.LOCKED, auth.login("dave@example.test", "bad".toCharArray(), IP).getStatus());
        assertEquals(AuthService.LoginStatus.LOCKED, auth.login("dave@example.test", PASSWORD.toCharArray(), IP).getStatus());
        assertEquals(AuthService.LoginStatus.SUCCESS,
                auth.login("dave@example.test", PASSWORD.toCharArray(), "198.51.100.3").getStatus());
        clock.advance(Duration.ofMinutes(5));
        assertEquals(AuthService.LoginStatus.SUCCESS, auth.login("dave@example.test", PASSWORD.toCharArray(), IP).getStatus());
        for (int i = 0; i < 5; i++) {
            auth.login("nobody-at-all@example.test", "bad".toCharArray(), IP);
        }
        assertEquals(AuthService.LoginStatus.LOCKED,
                auth.login("nobody-at-all@example.test", "bad".toCharArray(), IP).getStatus(), "unknown emails lock too");
    }

    @Test
    void weakerStoredHashIsUpgradedAfterSignIn() throws Exception {
        auth.register("erin@example.test", "Erin", PASSWORD.toCharArray());
        AuthService stronger = service(PasswordHasher.MIN_ITERATIONS + 20_000);
        assertEquals(AuthService.LoginStatus.SUCCESS, stronger.login("erin@example.test", PASSWORD.toCharArray(), IP).getStatus());
        assertEquals(PasswordHasher.MIN_ITERATIONS + 20_000, TestDatabase.queryLong(db,
                "SELECT password_iterations FROM users WHERE email = 'erin@example.test'"));
        assertEquals(AuthService.LoginStatus.SUCCESS, stronger.login("erin@example.test", PASSWORD.toCharArray(), IP).getStatus());
    }

    @Test
    void adminIsSeededOnlyFromTheConfiguredPassword() throws Exception {
        assertEquals(AuthService.AdminSeedResult.SKIPPED_NOT_CONFIGURED, auth.seedAdmin(null));
        assertEquals(AuthService.AdminSeedResult.SKIPPED_NOT_CONFIGURED, auth.seedAdmin("  "));
        assertEquals(AuthService.AdminSeedResult.SKIPPED_INVALID_PASSWORD, auth.seedAdmin("short"));
        assertEquals(0, count("role = 'ADMIN'"));

        assertEquals(AuthService.AdminSeedResult.CREATED, auth.seedAdmin("admin-pass-1"));
        assertEquals(1, count("role = 'ADMIN' AND email = 'admin@logiopt.local' AND enabled"));
        assertEquals(AuthService.AdminSeedResult.UNCHANGED, auth.seedAdmin("admin-pass-1"));
        User admin = auth.login("admin@logiopt.local", "admin-pass-1".toCharArray(), IP).getUser();
        assertTrue(admin.isAdmin());

        assertEquals(AuthService.AdminSeedResult.UPDATED, auth.seedAdmin("admin-pass-2"));
        assertEquals(AuthService.LoginStatus.INVALID,
                auth.login("admin@logiopt.local", "admin-pass-1".toCharArray(), IP).getStatus());
        assertEquals(AuthService.LoginStatus.SUCCESS,
                auth.login("admin@logiopt.local", "admin-pass-2".toCharArray(), IP).getStatus());
        assertEquals(1, count("role = 'ADMIN'"));
    }

    @Test
    void secondAdministratorIsImpossible() throws Exception {
        auth.seedAdmin("admin-pass-1");
        assertTrue(users.insert("other-admin@example.test", "Other", new PasswordHasher(PasswordHasher.MIN_ITERATIONS,
                new SecureRandom()).hash(PASSWORD.toCharArray()), Role.ADMIN).isEmpty(), "uk_users_single_admin");
        TestDatabase.execute(db, "DELETE FROM users WHERE role = 'ADMIN'");
        users.insert("other-admin@example.test", "Other", new PasswordHasher(PasswordHasher.MIN_ITERATIONS,
                new SecureRandom()).hash(PASSWORD.toCharArray()), Role.ADMIN);
        assertEquals(AuthService.AdminSeedResult.SKIPPED_CONFLICT, auth.seedAdmin("admin-pass-1"));
        assertEquals(0, count("email = 'admin@logiopt.local'"));
    }

    @Test
    void adminEmailOwnedByACustomerIsNotTakenOver() throws Exception {
        users.insert("admin@logiopt.local", "Squatter", new PasswordHasher(PasswordHasher.MIN_ITERATIONS,
                new SecureRandom()).hash(PASSWORD.toCharArray()), Role.CUSTOMER);
        assertEquals(AuthService.AdminSeedResult.SKIPPED_CONFLICT, auth.seedAdmin("admin-pass-1"));
        assertEquals(0, count("role = 'ADMIN'"));
    }

    @Test
    void onlyTheAdministratorCanListUsers() throws Exception {
        auth.register("frank@example.test", "Frank", PASSWORD.toCharArray());
        User customer = auth.login("frank@example.test", PASSWORD.toCharArray(), IP).getUser();
        assertThrows(SecurityException.class, () -> auth.listUsers(customer));
        assertThrows(SecurityException.class, () -> auth.listUsers(null));
        auth.seedAdmin("admin-pass-1");
        User admin = auth.login("admin@logiopt.local", "admin-pass-1".toCharArray(), IP).getUser();
        assertEquals(3, auth.listUsers(admin).size());
    }
}

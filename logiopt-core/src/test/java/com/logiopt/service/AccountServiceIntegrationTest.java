package com.logiopt.service;

import com.logiopt.dao.DBConnection;
import com.logiopt.dao.MigrationRunner;
import com.logiopt.dao.TestDatabase;
import com.logiopt.dao.UserDAO;
import com.logiopt.exception.AccountChangeException;
import com.logiopt.model.AccountProfile;
import com.logiopt.model.Role;
import com.logiopt.model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The signed-in user's own account: profile, name change and password change against the disposable
 * test database (LOGIOPT_TEST_DB_URL, name ending in _test). Uses the minimum PBKDF2 iteration count
 * to keep the run short.
 */
class AccountServiceIntegrationTest {

    private static final String IP = "desktop-client";
    private static final String PASSWORD = "old-pass-123";
    private static final String NEW_PASSWORD = "new-pass-456";

    private static DBConnection db;

    private final LoginThrottleTest.MutableClock clock = new LoginThrottleTest.MutableClock();
    private final List<String> logged = new ArrayList<>();
    private final Handler capture = new Handler() {
        @Override
        public void publish(LogRecord record) {
            logged.add(String.valueOf(record.getMessage()));
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    };
    private AuthService auth;
    private User carol;

    @BeforeAll
    static void connect() throws Exception {
        db = TestDatabase.connect();
        TestDatabase.dropAllTables(db);
        new MigrationRunner(db).migrate();
    }

    @BeforeEach
    void freshUser() throws Exception {
        TestDatabase.execute(db, "DELETE FROM shipments WHERE user_id <> 1", "DELETE FROM users WHERE user_id <> 1");
        auth = new AuthService(new UserDAO(db), new PasswordHasher(PasswordHasher.MIN_ITERATIONS, new SecureRandom()),
                new LoginThrottle(clock, 5, Duration.ofMinutes(5), Duration.ofMinutes(5)));
        auth.register("carol@example.test", "Carol Clark", PASSWORD.toCharArray());
        carol = auth.login("carol@example.test", PASSWORD.toCharArray(), IP).getUser();
        Logger.getLogger(AuthService.class.getName()).addHandler(capture);
    }

    @AfterEach
    void stopCapture() {
        Logger.getLogger(AuthService.class.getName()).removeHandler(capture);
        for (String message : logged) {
            assertFalse(message.contains(PASSWORD) || message.contains(NEW_PASSWORD), "password in the log: " + message);
        }
    }

    private void change(String current, String fresh, String confirm) throws Exception {
        auth.changePassword(carol, current == null ? null : current.toCharArray(), fresh.toCharArray(),
                confirm.toCharArray(), IP);
    }

    private String storedHash() throws Exception {
        return TestDatabase.queryString(db, "SELECT password_hash FROM users WHERE email = 'carol@example.test'");
    }

    @Test
    void profileShowsTheUsersOwnDetailsWithoutPasswordData() throws Exception {
        AccountProfile profile = auth.profile(carol);
        assertEquals(carol.getUserId(), profile.getUserId());
        assertEquals("carol@example.test", profile.getEmail());
        assertEquals("Carol Clark", profile.getName());
        assertEquals(Role.CUSTOMER, profile.getRole());
        assertNotNull(profile.getCreatedAt());
        assertTrue(profile.getCreatedAt().isAfter(LocalDateTime.now().minusDays(2)));
    }

    @Test
    void nameChangeFollowsTheRegistrationRulesAndReturnsTheUpdatedUser() throws Exception {
        AccountChangeException empty = assertThrows(AccountChangeException.class, () -> auth.changeName(carol, "   "));
        assertEquals(List.of("Name is required."), empty.getErrors());
        assertThrows(AccountChangeException.class, () -> auth.changeName(carol, "x".repeat(101)));
        assertThrows(AccountChangeException.class, () -> auth.changeName(carol, "Bad\u0007Name"));
        User renamed = auth.changeName(carol, "  Carol C. Clark ");
        assertEquals("Carol C. Clark", renamed.getName());
        assertEquals(carol.getUserId(), renamed.getUserId());
        assertEquals(carol.getEmail(), renamed.getEmail());
        assertEquals("Carol C. Clark", auth.profile(renamed).getName());
    }

    @Test
    void wrongCurrentPasswordIsRefusedGenericallyAndChangesNothing() throws Exception {
        String before = storedHash();
        AccountChangeException ex = assertThrows(AccountChangeException.class, () -> change("not-my-password", NEW_PASSWORD,
                NEW_PASSWORD));
        assertEquals(List.of(AuthService.WRONG_CURRENT_PASSWORD), ex.getErrors());
        assertThrows(AccountChangeException.class, () -> change(null, NEW_PASSWORD, NEW_PASSWORD));
        assertEquals(before, storedHash());
        assertEquals(AuthService.LoginStatus.SUCCESS, auth.login("carol@example.test", PASSWORD.toCharArray(), "x").getStatus());
    }

    @Test
    void weakOrMismatchedNewPasswordIsRefusedBeforeTheCurrentOneIsChecked() throws Exception {
        String before = storedHash();
        AccountChangeException weak = assertThrows(AccountChangeException.class, () -> change(PASSWORD, "short", "short"));
        assertEquals(List.of("Password must be at least 8 characters."), weak.getErrors());
        AccountChangeException tooLong = assertThrows(AccountChangeException.class,
                () -> change(PASSWORD, "x".repeat(129), "x".repeat(129)));
        assertEquals(List.of("Password must be at most 128 characters."), tooLong.getErrors());
        AccountChangeException differ = assertThrows(AccountChangeException.class,
                () -> change(PASSWORD, NEW_PASSWORD, NEW_PASSWORD + "x"));
        assertEquals(List.of(AuthService.PASSWORDS_DIFFER), differ.getErrors());
        assertEquals(before, storedHash());
        for (int i = 0; i < 6; i++) {
            assertThrows(AccountChangeException.class, () -> change("wrong-but-weak-new", "short", "short"));
        }
        assertEquals(AuthService.LoginStatus.SUCCESS, auth.login("carol@example.test", PASSWORD.toCharArray(), IP).getStatus(),
                "weak-new refusals never verified the current password, so they do not count as failures");
    }

    @Test
    void successfulChangeKeepsTheSessionAndOnlyTheNewPasswordSignsIn() throws Exception {
        String before = storedHash();
        char[] current = PASSWORD.toCharArray();
        char[] fresh = NEW_PASSWORD.toCharArray();
        char[] confirm = NEW_PASSWORD.toCharArray();
        auth.changePassword(carol, current, fresh, confirm, IP);
        for (char[] cleared : List.of(current, fresh, confirm)) {
            assertTrue(new String(cleared).chars().allMatch(c -> c == 0), "password arrays are cleared");
        }
        assertNotEquals(before, storedHash());
        assertEquals(PasswordHasher.ALGORITHM, TestDatabase.queryString(db,
                "SELECT password_algorithm FROM users WHERE email = 'carol@example.test'"));
        assertEquals(PasswordHasher.MIN_ITERATIONS, TestDatabase.queryLong(db,
                "SELECT password_iterations FROM users WHERE email = 'carol@example.test'"), "current PBKDF2 settings");
        assertEquals(AuthService.LoginStatus.SUCCESS,
                auth.login("carol@example.test", NEW_PASSWORD.toCharArray(), "other").getStatus());
        assertEquals(AuthService.LoginStatus.INVALID,
                auth.login("carol@example.test", PASSWORD.toCharArray(), "other").getStatus());
        assertEquals("Carol Clark", auth.profile(carol).getName(), "the old session object still works");
        assertTrue(logged.stream().anyMatch(m -> m.contains("changed their password")), logged.toString());
    }

    @Test
    void repeatedWrongCurrentPasswordsAreThrottledLikeSignIn() throws Exception {
        for (int i = 0; i < 4; i++) {
            AccountChangeException ex = assertThrows(AccountChangeException.class, () -> change("guess", NEW_PASSWORD,
                    NEW_PASSWORD));
            assertEquals(List.of(AuthService.WRONG_CURRENT_PASSWORD), ex.getErrors());
        }
        AccountChangeException fifth = assertThrows(AccountChangeException.class, () -> change("guess", NEW_PASSWORD,
                NEW_PASSWORD));
        assertEquals(List.of(AuthService.PASSWORD_LOCKED), fifth.getErrors());
        AccountChangeException locked = assertThrows(AccountChangeException.class, () -> change(PASSWORD, NEW_PASSWORD,
                NEW_PASSWORD));
        assertEquals(List.of(AuthService.PASSWORD_LOCKED), locked.getErrors(), "even the right password waits");
        assertEquals(AuthService.LoginStatus.LOCKED,
                auth.login("carol@example.test", PASSWORD.toCharArray(), IP).getStatus(), "the same throttle as sign-in");
        clock.advance(Duration.ofMinutes(6));
        change(PASSWORD, NEW_PASSWORD, NEW_PASSWORD);
        assertEquals(AuthService.LoginStatus.SUCCESS,
                auth.login("carol@example.test", NEW_PASSWORD.toCharArray(), IP).getStatus());
    }

    @Test
    void theAdministratorIsRefusedWithTheEnvironmentVariableMessage() throws Exception {
        assertEquals(AuthService.AdminSeedResult.CREATED, auth.seedAdmin("admin-pass-123"));
        User admin = auth.login(AuthService.ADMIN_EMAIL, "admin-pass-123".toCharArray(), IP).getUser();
        String before = TestDatabase.queryString(db, "SELECT password_hash FROM users WHERE role = 'ADMIN'");
        AccountChangeException ex = assertThrows(AccountChangeException.class, () -> auth.changePassword(admin,
                "admin-pass-123".toCharArray(), NEW_PASSWORD.toCharArray(), NEW_PASSWORD.toCharArray(), IP));
        assertEquals(List.of(AuthService.ADMIN_PASSWORD_FIXED), ex.getErrors());
        assertTrue(AuthService.ADMIN_PASSWORD_FIXED.contains("LOGIOPT_ADMIN_PASSWORD"));
        assertEquals(before, TestDatabase.queryString(db, "SELECT password_hash FROM users WHERE role = 'ADMIN'"));
        TestDatabase.execute(db, "DELETE FROM users WHERE role = 'ADMIN'");
    }

    @Test
    void onlyTheSignedInAccountChanges() throws Exception {
        auth.register("dave@example.test", "Dave", PASSWORD.toCharArray());
        String daveBefore = TestDatabase.queryString(db, "SELECT password_hash FROM users WHERE email = 'dave@example.test'");
        auth.changeName(carol, "Carol Only");
        change(PASSWORD, NEW_PASSWORD, NEW_PASSWORD);
        assertEquals("Dave", TestDatabase.queryString(db, "SELECT name FROM users WHERE email = 'dave@example.test'"));
        assertEquals(daveBefore, TestDatabase.queryString(db,
                "SELECT password_hash FROM users WHERE email = 'dave@example.test'"));
        User forged = new User(carol.getUserId(), "dave@example.test", "Dave", Role.CUSTOMER, true);
        assertThrows(Exception.class, () -> auth.changePassword(forged, PASSWORD.toCharArray(), "another-pass".toCharArray(),
                "another-pass".toCharArray(), IP), "a user object whose id and email disagree is refused");
        assertEquals(daveBefore, TestDatabase.queryString(db,
                "SELECT password_hash FROM users WHERE email = 'dave@example.test'"));
    }
}

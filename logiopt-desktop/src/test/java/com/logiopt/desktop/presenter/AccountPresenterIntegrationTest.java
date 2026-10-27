package com.logiopt.desktop.presenter;

import com.logiopt.app.AppServices;
import com.logiopt.app.CoreRuntime;
import com.logiopt.dao.DBConnection;
import com.logiopt.dao.MigrationRunner;
import com.logiopt.dao.TestDatabase;
import com.logiopt.desktop.presenter.Models.AccountModel;
import com.logiopt.model.User;
import com.logiopt.service.AuthService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Account presenter with a fake view and the real shared services against the disposable test
 * database (LOGIOPT_TEST_DB_URL, name ending in _test): profile, name change (the session gets the new
 * name), password change (wrong current, weak, mismatch, success, then sign-in with the new password)
 * and the administrator's refusal. No password ever reaches the view.
 */
class AccountPresenterIntegrationTest {

    private static final String PASSWORD = "account-pass-1";
    private static final String NEW_PASSWORD = "account-pass-2";
    private static final String ADMIN_PASSWORD = "account-admin-pass";

    private static DBConnection db;
    private static CoreRuntime runtime;
    private static AppServices services;

    private final Fakes.CheckingRunner runner = new Fakes.CheckingRunner();
    private final Fakes.AccountView view = new Fakes.AccountView();
    private final List<User> renamed = new ArrayList<>();
    private User erin;
    private String email;
    private static int sequence;

    @BeforeAll
    static void setUp() throws Exception {
        db = TestDatabase.connect();
        TestDatabase.dropAllTables(db);
        new MigrationRunner(db).migrate();
        Map<String, String> env = Map.of(AuthService.ENV_ADMIN_PASSWORD, ADMIN_PASSWORD);
        runtime = new CoreRuntime(db, env::get, Files.createTempDirectory("logiopt-account"), "cp");
        services = runtime.services();
        runtime.onSchemaReady();
    }

    @AfterAll
    static void stop() {
        if (runtime != null) {
            runtime.shutdown();
        }
    }

    @BeforeEach
    void freshCustomer() throws Exception {
        // A new address per test: the sign-in throttle is in memory and shared by the whole runtime.
        email = "erin" + (++sequence) + "@example.test";
        services.getAuthService().register(email, "Erin Evans", PASSWORD.toCharArray());
        erin = signIn(email, PASSWORD, "presenter-test");
        assertNotNull(erin);
    }

    private static User signIn(String email, String password, String client) throws Exception {
        AuthService.LoginResult result = services.getAuthService().login(email, password.toCharArray(), client);
        return result.getUser();
    }

    private AccountPresenter presenter(User user) {
        return new AccountPresenter(services.getAuthService(), runner, user, renamed::add, view);
    }

    @Test
    void profileIsShownForTheSignedInUser() {
        presenter(erin).load();
        AccountModel model = view.model;
        assertEquals("Erin Evans", model.name());
        assertEquals(email, model.email());
        assertEquals("Customer", model.role());
        assertTrue(model.canChangePassword());
        assertEquals(10, model.memberSince().length(), model.memberSince());
        assertEquals(1, runner.tasks, "loaded in the background");
    }

    @Test
    void nameChangeUpdatesTheSessionAndInvalidNamesShowInlineErrors() throws Exception {
        AccountPresenter p = presenter(erin);
        p.load();
        p.saveName("   ");
        assertEquals(List.of("Name is required."), view.nameErrors);
        assertTrue(renamed.isEmpty());
        p.saveName("Erin E. Evans");
        assertEquals(AccountPresenter.NAME_SAVED, view.nameNotice);
        assertEquals(1, renamed.size());
        assertEquals("Erin E. Evans", renamed.get(0).getName());
        assertEquals(erin.getUserId(), renamed.get(0).getUserId());
        assertEquals("Erin E. Evans", view.model.name());
        assertEquals("Erin E. Evans", TestDatabase.queryString(db, "SELECT name FROM users WHERE email = '" + email + "'"));
    }

    @Test
    void passwordChangeRefusesWrongWeakAndMismatchedThenSucceedsWithoutSigningOut() throws Exception {
        AccountPresenter p = presenter(erin);
        p.changePassword("wrong-current".toCharArray(), NEW_PASSWORD.toCharArray(), NEW_PASSWORD.toCharArray());
        assertEquals(Map.of(AccountPresenter.CURRENT, List.of(AuthService.WRONG_CURRENT_PASSWORD)), view.passwordErrors);
        p.changePassword(PASSWORD.toCharArray(), "short".toCharArray(), "short".toCharArray());
        assertEquals(Map.of(AccountPresenter.NEW, List.of("Password must be at least 8 characters.")), view.passwordErrors);
        p.changePassword(PASSWORD.toCharArray(), NEW_PASSWORD.toCharArray(), "something-else".toCharArray());
        assertEquals(Map.of(AccountPresenter.CONFIRM, List.of(AuthService.PASSWORDS_DIFFER)), view.passwordErrors);
        char[] current = PASSWORD.toCharArray();
        p.changePassword(current, NEW_PASSWORD.toCharArray(), NEW_PASSWORD.toCharArray());
        assertEquals(AccountPresenter.PASSWORD_CHANGED, view.passwordNotice);
        assertTrue(new String(current).chars().allMatch(c -> c == 0), "the password array is cleared");
        p.load();
        assertEquals("Erin Evans", view.model.name(), "still signed in: the same session user works");
        assertNotNull(signIn(email, NEW_PASSWORD, "other-client"));
        assertEquals(null, signIn(email, PASSWORD, "other-client"));
        for (String call : view.calls) {
            assertFalse(call.contains(PASSWORD) || call.contains(NEW_PASSWORD), "password shown: " + call);
        }
    }

    @Test
    void repeatedWrongPasswordsLockLikeSignIn() {
        AccountPresenter p = presenter(erin);
        for (int i = 0; i < 5; i++) {
            p.changePassword("guess".toCharArray(), NEW_PASSWORD.toCharArray(), NEW_PASSWORD.toCharArray());
        }
        assertEquals(Map.of(AccountPresenter.CURRENT, List.of(AuthService.PASSWORD_LOCKED)), view.passwordErrors);
    }

    @Test
    void theAdministratorSeesWhyThePasswordCannotBeChangedHere() throws Exception {
        User admin = signIn(AuthService.ADMIN_EMAIL, ADMIN_PASSWORD, "admin-client");
        AtomicReference<AccountModel> model = new AtomicReference<>();
        AccountPresenter p = presenter(admin);
        p.load();
        model.set(view.model);
        assertFalse(model.get().canChangePassword());
        assertEquals(AuthService.ADMIN_PASSWORD_FIXED, model.get().passwordNote());
        p.changePassword(ADMIN_PASSWORD.toCharArray(), NEW_PASSWORD.toCharArray(), NEW_PASSWORD.toCharArray());
        assertEquals(Map.of(AccountPresenter.GENERAL, List.of(AuthService.ADMIN_PASSWORD_FIXED)), view.passwordErrors);
        assertNotNull(signIn(AuthService.ADMIN_EMAIL, ADMIN_PASSWORD, "admin-client"), "unchanged");
    }
}

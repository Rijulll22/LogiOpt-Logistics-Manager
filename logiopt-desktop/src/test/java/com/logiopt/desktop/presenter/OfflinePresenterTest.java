package com.logiopt.desktop.presenter;

import com.logiopt.app.AppServices;
import com.logiopt.app.CoreRuntime;
import com.logiopt.dao.DBConnection;
import com.logiopt.desktop.support.ErrorMessages;
import com.logiopt.model.Role;
import com.logiopt.model.User;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Presenters with the database unreachable (a closed local port): every screen shows its friendly
 * error state, and no message reveals the URL or an exception. Needs no database and no display.
 */
class OfflinePresenterTest {

    private static final User CUSTOMER = new User(5, "c@example.test", "Cara", Role.CUSTOMER, true);
    private static CoreRuntime runtime;
    private static AppServices services;
    private final Fakes.CheckingRunner runner = new Fakes.CheckingRunner();

    @BeforeAll
    static void offline() throws Exception {
        runtime = new CoreRuntime(new DBConnection("jdbc:mysql://127.0.0.1:1/offline_test?connectTimeout=1000",
                "nobody", "not-a-secret"), name -> null, Files.createTempDirectory("logiopt-offline"), "cp");
        services = runtime.services();
    }

    @AfterAll
    static void stop() {
        runtime.shutdown();
    }

    @Test
    void dashboardShowsTheSameErrorStateAsTheWeb() {
        Fakes.DashboardView view = new Fakes.DashboardView();
        new DashboardPresenter(services.getDashboardService(), runner, CUSTOMER, view).load();
        assertEquals(List.of("loading", "error " + DashboardPresenter.UNAVAILABLE), view.calls);
        assertEquals(1, runner.tasks, "the database call ran as background work");
    }

    @Test
    void listsFormsAndSignInShowFriendlyMessages() {
        Fakes.ListView list = new Fakes.ListView();
        new ShipmentListPresenter(services.getShipmentDao(), runner, CUSTOMER, list).load();
        assertEquals("error " + ErrorMessages.DATABASE_UNAVAILABLE, list.last());

        Fakes.HistoryView history = new Fakes.HistoryView();
        new HistoryPresenter(services.getHistoryService(), runner, CUSTOMER, history).load();
        assertEquals("error " + ErrorMessages.DATABASE_UNAVAILABLE, history.last());

        Fakes.FormView form = new Fakes.FormView();
        ShipmentFormPresenter presenter = new ShipmentFormPresenter(services, runner, CUSTOMER, id -> { }, form);
        presenter.loadNew();
        assertEquals(ErrorMessages.DATABASE_UNAVAILABLE, form.unavailable);
        presenter.submit(Map.of("source", "Mumbai"));
        assertEquals(Map.of("", List.of(ErrorMessages.DATABASE_UNAVAILABLE)), form.errors);

        Fakes.LoginView login = new Fakes.LoginView();
        new LoginPresenter(services.getAuthService(), runner, u -> { }, login).signIn("c@example.test",
                "whatever-pass".toCharArray());
        assertEquals("error " + ErrorMessages.DATABASE_UNAVAILABLE, login.last());

        Fakes.ShellView shell = new Fakes.ShellView();
        new ShellPresenter(services.getNotificationService(), runner, CUSTOMER, shell).refresh();
        assertEquals(null, shell.unread, "the badge is hidden when the count cannot be read");

        for (List<String> calls : List.of(list.calls, history.calls, form.calls, login.calls)) {
            for (String call : calls) {
                assertFalse(call.contains("jdbc") || call.contains("Exception") || call.contains("127.0.0.1"), call);
            }
        }
    }

    @Test
    void emptySignInIsRefusedWithoutTouchingTheDatabase() {
        Fakes.LoginView login = new Fakes.LoginView();
        char[] password = "secret-pass".toCharArray();
        new LoginPresenter(services.getAuthService(), runner, u -> { }, login).signIn(" ", password);
        assertEquals(List.of("error " + LoginPresenter.MISSING_MESSAGE), login.calls);
        assertEquals(0, runner.tasks);
        assertTrue(new String(password).chars().allMatch(c -> c == 0), "the password array is cleared");
    }
}

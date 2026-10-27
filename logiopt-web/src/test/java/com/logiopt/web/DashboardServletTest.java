package com.logiopt.web;

import com.logiopt.app.AppServices;
import com.logiopt.dao.DBConnection;
import com.logiopt.dao.DashboardDAO;
import com.logiopt.dao.NotificationDAO;
import com.logiopt.dao.TestDatabase;
import com.logiopt.dao.UserDAO;
import com.logiopt.model.Role;
import com.logiopt.model.User;
import com.logiopt.service.DashboardService;
import com.logiopt.service.NotificationService;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Dashboard error state without a database: the counts cannot be loaded, so the page is shown with
 * a clear message and HTTP 503 instead of a stack trace or zero counts. No database is contacted
 * (the URL points at a closed local port).
 */
class DashboardServletTest {

    private NotificationService notifications;
    private AppServices services;

    @BeforeEach
    void unreachableDatabase() throws Exception {
        DBConnection dead = TestDatabase.unreachable();
        notifications = new NotificationService(new NotificationDAO(dead), new UserDAO(dead), 1, 10);
        services = new AppServices(null, null, null, null, null, null, null, null, null, null, null, notifications,
                null, new DashboardService(new DashboardDAO(dead), notifications), null);
    }

    @AfterEach
    void stop() {
        notifications.shutdown();
    }

    private WebFakes.Request get(HttpServlet servlet, String path, User user) throws Exception {
        WebFakes.init(servlet, WebFakes.context(services));
        WebFakes.Request r = new WebFakes.Request("GET", path);
        r.session = new WebFakes.Session();
        r.session.attributes.put(WebSupport.SESSION_USER, user);
        WebFakes.run(r, List.of(), servlet);
        return r;
    }

    @Test
    void customerDashboardShowsAnErrorStateWhenTheDatabaseIsUnavailable() throws Exception {
        WebFakes.Request r = get(new DashboardServlet(), "/dashboard",
                new User(5, "a@example.test", "A", Role.CUSTOMER, true));
        assertEquals(503, r.response.status);
        assertEquals("/WEB-INF/jsp/dashboard.jsp", r.response.forwardedTo);
        assertEquals(DashboardServlet.UNAVAILABLE, r.attributes.get("dashboardError"));
        assertNull(r.attributes.get("dashboard"), "no partial or zero counts are shown");
    }

    @Test
    void adminDashboardShowsAnErrorStateAndIsAdminOnly() throws Exception {
        WebFakes.Request r = get(new AdminDashboardServlet(), "/admin/dashboard",
                new User(6, "admin@logiopt.local", "Admin", Role.ADMIN, true));
        assertEquals(503, r.response.status);
        assertEquals("/WEB-INF/jsp/admin-dashboard.jsp", r.response.forwardedTo);
        assertEquals(DashboardServlet.UNAVAILABLE, r.attributes.get("dashboardError"));

        WebFakes.Request customer = get(new AdminDashboardServlet(), "/admin/dashboard",
                new User(5, "a@example.test", "A", Role.CUSTOMER, true));
        assertEquals(404, customer.response.status, "the Servlet checks the role again behind AuthFilter");
    }

    @Test
    void newPagesAreMappedWhereTheFiltersExpectThem() {
        assertEquals("/dashboard", DashboardServlet.class.getAnnotation(WebServlet.class).value()[0]);
        assertTrue(AdminDashboardServlet.class.getAnnotation(WebServlet.class).value()[0]
                .startsWith(AuthFilter.ADMIN_PREFIX + "/"));
        assertEquals("/history", HistoryServlet.class.getAnnotation(WebServlet.class).value()[0]);
        assertEquals("/shipments/edit", ShipmentEditServlet.class.getAnnotation(WebServlet.class).value()[0]);
        assertEquals("/reports/download", ReportDownloadServlet.class.getAnnotation(WebServlet.class).value()[0]);
        assertTrue(AuthFilter.PUBLIC_PATHS.stream().noneMatch(p -> p.startsWith("/dashboard") || p.startsWith("/history")
                || p.startsWith("/reports") || p.startsWith("/shipments")), "every new page needs sign-in");
    }
}

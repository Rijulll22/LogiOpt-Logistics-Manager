package com.logiopt.web;

import com.logiopt.exception.DataAccessException;
import com.logiopt.model.User;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Landing page after sign-in: the signed-in customer's own counts. The administrator is sent to
 * the system-wide dashboard. If the database is unavailable the page shows an error state (503).
 */
@WebServlet("/dashboard")
public class DashboardServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(DashboardServlet.class.getName());

    /** Shown when the counts cannot be loaded. */
    static final String UNAVAILABLE = "The dashboard could not be loaded because the database is unavailable. "
            + "Please try again in a moment.";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        User user = WebSupport.requireUser(req);
        if (user.isAdmin()) {
            resp.sendRedirect(req.getContextPath() + "/admin/dashboard");
            return;
        }
        try {
            req.setAttribute("dashboard", WebSupport.services(getServletContext()).getDashboardService().customer(user));
        } catch (DataAccessException e) {
            LOG.log(Level.SEVERE, "Loading the dashboard failed", e);
            resp.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            req.setAttribute("dashboardError", UNAVAILABLE);
        }
        WebSupport.forward(req, resp, "dashboard.jsp");
    }
}

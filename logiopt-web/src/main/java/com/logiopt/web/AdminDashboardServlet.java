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
 * System-wide counts for the administrator. AuthFilter already answers /admin URLs with 404 for
 * everyone else; this Servlet checks again. Database failure shows an error state (503).
 */
@WebServlet("/admin/dashboard")
public class AdminDashboardServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(AdminDashboardServlet.class.getName());

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        User admin = WebSupport.requireUser(req);
        if (!admin.isAdmin()) {
            WebSupport.notFound(req, resp);
            return;
        }
        try {
            req.setAttribute("dashboard", WebSupport.services(getServletContext()).getDashboardService().admin(admin));
        } catch (DataAccessException e) {
            LOG.log(Level.SEVERE, "Loading the admin dashboard failed", e);
            resp.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            req.setAttribute("dashboardError", DashboardServlet.UNAVAILABLE);
        }
        WebSupport.forward(req, resp, "admin-dashboard.jsp");
    }
}

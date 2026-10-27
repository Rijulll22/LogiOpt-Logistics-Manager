package com.logiopt.web;

import com.logiopt.exception.DataAccessException;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Administrator-only list of user accounts (no password data). AuthFilter already hides /admin from
 * other users; the service checks the role again.
 */
@WebServlet("/admin/users")
public class AdminUsersServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(AdminUsersServlet.class.getName());

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            req.setAttribute("users", WebSupport.services(getServletContext()).getAuthService()
                    .listUsers(WebSupport.requireUser(req)));
            WebSupport.forward(req, resp, "admin-users.jsp");
        } catch (SecurityException e) {
            WebSupport.notFound(req, resp);
        } catch (DataAccessException e) {
            LOG.log(Level.SEVERE, "Listing users failed", e);
            WebSupport.error(req, resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "Users could not be loaded because the database is unavailable.");
        }
    }
}

package com.logiopt.web;

import com.logiopt.exception.DataAccessException;
import com.logiopt.exception.InvalidRegistrationException;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Shows the registration form and registers customers. A valid form always leads to the same
 * redirect, whether or not the email address was already registered.
 */
@WebServlet("/register")
public class RegisterServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(RegisterServlet.class.getName());

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        WebSupport.forward(req, resp, "register.jsp");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String email = req.getParameter("email");
        String name = req.getParameter("name");
        String password = req.getParameter("password");
        try {
            WebSupport.services(getServletContext()).getAuthService()
                    .register(email, name, password == null ? null : password.toCharArray());
        } catch (InvalidRegistrationException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            req.setAttribute("errors", e.getErrors());
            req.setAttribute("email", email);
            req.setAttribute("name", name);
            WebSupport.forward(req, resp, "register.jsp");
            return;
        } catch (DataAccessException e) {
            LOG.log(Level.SEVERE, "Registration failed", e);
            WebSupport.error(req, resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "Registration is unavailable because the database is unavailable.");
            return;
        }
        resp.sendRedirect(req.getContextPath() + "/login?registered=1");
    }
}

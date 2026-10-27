package com.logiopt.web;

import com.logiopt.exception.DataAccessException;
import com.logiopt.service.AuthService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Shows the sign-in form and signs users in. On success the session id is rotated, the user is
 * stored in the server-side session and a new CSRF token is issued. Every failure shows the same
 * message; repeated failures lock the email and client address for a few minutes (HTTP 429).
 */
@WebServlet("/login")
public class LoginServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(LoginServlet.class.getName());

    /** Message for every failed sign-in. */
    static final String INVALID_MESSAGE = "Email or password is incorrect.";
    /** Message while locked. */
    static final String LOCKED_MESSAGE = "Too many failed sign-in attempts. Please wait a few minutes and try again.";
    /** Code for HTTP 429 Too Many Requests (no constant in the Servlet API). */
    static final int SC_TOO_MANY_REQUESTS = 429;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (WebSupport.currentUser(req) != null) {
            resp.sendRedirect(req.getContextPath() + "/dashboard");
            return;
        }
        if (req.getParameter("registered") != null) {
            req.setAttribute("notice", "Registration received. If this email address was not already registered, "
                    + "you can now sign in with it.");
        } else if (req.getParameter("loggedOut") != null) {
            req.setAttribute("notice", "You have been signed out.");
        } else if (req.getParameter("expired") != null) {
            req.setAttribute("notice", "Your session has expired. Please sign in again.");
        }
        WebSupport.forward(req, resp, "login.jsp");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String email = req.getParameter("email");
        String password = req.getParameter("password");
        AuthService.LoginResult result;
        try {
            result = WebSupport.services(getServletContext()).getAuthService()
                    .login(email, password == null ? null : password.toCharArray(), req.getRemoteAddr());
        } catch (DataAccessException e) {
            LOG.log(Level.SEVERE, "Sign-in failed", e);
            WebSupport.error(req, resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "Sign-in is unavailable because the database is unavailable.");
            return;
        }
        switch (result.getStatus()) {
            case SUCCESS:
                HttpSession session = req.getSession(true);
                req.changeSessionId();
                session.setAttribute(WebSupport.SESSION_USER, result.getUser());
                CsrfFilter.rotateToken(session);
                resp.sendRedirect(req.getContextPath() + "/dashboard");
                return;
            case LOCKED:
                resp.setStatus(SC_TOO_MANY_REQUESTS);
                req.setAttribute("error", LOCKED_MESSAGE);
                break;
            default:
                resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                req.setAttribute("error", INVALID_MESSAGE);
                break;
        }
        req.setAttribute("email", email);
        WebSupport.forward(req, resp, "login.jsp");
    }
}

package com.logiopt.web;

import com.logiopt.app.AppServices;
import com.logiopt.model.User;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;

/**
 * Small helpers shared by the Servlets and filters.
 */
public final class WebSupport {

    /** Location of the JSP views. */
    public static final String VIEW_PREFIX = "/WEB-INF/jsp/";
    /** Session attribute holding the signed-in {@link User}. */
    public static final String SESSION_USER = "logiopt.user";
    /** Request attribute exposing the signed-in user to JSPs. */
    public static final String CURRENT_USER = "currentUser";

    private WebSupport() {
    }

    /**
     * Returns the application singletons.
     *
     * @param context servlet context
     * @return services
     */
    public static AppServices services(ServletContext context) {
        return (AppServices) context.getAttribute(AppServices.ATTRIBUTE);
    }

    /**
     * Returns the signed-in user from the server-side session; never from request parameters.
     *
     * @param req request
     * @return user, or null if not signed in
     */
    public static User currentUser(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        Object user = session == null ? null : session.getAttribute(SESSION_USER);
        return user instanceof User ? (User) user : null;
    }

    /**
     * Returns the signed-in user of a protected request. Fails closed if AuthFilter did not run.
     *
     * @param req request
     * @return user
     * @throws IllegalStateException if nobody is signed in
     */
    public static User requireUser(HttpServletRequest req) {
        User user = currentUser(req);
        if (user == null) {
            throw new IllegalStateException("No signed-in user; AuthFilter must protect this URL");
        }
        return user;
    }

    /**
     * Returns the request path inside the application (servlet path plus path info).
     *
     * @param req request
     * @return path such as "/shipments/new"
     */
    public static String path(HttpServletRequest req) {
        String servletPath = req.getServletPath() == null ? "" : req.getServletPath();
        return req.getPathInfo() == null ? servletPath : servletPath + req.getPathInfo();
    }

    /**
     * Parses a positive id parameter.
     *
     * @param req  request
     * @param name parameter name
     * @return the id, or null if missing or not a positive number
     */
    public static Long idParam(HttpServletRequest req, String name) {
        String value = req.getParameter(name);
        try {
            long id = Long.parseLong(value == null ? "" : value.trim());
            return id > 0 ? id : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Forwards to a JSP view.
     *
     * @param req  request
     * @param resp response
     * @param view JSP file name under /WEB-INF/jsp/
     * @throws ServletException on forward failure
     * @throws IOException      on I/O failure
     */
    public static void forward(HttpServletRequest req, HttpServletResponse resp, String view)
            throws ServletException, IOException {
        req.getRequestDispatcher(VIEW_PREFIX + view).forward(req, resp);
    }

    /**
     * Shows the friendly error page with a status code and message (never a stack trace).
     *
     * @param req     request
     * @param resp    response
     * @param status  HTTP status
     * @param message user-facing message
     * @throws ServletException on forward failure
     * @throws IOException      on I/O failure
     */
    public static void error(HttpServletRequest req, HttpServletResponse resp, int status, String message)
            throws ServletException, IOException {
        resp.setStatus(status);
        req.setAttribute("errorMessage", message);
        forward(req, resp, "error.jsp");
    }

    /**
     * Shows the same 404 page for a missing resource and for one the user does not own.
     *
     * @param req  request
     * @param resp response
     * @throws ServletException on forward failure
     * @throws IOException      on I/O failure
     */
    public static void notFound(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        error(req, resp, HttpServletResponse.SC_NOT_FOUND, "The page you requested does not exist.");
    }
}

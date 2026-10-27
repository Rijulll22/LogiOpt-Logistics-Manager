package com.logiopt.web;

import com.logiopt.app.AppServices;
import com.logiopt.exception.DataAccessException;
import com.logiopt.model.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Second filter (declared in web.xml): every URL except sign-in and registration needs a signed-in
 * user from the server-side session. Anonymous requests are redirected to /login (with an
 * "expired" notice when the browser sent a session id that is no longer valid). /admin URLs are
 * answered with the normal 404 page unless the user is the administrator, so their existence is
 * not revealed. The signed-in user is exposed to JSPs as the request attribute "currentUser".
 */
public class AuthFilter extends HttpFilter {

    private static final long serialVersionUID = 1L;

    /** URLs reachable without signing in. */
    static final Set<String> PUBLIC_PATHS = Set.of("/login", "/register");
    /** Prefix of administrator-only URLs. */
    static final String ADMIN_PREFIX = "/admin";
    /** Request attribute with the signed-in customer's unread notification count. */
    static final String UNREAD_COUNT = "unreadCount";

    private static final Logger LOG = Logger.getLogger(AuthFilter.class.getName());

    @Override
    protected void doFilter(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        String path = WebSupport.path(req);
        User user = WebSupport.currentUser(req);
        if (user != null) {
            req.setAttribute(WebSupport.CURRENT_USER, user);
            exposeUnreadCount(req, user);
        }
        if (PUBLIC_PATHS.contains(path)) {
            chain.doFilter(req, resp);
            return;
        }
        if (user == null) {
            boolean expired = req.getRequestedSessionId() != null && !req.isRequestedSessionIdValid();
            resp.sendRedirect(req.getContextPath() + "/login" + (expired ? "?expired=1" : ""));
            return;
        }
        if ((path.equals(ADMIN_PREFIX) || path.startsWith(ADMIN_PREFIX + "/")) && !user.isAdmin()) {
            WebSupport.notFound(req, resp);
            return;
        }
        chain.doFilter(req, resp);
    }

    /** Puts the user's unread notification count on the request for the page header; never fails the request. */
    private void exposeUnreadCount(HttpServletRequest req, User user) {
        AppServices services = WebSupport.services(getServletContext());
        if (user.isAdmin() || services == null || services.getNotificationService() == null) {
            return;
        }
        try {
            req.setAttribute(UNREAD_COUNT, services.getNotificationService().unreadCount(user));
        } catch (DataAccessException e) {
            LOG.log(Level.WARNING, "Unread notification count unavailable", e);
        }
    }
}

package com.logiopt.web;

import com.logiopt.exception.DataAccessException;
import com.logiopt.exception.NotFoundException;
import com.logiopt.model.User;
import com.logiopt.service.NotificationService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The signed-in user's notifications. GET lists them; POST action=read&id=N marks one as read and
 * action=readAll marks all (POST + CSRF). Another user's notification gives 404.
 */
@WebServlet("/notifications")
public class NotificationServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(NotificationServlet.class.getName());

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            req.setAttribute("notifications", WebSupport.services(getServletContext()).getNotificationService()
                    .list(WebSupport.requireUser(req)));
            WebSupport.forward(req, resp, "notifications.jsp");
        } catch (DataAccessException e) {
            LOG.log(Level.SEVERE, "Loading notifications failed", e);
            WebSupport.error(req, resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "Notifications could not be loaded because the database is unavailable.");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        NotificationService notifications = WebSupport.services(getServletContext()).getNotificationService();
        User user = WebSupport.requireUser(req);
        try {
            if ("readAll".equals(req.getParameter("action"))) {
                notifications.markAllRead(user);
            } else if ("read".equals(req.getParameter("action"))) {
                Long id = WebSupport.idParam(req, "id");
                if (id == null) {
                    WebSupport.notFound(req, resp);
                    return;
                }
                notifications.markRead(user, id);
            } else {
                WebSupport.error(req, resp, HttpServletResponse.SC_BAD_REQUEST, "Unknown notification action.");
                return;
            }
            resp.sendRedirect(req.getContextPath() + "/notifications");
        } catch (NotFoundException e) {
            WebSupport.notFound(req, resp);
        } catch (DataAccessException e) {
            LOG.log(Level.SEVERE, "Updating notifications failed", e);
            WebSupport.error(req, resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "Notifications could not be updated because the database is unavailable.");
        }
    }
}

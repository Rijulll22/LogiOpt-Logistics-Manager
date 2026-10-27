package com.logiopt.web;

import com.logiopt.exception.DataAccessException;
import com.logiopt.strategy.BalancedStrategy;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The signed-in user's latest shipments, bookings and reports, newest first.
 */
@WebServlet("/history")
public class HistoryServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(HistoryServlet.class.getName());

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            req.setAttribute("history", WebSupport.services(getServletContext()).getHistoryService()
                    .of(WebSupport.requireUser(req)));
            req.setAttribute("defaultStrategy", BalancedStrategy.KEY);
            WebSupport.forward(req, resp, "history.jsp");
        } catch (DataAccessException e) {
            LOG.log(Level.SEVERE, "Loading history failed", e);
            WebSupport.error(req, resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "Your history could not be loaded because the database is unavailable.");
        }
    }
}

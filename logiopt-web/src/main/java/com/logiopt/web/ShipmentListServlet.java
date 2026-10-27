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
 * Lists the signed-in user's shipments.
 */
@WebServlet("/shipments")
public class ShipmentListServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(ShipmentListServlet.class.getName());

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            req.setAttribute("shipments",
                    WebSupport.services(getServletContext()).getShipmentDao()
                            .findByUser(WebSupport.requireUser(req).getUserId()));
            req.setAttribute("defaultStrategy", BalancedStrategy.KEY);
            WebSupport.forward(req, resp, "shipment-list.jsp");
        } catch (DataAccessException e) {
            LOG.log(Level.SEVERE, "Listing shipments failed", e);
            WebSupport.error(req, resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "Shipments could not be loaded because the database is unavailable.");
        }
    }
}

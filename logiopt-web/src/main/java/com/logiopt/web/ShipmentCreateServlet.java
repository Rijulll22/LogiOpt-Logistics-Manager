package com.logiopt.web;

import com.logiopt.app.AppServices;
import com.logiopt.exception.DataAccessException;
import com.logiopt.exception.InvalidShipmentException;
import com.logiopt.strategy.BalancedStrategy;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Validates and saves a new shipment, then redirects to its route comparison.
 */
@WebServlet("/shipments/create")
public class ShipmentCreateServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(ShipmentCreateServlet.class.getName());

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding(StandardCharsets.UTF_8.name());
        AppServices services = WebSupport.services(getServletContext());
        List<String> cities;
        try {
            cities = services.getRouteDao().knownCities();
        } catch (DataAccessException e) {
            LOG.log(Level.SEVERE, "Loading cities failed", e);
            WebSupport.error(req, resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "The shipment could not be checked because the database is unavailable.");
            return;
        }
        ShipmentForm form = ShipmentForm.fromRequest(req, WebSupport.requireUser(req).getUserId());
        Set<String> errors = new LinkedHashSet<>(form.getParseErrors());
        try {
            services.getValidator().validate(form.getShipment(), cities);
        } catch (InvalidShipmentException e) {
            errors.addAll(e.getErrors());
        }
        if (!errors.isEmpty()) {
            req.setAttribute("errors", new ArrayList<>(errors));
            req.setAttribute(ShipmentForm.ATTRIBUTE, form.getValues());
            ShipmentFormServlet.addFormOptions(req, cities);
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            WebSupport.forward(req, resp, "shipment-form.jsp");
            return;
        }
        try {
            long id = services.getShipmentDao().insert(form.getShipment());
            resp.sendRedirect(req.getContextPath() + "/compare?shipmentId=" + id + "&strategy=" + BalancedStrategy.KEY);
        } catch (DataAccessException e) {
            LOG.log(Level.SEVERE, "Saving shipment failed", e);
            WebSupport.error(req, resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "The shipment could not be saved because the database is unavailable.");
        }
    }
}

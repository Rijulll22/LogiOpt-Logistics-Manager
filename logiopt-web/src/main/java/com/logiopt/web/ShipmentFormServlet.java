package com.logiopt.web;

import com.logiopt.exception.DataAccessException;
import com.logiopt.model.CargoType;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Shows the empty shipment form.
 */
@WebServlet("/shipments/new")
public class ShipmentFormServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(ShipmentFormServlet.class.getName());

    /**
     * Puts the cargo type names and the known city names on the request for the form's dropdowns.
     *
     * @param req    request
     * @param cities cities served by at least one route
     */
    static void addFormOptions(HttpServletRequest req, List<String> cities) {
        List<String> cargoTypes = new ArrayList<>();
        for (CargoType type : CargoType.values()) {
            cargoTypes.add(type.name());
        }
        req.setAttribute("cargoTypes", cargoTypes);
        req.setAttribute("cities", cities);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        List<String> cities;
        try {
            cities = WebSupport.services(getServletContext()).getRouteDao().knownCities();
        } catch (DataAccessException e) {
            LOG.log(Level.SEVERE, "Loading cities failed", e);
            WebSupport.error(req, resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "The shipment form could not be loaded because the database is unavailable.");
            return;
        }
        addFormOptions(req, cities);
        req.setAttribute(ShipmentForm.ATTRIBUTE, ShipmentForm.emptyValues());
        WebSupport.forward(req, resp, "shipment-form.jsp");
    }
}

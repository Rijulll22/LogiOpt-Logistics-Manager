package com.logiopt.web;

import com.logiopt.app.AppServices;
import com.logiopt.exception.DataAccessException;
import com.logiopt.exception.InvalidShipmentException;
import com.logiopt.exception.NotFoundException;
import com.logiopt.exception.ShipmentNotEditableException;
import com.logiopt.model.Shipment;
import com.logiopt.model.User;
import com.logiopt.strategy.BalancedStrategy;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Edits a DRAFT shipment of the signed-in user. GET shows the filled-in form; POST (CSRF-checked
 * by CsrfFilter) validates exactly like creation and saves. Someone else's or a missing shipment
 * gives 404; a shipment that is not a draft gives 409 with the reason, and nothing is changed.
 */
@WebServlet("/shipments/edit")
public class ShipmentEditServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(ShipmentEditServlet.class.getName());

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        AppServices services = WebSupport.services(getServletContext());
        Long id = WebSupport.idParam(req, "shipmentId");
        if (id == null) {
            WebSupport.notFound(req, resp);
            return;
        }
        try {
            Shipment draft = services.getShipmentService().editableDraft(WebSupport.requireUser(req), id);
            showForm(req, resp, services.getRouteDao().knownCities(), draft.getShipmentId(),
                    ShipmentForm.valuesOf(draft), List.of(), HttpServletResponse.SC_OK);
        } catch (NotFoundException e) {
            WebSupport.notFound(req, resp);
        } catch (ShipmentNotEditableException e) {
            WebSupport.error(req, resp, HttpServletResponse.SC_CONFLICT, e.getMessage());
        } catch (DataAccessException e) {
            LOG.log(Level.SEVERE, "Loading shipment for editing failed", e);
            WebSupport.error(req, resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "The shipment could not be loaded because the database is unavailable.");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        AppServices services = WebSupport.services(getServletContext());
        Long id = WebSupport.idParam(req, "shipmentId");
        if (id == null) {
            WebSupport.notFound(req, resp);
            return;
        }
        User user = WebSupport.requireUser(req);
        try {
            services.getShipmentService().editableDraft(user, id);
            List<String> cities = services.getRouteDao().knownCities();
            ShipmentForm form = ShipmentForm.fromRequest(req, user.getUserId());
            Set<String> errors = new LinkedHashSet<>(form.getParseErrors());
            try {
                services.getValidator().validate(form.getShipment(), cities);
            } catch (InvalidShipmentException e) {
                errors.addAll(e.getErrors());
            }
            if (errors.isEmpty()) {
                try {
                    services.getShipmentService().updateDraft(user, id, form.getShipment(), cities);
                    resp.sendRedirect(req.getContextPath() + "/compare?shipmentId=" + id + "&strategy="
                            + BalancedStrategy.KEY);
                    return;
                } catch (InvalidShipmentException e) {
                    errors.addAll(e.getErrors());
                }
            }
            showForm(req, resp, cities, id, form.getValues(), new ArrayList<>(errors), HttpServletResponse.SC_BAD_REQUEST);
        } catch (NotFoundException e) {
            WebSupport.notFound(req, resp);
        } catch (ShipmentNotEditableException e) {
            WebSupport.error(req, resp, HttpServletResponse.SC_CONFLICT, e.getMessage());
        } catch (DataAccessException e) {
            LOG.log(Level.SEVERE, "Saving shipment edit failed", e);
            WebSupport.error(req, resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "The shipment could not be saved because the database is unavailable.");
        }
    }

    private static void showForm(HttpServletRequest req, HttpServletResponse resp, List<String> cities, long shipmentId,
                                 Map<String, String> values, List<String> errors, int status)
            throws ServletException, IOException {
        ShipmentFormServlet.addFormOptions(req, cities);
        req.setAttribute(ShipmentForm.ATTRIBUTE, values);
        req.setAttribute("editShipmentId", shipmentId);
        if (!errors.isEmpty()) {
            req.setAttribute("errors", errors);
        }
        resp.setStatus(status);
        WebSupport.forward(req, resp, "shipment-form.jsp");
    }
}

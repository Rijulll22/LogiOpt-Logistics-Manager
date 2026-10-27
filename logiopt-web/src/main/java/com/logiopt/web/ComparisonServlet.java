package com.logiopt.web;

import com.logiopt.app.AppServices;
import com.logiopt.exception.DataAccessException;
import com.logiopt.exception.InvalidShipmentException;
import com.logiopt.exception.InvalidStrategyException;
import com.logiopt.model.PlanningResult;
import com.logiopt.model.Route;
import com.logiopt.model.Shipment;
import com.logiopt.model.User;
import com.logiopt.service.RouteEvaluator;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Plans a saved shipment with the requested strategy and shows the route comparison.
 */
@WebServlet("/compare")
public class ComparisonServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(ComparisonServlet.class.getName());

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        AppServices services = WebSupport.services(getServletContext());
        Long shipmentId = WebSupport.idParam(req, "shipmentId");
        if (shipmentId == null) {
            WebSupport.error(req, resp, HttpServletResponse.SC_BAD_REQUEST, "A valid shipment id is required.");
            return;
        }
        String strategy = req.getParameter("strategy");
        try {
            User user = WebSupport.requireUser(req);
            Optional<Shipment> shipment = services.getShipmentDao().findByIdForUser(shipmentId, user.getUserId());
            if (shipment.isEmpty()) {
                WebSupport.notFound(req, resp);
                return;
            }
            req.setAttribute("activeBooking", services.getBookingService().activeBooking(user, shipmentId).orElse(null));
            req.setAttribute("dateBookable", services.getBookingService().isDispatchDateBookable(shipment.get()));
            List<Route> candidates = services.getRouteDao()
                    .findBySourceAndDestination(shipment.get().getSource(), shipment.get().getDestination());
            PlanningResult result = services.getPlanningService().plan(shipment.get(), candidates, strategy);
            req.setAttribute("result", result);
            req.setAttribute("strategies", services.getRecommendationService().getStrategies());
            req.setAttribute("selectedStrategy", result.getRecommendation().getStrategyKey());
            req.setAttribute("estimateLabel", RouteEvaluator.ESTIMATE_LABEL);
            WebSupport.forward(req, resp, "comparison.jsp");
        } catch (InvalidStrategyException e) {
            WebSupport.error(req, resp, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (InvalidShipmentException e) {
            WebSupport.error(req, resp, HttpServletResponse.SC_BAD_REQUEST,
                    "This shipment can no longer be planned: " + String.join(" ", e.getErrors()));
        } catch (DataAccessException e) {
            LOG.log(Level.SEVERE, "Comparison failed", e);
            WebSupport.error(req, resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "Routes could not be loaded because the database is unavailable.");
        }
    }
}

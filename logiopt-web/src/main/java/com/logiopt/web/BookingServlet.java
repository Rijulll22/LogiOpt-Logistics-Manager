package com.logiopt.web;

import com.logiopt.exception.BookingException;
import com.logiopt.exception.DataAccessException;
import com.logiopt.exception.InvalidShipmentException;
import com.logiopt.exception.InvalidStrategyException;
import com.logiopt.exception.NotFoundException;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * GET lists the signed-in user's bookings. POST books a route from the comparison page: only the
 * shipment id, route id and strategy key are read from the form; everything stored is recomputed on
 * the server.
 */
@WebServlet("/bookings")
public class BookingServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(BookingServlet.class.getName());

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            req.setAttribute("bookings", WebSupport.services(getServletContext()).getBookingService()
                    .myBookings(WebSupport.requireUser(req)));
            WebSupport.forward(req, resp, "booking-list.jsp");
        } catch (DataAccessException e) {
            LOG.log(Level.SEVERE, "Listing bookings failed", e);
            WebSupport.error(req, resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "Bookings could not be loaded because the database is unavailable.");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Long shipmentId = WebSupport.idParam(req, "shipmentId");
        Long routeId = WebSupport.idParam(req, "routeId");
        if (shipmentId == null || routeId == null) {
            WebSupport.error(req, resp, HttpServletResponse.SC_BAD_REQUEST, "A valid shipment and route are required.");
            return;
        }
        try {
            long bookingId = WebSupport.services(getServletContext()).getBookingService()
                    .book(WebSupport.requireUser(req), shipmentId, routeId, req.getParameter("strategy"));
            resp.sendRedirect(req.getContextPath() + "/bookings/view?id=" + bookingId + "&created=1");
        } catch (NotFoundException e) {
            WebSupport.notFound(req, resp);
        } catch (BookingException e) {
            WebSupport.error(req, resp, HttpServletResponse.SC_CONFLICT, e.getMessage());
        } catch (InvalidStrategyException e) {
            WebSupport.error(req, resp, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (InvalidShipmentException e) {
            WebSupport.error(req, resp, HttpServletResponse.SC_BAD_REQUEST,
                    "This shipment cannot be booked: " + String.join(" ", e.getErrors()));
        } catch (DataAccessException e) {
            LOG.log(Level.SEVERE, "Booking failed", e);
            WebSupport.error(req, resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "The booking could not be saved because the database is unavailable.");
        }
    }
}

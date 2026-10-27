package com.logiopt.web;

import com.logiopt.exception.DataAccessException;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Administrator-only, read-only list of all bookings. AuthFilter hides /admin from other users; the
 * service checks the role again.
 */
@WebServlet("/admin/bookings")
public class AdminBookingsServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(AdminBookingsServlet.class.getName());

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            req.setAttribute("bookings", WebSupport.services(getServletContext()).getBookingService()
                    .allBookings(WebSupport.requireUser(req)));
            WebSupport.forward(req, resp, "admin-bookings.jsp");
        } catch (SecurityException e) {
            WebSupport.notFound(req, resp);
        } catch (DataAccessException e) {
            LOG.log(Level.SEVERE, "Listing all bookings failed", e);
            WebSupport.error(req, resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "Bookings could not be loaded because the database is unavailable.");
        }
    }
}

package com.logiopt.web;

import com.logiopt.exception.BookingException;
import com.logiopt.exception.DataAccessException;
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
 * POST cancels the owner's unpaid booking. A paid booking is refused with 409; someone else's
 * booking (including for the administrator) gives 404.
 */
@WebServlet("/bookings/cancel")
public class BookingCancelServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(BookingCancelServlet.class.getName());

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Long id = WebSupport.idParam(req, "id");
        if (id == null) {
            WebSupport.notFound(req, resp);
            return;
        }
        try {
            WebSupport.services(getServletContext()).getBookingService().cancel(WebSupport.requireUser(req), id);
            resp.sendRedirect(req.getContextPath() + "/bookings/view?id=" + id + "&cancelled=1");
        } catch (NotFoundException e) {
            WebSupport.notFound(req, resp);
        } catch (BookingException e) {
            WebSupport.error(req, resp, HttpServletResponse.SC_CONFLICT, e.getMessage());
        } catch (DataAccessException e) {
            LOG.log(Level.SEVERE, "Cancelling booking failed", e);
            WebSupport.error(req, resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "The booking could not be cancelled because the database is unavailable.");
        }
    }
}

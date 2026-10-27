package com.logiopt.web;

import com.logiopt.app.AppServices;
import com.logiopt.exception.DataAccessException;
import com.logiopt.exception.NotFoundException;
import com.logiopt.model.Booking;
import com.logiopt.model.TrackingEvent;
import com.logiopt.model.User;
import com.logiopt.service.TrackingService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Shows a booking's snapshot, state and payment attempts. The owner sees "Pay" and "Cancel" while it
 * is unpaid; the administrator can view any booking but gets no actions. Everyone else gets 404.
 */
@WebServlet("/bookings/view")
public class BookingViewServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(BookingViewServlet.class.getName());

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Long id = WebSupport.idParam(req, "id");
        if (id == null) {
            WebSupport.notFound(req, resp);
            return;
        }
        AppServices services = WebSupport.services(getServletContext());
        User user = WebSupport.requireUser(req);
        try {
            Booking booking = services.getBookingService().view(user, id);
            boolean owner = booking.getOwnerUserId() == user.getUserId();
            req.setAttribute("booking", booking);
            req.setAttribute("payments", services.getPaymentService().attempts(booking));
            List<TrackingEvent> timeline = services.getTrackingService().timeline(booking);
            req.setAttribute("timeline", timeline);
            req.setAttribute("nextMilestone", TrackingService.nextMilestone(booking, timeline));
            req.setAttribute("canAct", owner && booking.isUnpaid());
            req.setAttribute("readOnlyAdmin", !owner);
            if (req.getParameter("created") != null) {
                req.setAttribute("notice", "Booking saved. Pay to confirm it.");
            } else if ("paid".equals(req.getParameter("payment"))) {
                req.setAttribute("notice", "Simulated payment succeeded. The booking is confirmed.");
            } else if ("failed".equals(req.getParameter("payment"))) {
                req.setAttribute("notice", "Simulated payment failed. You can try again or cancel the booking.");
            } else if (req.getParameter("cancelled") != null) {
                req.setAttribute("notice", "Booking cancelled. The shipment can be booked again.");
            }
            WebSupport.forward(req, resp, "booking.jsp");
        } catch (NotFoundException e) {
            WebSupport.notFound(req, resp);
        } catch (DataAccessException e) {
            LOG.log(Level.SEVERE, "Loading booking failed", e);
            WebSupport.error(req, resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "The booking could not be loaded because the database is unavailable.");
        }
    }
}

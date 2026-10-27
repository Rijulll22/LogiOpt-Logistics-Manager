package com.logiopt.web;

import com.logiopt.app.AppServices;
import com.logiopt.exception.BookingException;
import com.logiopt.exception.DataAccessException;
import com.logiopt.exception.NotFoundException;
import com.logiopt.model.Booking;
import com.logiopt.model.BookingStatus;
import com.logiopt.model.Payment;
import com.logiopt.model.PaymentMethod;
import com.logiopt.model.PaymentStatus;
import com.logiopt.model.User;
import com.logiopt.service.PaymentService;
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
 * SIMULATED payment. GET shows the simulation page for the owner's unpaid booking (amount from the
 * snapshot, method label, "Simulate success" / "Simulate failure"); POST records one attempt. No
 * card or account data is ever asked for. Other users, including the administrator, get 404.
 */
@WebServlet("/payments")
public class PaymentServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(PaymentServlet.class.getName());

    /** Value of the "outcome" button for a successful simulation. */
    static final String SUCCESS = "SUCCESS";
    /** Value of the "outcome" button for a failed simulation. */
    static final String FAILURE = "FAILURE";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Long bookingId = WebSupport.idParam(req, "bookingId");
        if (bookingId == null) {
            WebSupport.notFound(req, resp);
            return;
        }
        AppServices services = WebSupport.services(getServletContext());
        User user = WebSupport.requireUser(req);
        try {
            Booking booking = services.getBookingService().view(user, bookingId);
            if (booking.getOwnerUserId() != user.getUserId()) {
                WebSupport.notFound(req, resp);
                return;
            }
            if (!booking.isUnpaid()) {
                WebSupport.error(req, resp, HttpServletResponse.SC_CONFLICT,
                        booking.getStatus() == BookingStatus.CONFIRMED
                                ? "This booking is already paid." : "Cancelled bookings cannot be paid.");
                return;
            }
            req.setAttribute("booking", booking);
            req.setAttribute("methods", List.of(PaymentMethod.values()));
            req.setAttribute("successOutcome", SUCCESS);
            req.setAttribute("failureOutcome", FAILURE);
            WebSupport.forward(req, resp, "payment.jsp");
        } catch (NotFoundException e) {
            WebSupport.notFound(req, resp);
        } catch (DataAccessException e) {
            LOG.log(Level.SEVERE, "Loading payment page failed", e);
            WebSupport.error(req, resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "The payment page could not be loaded because the database is unavailable.");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Long bookingId = WebSupport.idParam(req, "bookingId");
        String outcome = req.getParameter("outcome");
        if (bookingId == null) {
            WebSupport.notFound(req, resp);
            return;
        }
        if (!SUCCESS.equals(outcome) && !FAILURE.equals(outcome)) {
            WebSupport.error(req, resp, HttpServletResponse.SC_BAD_REQUEST, "Choose Simulate success or Simulate failure.");
            return;
        }
        try {
            PaymentMethod method = PaymentService.parseMethod(req.getParameter("method"));
            Payment payment = WebSupport.services(getServletContext()).getPaymentService()
                    .simulate(WebSupport.requireUser(req), bookingId, method, SUCCESS.equals(outcome));
            resp.sendRedirect(req.getContextPath() + "/bookings/view?id=" + bookingId + "&payment="
                    + (payment.getStatus() == PaymentStatus.PAID ? "paid" : "failed"));
        } catch (NotFoundException e) {
            WebSupport.notFound(req, resp);
        } catch (BookingException e) {
            WebSupport.error(req, resp, HttpServletResponse.SC_CONFLICT, e.getMessage());
        } catch (DataAccessException e) {
            LOG.log(Level.SEVERE, "Recording payment failed", e);
            WebSupport.error(req, resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "The payment could not be recorded because the database is unavailable.");
        }
    }
}

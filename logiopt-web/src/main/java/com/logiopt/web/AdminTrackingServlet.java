package com.logiopt.web;

import com.logiopt.exception.DataAccessException;
import com.logiopt.exception.NotFoundException;
import com.logiopt.exception.TrackingException;
import com.logiopt.model.Booking;
import com.logiopt.model.Milestone;
import com.logiopt.model.TrackingEvent;
import com.logiopt.model.User;
import com.logiopt.service.TrackingService;
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
 * Administrator-only tracking page. GET lists CONFIRMED bookings with the single legal next
 * milestone. POST action=record records that milestone with a description (source ADMIN);
 * action=advance runs the idempotent simulation step (source SIMULATED). Both carry the milestone the
 * page was shown with, so a double submission records nothing twice. Everyone else gets 404
 * (AuthFilter, and the service checks the role again).
 */
@WebServlet("/admin/tracking")
public class AdminTrackingServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(AdminTrackingServlet.class.getName());

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        TrackingService tracking = WebSupport.services(getServletContext()).getTrackingService();
        User admin = WebSupport.requireUser(req);
        try {
            List<TrackingRow> rows = new ArrayList<>();
            for (Booking b : tracking.confirmedBookings(admin)) {
                List<TrackingEvent> events = tracking.eventsForAdmin(admin, b.getBookingId());
                rows.add(new TrackingRow(b, events, TrackingService.latest(events),
                        TrackingService.nextMilestone(b, events)));
            }
            req.setAttribute("rows", rows);
            if ("recorded".equals(req.getParameter("result"))) {
                req.setAttribute("notice", "Milestone recorded.");
            } else if ("unchanged".equals(req.getParameter("result"))) {
                req.setAttribute("notice", "Nothing changed: tracking had already moved on or is delivered.");
            }
            WebSupport.forward(req, resp, "admin-tracking.jsp");
        } catch (SecurityException e) {
            WebSupport.notFound(req, resp);
        } catch (DataAccessException e) {
            LOG.log(Level.SEVERE, "Loading tracking failed", e);
            WebSupport.error(req, resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "Tracking could not be loaded because the database is unavailable.");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        User admin = WebSupport.requireUser(req);
        if (!admin.isAdmin()) {
            WebSupport.notFound(req, resp);
            return;
        }
        Long bookingId = WebSupport.idParam(req, "bookingId");
        if (bookingId == null) {
            WebSupport.notFound(req, resp);
            return;
        }
        TrackingService tracking = WebSupport.services(getServletContext()).getTrackingService();
        try {
            Milestone expected = TrackingService.parseMilestone(req.getParameter("current"));
            TrackingService.Result result;
            if ("advance".equals(req.getParameter("action"))) {
                result = tracking.advance(admin, bookingId, expected);
            } else if ("record".equals(req.getParameter("action"))) {
                Milestone target = TrackingService.parseMilestone(req.getParameter("milestone"));
                result = tracking.record(admin, bookingId, expected, target, req.getParameter("description"));
            } else {
                WebSupport.error(req, resp, HttpServletResponse.SC_BAD_REQUEST, "Unknown tracking action.");
                return;
            }
            resp.sendRedirect(req.getContextPath() + "/admin/tracking?result="
                    + (result.getOutcome() == TrackingService.Outcome.RECORDED ? "recorded" : "unchanged"));
        } catch (SecurityException | NotFoundException e) {
            WebSupport.notFound(req, resp);
        } catch (TrackingException e) {
            WebSupport.error(req, resp, HttpServletResponse.SC_CONFLICT, e.getMessage());
        } catch (DataAccessException e) {
            LOG.log(Level.SEVERE, "Recording tracking failed", e);
            WebSupport.error(req, resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "The milestone could not be recorded because the database is unavailable.");
        }
    }
}

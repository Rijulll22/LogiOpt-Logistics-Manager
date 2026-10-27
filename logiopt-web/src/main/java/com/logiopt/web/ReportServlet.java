package com.logiopt.web;

import com.logiopt.app.AppServices;
import com.logiopt.exception.DataAccessException;
import com.logiopt.exception.InvalidShipmentException;
import com.logiopt.exception.InvalidStrategyException;
import com.logiopt.model.PlanningResult;
import com.logiopt.model.Shipment;
import com.logiopt.report.ReportJob;
import com.logiopt.report.ReportRecord;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * POST submits a background TXT report job (recorded in the database) and returns immediately;
 * GET shows the job's persisted status.
 */
@WebServlet("/reports")
public class ReportServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(ReportServlet.class.getName());

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        AppServices services = WebSupport.services(getServletContext());
        Long shipmentId = WebSupport.idParam(req, "shipmentId");
        if (shipmentId == null) {
            WebSupport.error(req, resp, HttpServletResponse.SC_BAD_REQUEST, "A valid shipment id is required.");
            return;
        }
        try {
            Optional<Shipment> shipment = services.getShipmentDao()
                    .findByIdForUser(shipmentId, WebSupport.requireUser(req).getUserId());
            if (shipment.isEmpty()) {
                WebSupport.notFound(req, resp);
                return;
            }
            PlanningResult result = services.getPlanningService().plan(shipment.get(),
                    services.getRouteDao().findBySourceAndDestination(shipment.get().getSource(),
                            shipment.get().getDestination()),
                    req.getParameter("strategy"));
            ReportJob job = services.getReportService().submit(result);
            resp.sendRedirect(req.getContextPath() + "/reports?jobId="
                    + URLEncoder.encode(job.getJobId(), StandardCharsets.UTF_8));
        } catch (InvalidStrategyException e) {
            WebSupport.error(req, resp, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (InvalidShipmentException e) {
            WebSupport.error(req, resp, HttpServletResponse.SC_BAD_REQUEST,
                    "This shipment can no longer be planned: " + String.join(" ", e.getErrors()));
        } catch (DataAccessException e) {
            LOG.log(Level.SEVERE, "Report submission failed", e);
            WebSupport.error(req, resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "The report could not be started because the database is unavailable.");
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Optional<ReportRecord> job;
        try {
            job = WebSupport.services(getServletContext()).getReportService()
                    .getJob(req.getParameter("jobId"), WebSupport.requireUser(req).getUserId());
        } catch (DataAccessException e) {
            LOG.log(Level.SEVERE, "Loading report status failed", e);
            WebSupport.error(req, resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "The report status could not be loaded because the database is unavailable.");
            return;
        }
        if (job.isEmpty()) {
            WebSupport.notFound(req, resp);
            return;
        }
        req.setAttribute("job", job.get());
        WebSupport.forward(req, resp, "report-status.jsp");
    }
}

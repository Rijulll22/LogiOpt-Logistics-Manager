package com.logiopt.web;

import com.logiopt.exception.DataAccessException;
import com.logiopt.report.ReportDownload;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Sends a DONE report of the signed-in user as a plain-text attachment. The file is resolved only
 * from the managed file name stored for the job (see ReportService#download); the request supplies
 * nothing but the job id. A missing, foreign, unfinished or failed job, and any file that cannot be
 * resolved safely, all give the usual 404 page without internal details.
 */
@WebServlet("/reports/download")
public class ReportDownloadServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(ReportDownloadServlet.class.getName());

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Optional<ReportDownload> download;
        try {
            download = WebSupport.services(getServletContext()).getReportService()
                    .download(req.getParameter("jobId"), WebSupport.requireUser(req).getUserId());
        } catch (DataAccessException e) {
            LOG.log(Level.SEVERE, "Report download lookup failed", e);
            WebSupport.error(req, resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "The report could not be loaded because the database is unavailable.");
            return;
        }
        if (download.isEmpty()) {
            WebSupport.notFound(req, resp);
            return;
        }
        InputStream in;
        try {
            in = Files.newInputStream(download.get().getFile());
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Report file disappeared before download", e);
            WebSupport.notFound(req, resp);
            return;
        }
        try (InputStream file = in) {
            resp.setContentType("text/plain; charset=UTF-8");
            resp.setHeader("Content-Disposition", "attachment; filename=\"" + download.get().getDownloadName() + "\"");
            resp.setHeader("X-Content-Type-Options", "nosniff");
            resp.setHeader("Cache-Control", "private, no-store");
            resp.setContentLengthLong(Files.size(download.get().getFile()));
            OutputStream out = resp.getOutputStream();
            file.transferTo(out);
            out.flush();
        }
    }
}

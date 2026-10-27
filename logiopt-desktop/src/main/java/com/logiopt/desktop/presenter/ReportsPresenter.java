package com.logiopt.desktop.presenter;

import com.logiopt.app.AppServices;
import com.logiopt.desktop.presenter.Models.ReportJobRow;
import com.logiopt.desktop.presenter.Models.ReportProgress;
import com.logiopt.desktop.presenter.Models.ReportsModel;
import com.logiopt.desktop.support.BackgroundRunner;
import com.logiopt.desktop.support.ErrorMessages;
import com.logiopt.desktop.support.Poller;
import com.logiopt.exception.NotFoundException;
import com.logiopt.model.PlanningResult;
import com.logiopt.model.Shipment;
import com.logiopt.model.User;
import com.logiopt.report.ReportDownload;
import com.logiopt.report.ReportRecord;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reports: pick one of the user's shipments and a strategy, generate the TXT report through the
 * existing {@link com.logiopt.report.ReportService} (the same planning step as the web application),
 * follow its status until Done or Failed on a named polling thread (stopped when the screen closes),
 * list the user's reports from the database, and save a copy of a finished report. The copy is made
 * from the file the owner-checked download service returns, so another user's report looks missing.
 */
public final class ReportsPresenter {

    /** Name of the status polling thread. */
    public static final String THREAD_NAME = "logiopt-report-status";
    /** Time between two status checks. */
    public static final Duration STATUS_PERIOD = Duration.ofSeconds(1);
    /** Newest reports shown. */
    public static final int LIMIT = 25;
    /** Shown when the copy cannot be written. */
    public static final String COPY_FAILED = "The copy could not be saved there. Choose another folder and try again.";

    private static final Logger LOG = Logger.getLogger(ReportsPresenter.class.getName());

    /** What the reports page can show. */
    public interface View {
        /** Shows a loading state. */
        void showLoading();

        /** @param model shipments, strategies and the user's reports */
        void showReports(ReportsModel model);

        /** @param message error state text */
        void showError(String message);

        /** @param progress status of the report being generated */
        void showProgress(ReportProgress progress);

        /** @param message result of an action, or why it failed */
        void showMessage(String message);

        /** @param busy true while a report is being generated */
        void setBusy(boolean busy);
    }

    private final AppServices services;
    private final BackgroundRunner runner;
    private final User user;
    private final Executor ui;
    private final Duration statusPeriod;
    private final View view;
    private volatile Poller status;
    private boolean busy;
    private volatile boolean closed;

    /**
     * @param services     shared services
     * @param runner       background runner
     * @param user         signed-in user
     * @param ui           runs status updates on the UI thread
     * @param statusPeriod time between two status checks
     * @param view         page
     */
    public ReportsPresenter(AppServices services, BackgroundRunner runner, User user, Executor ui,
                            Duration statusPeriod, View view) {
        this.services = services;
        this.runner = runner;
        this.user = user;
        this.ui = ui;
        this.statusPeriod = statusPeriod;
        this.view = view;
    }

    /** Loads the shipments and reports in the background. */
    public void load() {
        view.showLoading();
        runner.run(this::model, view::showReports, error -> view.showError(ErrorMessages.of(error)));
    }

    private ReportsModel model() throws Exception {
        return ViewModels.reports(services.getShipmentDao().findByUser(user.getUserId()),
                services.getReportService().history(user.getUserId(), LIMIT), LIMIT);
    }

    /**
     * Starts a report for one of the user's shipments (a past-dated one too) and follows its status.
     *
     * @param shipmentId  shipment id
     * @param strategyKey CHEAPEST, FASTEST, GREENEST or BALANCED
     */
    public void generate(long shipmentId, String strategyKey) {
        if (busy || closed) {
            return;
        }
        busy = true;
        view.setBusy(true);
        runner.run(() -> {
            Shipment shipment = services.getShipmentDao().findByIdForUser(shipmentId, user.getUserId())
                    .orElseThrow(() -> new NotFoundException("Shipment " + shipmentId));
            PlanningResult result = services.getPlanningService().plan(shipment, services.getRouteDao()
                    .findBySourceAndDestination(shipment.getSource(), shipment.getDestination()), strategyKey);
            return services.getReportService().submit(result).getJobId();
        }, this::follow, error -> {
            busy = false;
            view.setBusy(false);
            view.showMessage(ErrorMessages.of(error));
        });
    }

    private void follow(String jobId) {
        if (closed) {
            return;
        }
        Poller poller = new Poller(THREAD_NAME, statusPeriod, () -> check(jobId));
        status = poller;
        poller.start();
    }

    /** One status check, on the polling thread. */
    private void check(String jobId) {
        Optional<ReportRecord> record;
        try {
            record = services.getReportService().getJob(jobId, user.getUserId());
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Report status check failed; trying again", e);
            return;
        }
        if (record.isEmpty()) {
            ui.execute(() -> finished(ErrorMessages.NOT_FOUND, null));
            return;
        }
        ReportProgress progress = ViewModels.progress(record.get());
        ui.execute(() -> {
            if (!closed) {
                view.showProgress(progress);
                if (progress.finished()) {
                    finished(null, progress);
                }
            }
        });
    }

    private void finished(String message, ReportProgress progress) {
        stopStatus();
        busy = false;
        if (closed) {
            return;
        }
        view.setBusy(false);
        if (message != null) {
            view.showMessage(message);
        }
        runner.run(this::model, model -> {
            if (!closed) {
                view.showReports(model);
                if (progress != null) {
                    view.showProgress(progress);
                }
            }
        }, error -> view.showMessage(ErrorMessages.of(error)));
    }

    /**
     * Copies a finished report of the user to {@code target}, byte for byte.
     *
     * @param jobId  report job id
     * @param target file chosen in the save dialog
     */
    public void saveCopy(String jobId, Path target) {
        if (target == null) {
            return;
        }
        runner.run(() -> {
            ReportDownload download = services.getReportService().download(jobId, user.getUserId())
                    .orElseThrow(() -> new NotFoundException("Report " + jobId));
            try {
                Files.copy(download.getFile(), target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Saving a copy of a report failed", e);
                throw new CopyFailed();
            }
            return target.getFileName().toString();
        }, name -> view.showMessage("Saved a copy as " + name + "."),
                error -> view.showMessage(error instanceof CopyFailed ? COPY_FAILED : ErrorMessages.of(error)));
    }

    /** The copy could not be written (already logged). */
    private static final class CopyFailed extends Exception {
        private static final long serialVersionUID = 1L;
    }

    /**
     * @param row a finished report
     * @return the file name offered in the save dialog
     */
    public static String suggestedName(ReportJobRow row) {
        String strategy = String.valueOf(row.strategy()).replaceAll("[^A-Za-z]", "").toLowerCase(Locale.ROOT);
        return "logiopt-report-shipment-" + row.shipmentId() + (strategy.isEmpty() ? "" : "-" + strategy) + ".txt";
    }

    /** @return true while a status poll is running */
    public boolean isFollowing() {
        Poller current = status;
        return current != null && current.isRunning();
    }

    /** Stops the status polling; called when the screen closes. Safe to call twice. */
    public void close() {
        closed = true;
        stopStatus();
    }

    private void stopStatus() {
        Poller current = status;
        status = null;
        if (current != null) {
            current.stop();
        }
    }
}

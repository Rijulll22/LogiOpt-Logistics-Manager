package com.logiopt.desktop.presenter;

import com.logiopt.desktop.presenter.Models.AdminTrackingRow;
import com.logiopt.desktop.support.BackgroundRunner;
import com.logiopt.desktop.support.ErrorMessages;
import com.logiopt.model.Booking;
import com.logiopt.model.User;
import com.logiopt.service.TrackingService;

import java.util.ArrayList;
import java.util.List;

/**
 * The administrator's tracking screen: confirmed, undelivered bookings, each with only the single
 * legal next milestone. Record adds it with a required description (source ADMIN); Advance
 * (simulated) runs the same idempotent service step as the web button. Both send the milestone the
 * screen was showing, so a stale screen or a second click records nothing and says so. Anyone but the
 * administrator is refused here and again by {@link TrackingService}.
 */
public final class AdminTrackingPresenter {

    /** Shown when the screen was out of date. */
    public static final String STALE = "This screen was out of date: tracking had already moved on, so nothing was "
            + "recorded. The list now shows the current state.";

    /** What the tracking screen can show. */
    public interface View {
        /** Shows a loading state. */
        void showLoading();

        /** @param rows confirmed, undelivered bookings */
        void showRows(List<AdminTrackingRow> rows);

        /** @param message error state text */
        void showError(String message);

        /**
         * @param success true if a milestone was recorded
         * @param message what happened
         */
        void showMessage(boolean success, String message);

        /** @param busy true while a change runs (the buttons are disabled) */
        void setBusy(boolean busy);
    }

    private final TrackingService tracking;
    private final BackgroundRunner runner;
    private final User admin;
    private final View view;
    private boolean busy;

    /**
     * @param tracking tracking service
     * @param runner   background runner
     * @param admin    signed-in user (must be the administrator)
     * @param view     page
     */
    public AdminTrackingPresenter(TrackingService tracking, BackgroundRunner runner, User admin, View view) {
        this.tracking = tracking;
        this.runner = runner;
        this.admin = admin;
        this.view = view;
    }

    /** Loads the rows in the background. */
    public void load() {
        if (!admin.isAdmin()) {
            view.showError(ErrorMessages.NOT_FOUND);
            return;
        }
        view.showLoading();
        runner.run(this::rows, view::showRows, error -> view.showError(ErrorMessages.of(error)));
    }

    private List<AdminTrackingRow> rows() throws Exception {
        List<AdminTrackingRow> rows = new ArrayList<>();
        for (Booking b : tracking.confirmedBookings(admin)) {
            AdminTrackingRow row = ViewModels.adminTrackingRow(b, tracking.eventsForAdmin(admin, b.getBookingId()));
            if (row != null) {
                rows.add(row);
            }
        }
        return rows;
    }

    /**
     * Records the row's next milestone with a description (source ADMIN).
     *
     * @param row         row as shown
     * @param description required description
     */
    public void record(AdminTrackingRow row, String description) {
        change(row, () -> tracking.record(admin, row.bookingId(), TrackingService.parseMilestone(row.currentKey()),
                TrackingService.parseMilestone(row.nextKey()), description));
    }

    /** @param row row as shown; records its next milestone through the idempotent simulation step */
    public void advance(AdminTrackingRow row) {
        change(row, () -> tracking.advance(admin, row.bookingId(), TrackingService.parseMilestone(row.currentKey())));
    }

    /** A tracking call done in the background. */
    @FunctionalInterface
    private interface Step {
        TrackingService.Result run() throws Exception;
    }

    /** Outcome of a change and the reloaded rows. */
    private record Outcome(TrackingService.Result result, List<AdminTrackingRow> rows) {
    }

    private void change(AdminTrackingRow row, Step step) {
        if (!admin.isAdmin()) {
            view.showMessage(false, ErrorMessages.NOT_FOUND);
            return;
        }
        if (busy) {
            return;
        }
        busy = true;
        view.setBusy(true);
        runner.run(() -> new Outcome(step.run(), rows()), outcome -> {
            busy = false;
            view.setBusy(false);
            view.showRows(outcome.rows());
            boolean recorded = outcome.result().getOutcome() == TrackingService.Outcome.RECORDED;
            view.showMessage(recorded, recorded ? "Booking #" + row.bookingId() + ": "
                    + outcome.result().getCurrent().getLabel() + " recorded." : STALE);
        }, error -> {
            busy = false;
            view.setBusy(false);
            view.showMessage(false, ErrorMessages.of(error));
        });
    }
}

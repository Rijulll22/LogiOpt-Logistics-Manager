package com.logiopt.desktop.presenter;

import com.logiopt.desktop.presenter.Models.DashboardModel;
import com.logiopt.desktop.support.BackgroundRunner;
import com.logiopt.desktop.support.ErrorMessages;
import com.logiopt.model.User;
import com.logiopt.service.DashboardService;

/**
 * Loads the customer's own counts, or the system-wide counts for the administrator, through
 * {@link DashboardService} (which checks the role itself).
 */
public final class DashboardPresenter {

    /** Same text as the web dashboard's error state. */
    public static final String UNAVAILABLE = "The dashboard could not be loaded because the database is unavailable. "
            + "Please try again in a moment.";

    /** What the dashboard page can show. */
    public interface View {
        /** Shows a loading state. */
        void showLoading();

        /** @param model counts and tracking rows */
        void showDashboard(DashboardModel model);

        /** @param message error state text */
        void showError(String message);
    }

    private final DashboardService dashboards;
    private final BackgroundRunner runner;
    private final View view;
    private final User user;

    /**
     * @param dashboards dashboard service
     * @param runner     background runner
     * @param user       signed-in user
     * @param view       page
     */
    public DashboardPresenter(DashboardService dashboards, BackgroundRunner runner, User user, View view) {
        this.dashboards = dashboards;
        this.runner = runner;
        this.user = user;
        this.view = view;
    }

    /** Loads the counts in the background. */
    public void load() {
        view.showLoading();
        runner.run(() -> user.isAdmin() ? ViewModels.adminDashboard(dashboards.admin(user))
                : ViewModels.customerDashboard(dashboards.customer(user), user), view::showDashboard, error -> {
                    String message = ErrorMessages.of(error);
                    view.showError(ErrorMessages.DATABASE_UNAVAILABLE.equals(message) ? UNAVAILABLE : message);
                });
    }
}

package com.logiopt.service;

import com.logiopt.dao.DashboardDAO;
import com.logiopt.exception.DataAccessException;
import com.logiopt.model.AdminDashboard;
import com.logiopt.model.CustomerDashboard;
import com.logiopt.model.TrackingSummary;
import com.logiopt.model.User;

import java.util.List;

/**
 * Dashboards with real counts from the database: a customer sees only their own shipments,
 * bookings, unread notifications and tracking; the administrator sees system-wide counts.
 */
public class DashboardService {

    /** Maximum number of confirmed bookings whose latest milestone the customer dashboard lists. */
    public static final int TRACKING_LIMIT = 20;

    private final DashboardDAO dashboards;
    private final NotificationService notifications;

    /**
     * Creates the service.
     *
     * @param dashboards    aggregate queries
     * @param notifications unread counts
     */
    public DashboardService(DashboardDAO dashboards, NotificationService notifications) {
        if (dashboards == null || notifications == null) {
            throw new IllegalArgumentException("Dashboard dependencies must not be null");
        }
        this.dashboards = dashboards;
        this.notifications = notifications;
    }

    /**
     * @param user signed-in customer
     * @return the customer's dashboard
     * @throws DataAccessException on database failure
     */
    public CustomerDashboard customer(User user) throws DataAccessException {
        List<TrackingSummary> tracking = dashboards.latestTracking(user.getUserId(), TRACKING_LIMIT + 1);
        boolean truncated = tracking.size() > TRACKING_LIMIT;
        return new CustomerDashboard(dashboards.shipmentCounts(user.getUserId()),
                dashboards.bookingCounts(user.getUserId()), notifications.unreadCount(user),
                truncated ? tracking.subList(0, TRACKING_LIMIT) : tracking, TRACKING_LIMIT, truncated);
    }

    /**
     * @param admin signed-in administrator
     * @return system-wide counts
     * @throws DataAccessException on database failure
     * @throws SecurityException   if the user is not the administrator
     */
    public AdminDashboard admin(User admin) throws DataAccessException {
        if (admin == null || !admin.isAdmin()) {
            throw new SecurityException("Administrator only");
        }
        return new AdminDashboard(dashboards.userCounts(), dashboards.allBookingCounts(),
                dashboards.confirmedAwaitingTracking());
    }
}

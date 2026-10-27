package com.logiopt.app;

import com.logiopt.dao.RouteDAO;
import com.logiopt.dao.ShipmentDAO;
import com.logiopt.report.ReportService;
import com.logiopt.service.AuthService;
import com.logiopt.service.BookingService;
import com.logiopt.service.DashboardService;
import com.logiopt.service.HistoryService;
import com.logiopt.service.NotificationService;
import com.logiopt.service.PaymentService;
import com.logiopt.service.PlanningService;
import com.logiopt.service.RecommendationService;
import com.logiopt.service.ShipmentService;
import com.logiopt.service.ShipmentValidator;
import com.logiopt.service.TrackingService;

/**
 * Application singletons created once by {@link CoreRuntime} and shared by every front end (the web
 * application keeps this object in its servlet context under {@link #ATTRIBUTE}).
 */
public final class AppServices {

    /** Servlet context attribute holding this object. */
    public static final String ATTRIBUTE = "logiopt.services";

    private final ShipmentDAO shipmentDao;
    private final RouteDAO routeDao;
    private final ShipmentValidator validator;
    private final RecommendationService recommendationService;
    private final PlanningService planningService;
    private final ReportService reportService;
    private final AuthService authService;
    private final SchemaGate schemaGate;
    private final BookingService bookingService;
    private final PaymentService paymentService;
    private final TrackingService trackingService;
    private final NotificationService notificationService;
    private final ShipmentService shipmentService;
    private final DashboardService dashboardService;
    private final HistoryService historyService;

    /**
     * Creates the holder.
     *
     * @param shipmentDao           shipment DAO
     * @param routeDao              route DAO
     * @param validator             shipment validator
     * @param recommendationService strategy registry
     * @param planningService       planning service
     * @param reportService         background report writer
     * @param authService           registration and sign-in
     * @param schemaGate            start-up schema version check
     * @param bookingService        bookings
     * @param paymentService        simulated payments
     * @param trackingService       tracking milestones
     * @param notificationService   in-app notifications
     * @param shipmentService       draft editing
     * @param dashboardService      dashboards
     * @param historyService        shipment, booking and report history
     */
    public AppServices(ShipmentDAO shipmentDao, RouteDAO routeDao, ShipmentValidator validator,
                       RecommendationService recommendationService, PlanningService planningService,
                       ReportService reportService, AuthService authService, SchemaGate schemaGate,
                       BookingService bookingService, PaymentService paymentService,
                       TrackingService trackingService, NotificationService notificationService,
                       ShipmentService shipmentService, DashboardService dashboardService,
                       HistoryService historyService) {
        this.shipmentDao = shipmentDao;
        this.routeDao = routeDao;
        this.validator = validator;
        this.recommendationService = recommendationService;
        this.planningService = planningService;
        this.reportService = reportService;
        this.authService = authService;
        this.schemaGate = schemaGate;
        this.bookingService = bookingService;
        this.paymentService = paymentService;
        this.trackingService = trackingService;
        this.notificationService = notificationService;
        this.shipmentService = shipmentService;
        this.dashboardService = dashboardService;
        this.historyService = historyService;
    }

    /** @return draft editing */
    public ShipmentService getShipmentService() {
        return shipmentService;
    }

    /** @return dashboards */
    public DashboardService getDashboardService() {
        return dashboardService;
    }

    /** @return shipment, booking and report history */
    public HistoryService getHistoryService() {
        return historyService;
    }

    /** @return tracking milestones */
    public TrackingService getTrackingService() {
        return trackingService;
    }

    /** @return in-app notifications */
    public NotificationService getNotificationService() {
        return notificationService;
    }

    /** @return bookings */
    public BookingService getBookingService() {
        return bookingService;
    }

    /** @return simulated payments */
    public PaymentService getPaymentService() {
        return paymentService;
    }

    /** @return shipment DAO */
    public ShipmentDAO getShipmentDao() {
        return shipmentDao;
    }

    /** @return route DAO */
    public RouteDAO getRouteDao() {
        return routeDao;
    }

    /** @return shipment validator */
    public ShipmentValidator getValidator() {
        return validator;
    }

    /** @return strategy registry */
    public RecommendationService getRecommendationService() {
        return recommendationService;
    }

    /** @return planning service */
    public PlanningService getPlanningService() {
        return planningService;
    }

    /** @return background report writer */
    public ReportService getReportService() {
        return reportService;
    }

    /** @return registration and sign-in */
    public AuthService getAuthService() {
        return authService;
    }

    /** @return start-up schema version check */
    public SchemaGate getSchemaGate() {
        return schemaGate;
    }
}

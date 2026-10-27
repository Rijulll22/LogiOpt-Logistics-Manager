package com.logiopt.desktop.presenter;

import com.logiopt.app.AppServices;
import com.logiopt.desktop.presenter.Models.ComparisonModel;
import com.logiopt.desktop.presenter.Models.OrderSummaryModel;
import com.logiopt.desktop.support.BackgroundRunner;
import com.logiopt.desktop.support.ErrorMessages;
import com.logiopt.exception.NotFoundException;
import com.logiopt.model.Booking;
import com.logiopt.model.PlanningResult;
import com.logiopt.model.Recommendation;
import com.logiopt.model.RouteEvaluation;
import com.logiopt.model.Shipment;
import com.logiopt.model.User;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Route comparison of one of the user's shipments: the planning engine runs for the shipment's
 * preferred strategy, every strategy ranks the same evaluations, and Book opens "Review your order".
 * Only Confirm calls BookingService.book, with the shipment, route and strategy only, so the
 * server-side snapshot logic is reused. All numbers come from the services.
 */
public final class ComparisonPresenter {

    /** What the comparison page can show. */
    public interface View {
        /** Shows a loading state. */
        void showLoading();

        /** @param model the comparison */
        void showComparison(ComparisonModel model);

        /** @param message error state text */
        void showError(String message);

        /** @param model "Review your order" for the chosen route */
        void showOrderSummary(OrderSummaryModel model);

        /** @param busy true while the booking is being made (Confirm disabled) */
        void setBookingBusy(boolean busy);

        /** @param message why the booking failed (shown in the order summary) */
        void showBookingError(String message);
    }

    /** What load() found, kept for review(). */
    private record Loaded(Shipment shipment, PlanningResult result, ComparisonModel model) {
    }

    private final AppServices services;
    private final BackgroundRunner runner;
    private final User user;
    private final Consumer<Long> onBooked;
    private final View view;
    private Loaded loaded;
    private boolean booking;

    /**
     * @param services shared services
     * @param runner   background runner
     * @param user     signed-in user
     * @param onBooked receives the new booking id
     * @param view     page
     */
    public ComparisonPresenter(AppServices services, BackgroundRunner runner, User user, Consumer<Long> onBooked,
                               View view) {
        this.services = services;
        this.runner = runner;
        this.user = user;
        this.onBooked = onBooked;
        this.view = view;
    }

    /** @param shipmentId one of the user's shipments (another user's looks missing) */
    public void load(long shipmentId) {
        view.showLoading();
        runner.run(() -> compute(shipmentId), result -> {
            loaded = result;
            view.showComparison(result.model());
        }, error -> view.showError(ErrorMessages.of(error)));
    }

    private Loaded compute(long shipmentId) throws Exception {
        Shipment shipment = services.getShipmentDao().findByIdForUser(shipmentId, user.getUserId())
                .orElseThrow(() -> new NotFoundException("Shipment " + shipmentId));
        PlanningResult result = services.getPlanningService().plan(shipment, services.getRouteDao()
                .findBySourceAndDestination(shipment.getSource(), shipment.getDestination()),
                shipment.getPreference().getStrategyKey());
        Map<String, Recommendation> byStrategy = new LinkedHashMap<>();
        for (String key : ViewModels.STRATEGY_ORDER) {
            byStrategy.put(key, services.getRecommendationService().recommend(key, result.getEvaluations()));
        }
        Optional<Booking> active = services.getBookingService().activeBooking(user, shipmentId);
        boolean dateBookable = services.getBookingService().isDispatchDateBookable(shipment);
        return new Loaded(shipment, result, ViewModels.comparison(shipment, result, byStrategy, active, dateBookable));
    }

    /**
     * Opens "Review your order" for an eligible route; does nothing while booking is not possible.
     *
     * @param routeId route of the clicked Book button
     */
    public void review(long routeId) {
        if (loaded == null || !loaded.model().bookable()) {
            return;
        }
        for (RouteEvaluation e : loaded.result().getEvaluations()) {
            if (e.getRouteId() == routeId) {
                view.showOrderSummary(ViewModels.orderSummary(loaded.shipment(), e));
                return;
            }
        }
    }

    /**
     * Books the route. A second call while the first runs does nothing.
     *
     * @param routeId     route
     * @param strategyKey strategy the booking is recorded under
     */
    public void confirm(long routeId, String strategyKey) {
        if (booking || loaded == null) {
            return;
        }
        booking = true;
        long shipmentId = loaded.shipment().getShipmentId();
        view.showBookingError(null);
        view.setBookingBusy(true);
        runner.run(() -> services.getBookingService().book(user, shipmentId, routeId, strategyKey), id -> {
            booking = false;
            view.setBookingBusy(false);
            onBooked.accept(id);
        }, error -> {
            booking = false;
            view.setBookingBusy(false);
            view.showBookingError(ErrorMessages.of(error));
        });
    }

    /** @return true while a booking call runs */
    public boolean isBooking() {
        return booking;
    }
}

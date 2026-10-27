package com.logiopt.desktop.presenter;

import com.logiopt.dao.ShipmentDAO;
import com.logiopt.desktop.presenter.Models.ShipmentRow;
import com.logiopt.desktop.support.BackgroundRunner;
import com.logiopt.desktop.support.ErrorMessages;
import com.logiopt.model.User;

import java.util.List;

/** The signed-in user's own shipments (the DAO filters by owner, as on the web list). */
public final class ShipmentListPresenter {

    /** What the shipments page can show. */
    public interface View {
        /** Shows a loading state. */
        void showLoading();

        /** @param rows the user's shipments */
        void showShipments(List<ShipmentRow> rows);

        /** @param message error state text */
        void showError(String message);
    }

    private final ShipmentDAO shipments;
    private final BackgroundRunner runner;
    private final User user;
    private final View view;

    /**
     * @param shipments shipment DAO
     * @param runner    background runner
     * @param user      signed-in user
     * @param view      page
     */
    public ShipmentListPresenter(ShipmentDAO shipments, BackgroundRunner runner, User user, View view) {
        this.shipments = shipments;
        this.runner = runner;
        this.user = user;
        this.view = view;
    }

    /** Loads the list in the background. */
    public void load() {
        view.showLoading();
        runner.run(() -> ViewModels.shipmentRows(shipments.findByUser(user.getUserId())), view::showShipments,
                error -> view.showError(ErrorMessages.of(error)));
    }
}

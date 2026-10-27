package com.logiopt.desktop.presenter;

import com.logiopt.app.AppServices;
import com.logiopt.app.ShipmentFormInput;
import com.logiopt.desktop.presenter.Models.ShipmentFormModel;
import com.logiopt.desktop.support.BackgroundRunner;
import com.logiopt.desktop.support.ErrorMessages;
import com.logiopt.exception.InvalidShipmentException;
import com.logiopt.exception.NotFoundException;
import com.logiopt.exception.ShipmentNotEditableException;
import com.logiopt.model.CargoType;
import com.logiopt.model.Shipment;
import com.logiopt.model.User;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * New-shipment and edit-draft form. Input is parsed by the shared {@link ShipmentFormInput} and
 * validated by the same ShipmentValidator as the web form (future date, weight bounds, known cities);
 * editing goes through ShipmentService, which checks the owner and the DRAFT rule.
 */
public final class ShipmentFormPresenter {

    /** Shown under the weight field (the DECIMAL(12,3) limits). */
    public static final String WEIGHT_HINT = "0.001 to 999,999,999.999 kg, at most 3 decimal places";

    /** What the form page can show. */
    public interface View {
        /** Shows a loading state. */
        void showLoading();

        /** @param model fields, choices and values */
        void showForm(ShipmentFormModel model);

        /** @param errors messages by field name (ShipmentFormInput.GENERAL for the rest); empty clears them */
        void showFieldErrors(Map<String, List<String>> errors);

        /** @param busy true while saving */
        void setBusy(boolean busy);

        /** @param message why the form cannot be used (not found, not editable, database down) */
        void showUnavailable(String message);
    }

    /** Result of a save attempt: either errors or the saved id. */
    private static final class Outcome {
        private final List<String> errors;
        private final long shipmentId;

        Outcome(List<String> errors, long shipmentId) {
            this.errors = errors;
            this.shipmentId = shipmentId;
        }
    }

    private final AppServices services;
    private final BackgroundRunner runner;
    private final User user;
    private final Consumer<Long> onSaved;
    private final View view;
    private Consumer<Long> onSavedAndCompare;
    private Long editId;

    /**
     * @param services shared services
     * @param runner   background runner
     * @param user     signed-in user (the owner of every saved shipment)
     * @param onSaved  receives the saved shipment id
     * @param view     form page
     */
    public ShipmentFormPresenter(AppServices services, BackgroundRunner runner, User user, Consumer<Long> onSaved,
                                 View view) {
        this.services = services;
        this.runner = runner;
        this.user = user;
        this.onSaved = onSaved;
        this.view = view;
    }

    /** Loads the city list for an empty form. */
    public void loadNew() {
        editId = null;
        view.showLoading();
        runner.run(() -> model(null, Map.of(), services.getRouteDao().knownCities()), view::showForm, this::unavailable);
    }

    /**
     * Loads one of the user's DRAFT shipments for editing.
     *
     * @param shipmentId shipment id
     */
    public void loadEdit(long shipmentId) {
        editId = shipmentId;
        view.showLoading();
        runner.run(() -> {
            Shipment draft = services.getShipmentService().editableDraft(user, shipmentId);
            return model(shipmentId, ShipmentFormInput.valuesOf(draft), services.getRouteDao().knownCities());
        }, view::showForm, this::unavailable);
    }

    /** @param action receives the saved id after "Save and compare routes" */
    public void onSavedAndCompare(Consumer<Long> action) {
        this.onSavedAndCompare = action;
    }

    /**
     * Validates and saves, then opens the comparison of the saved shipment.
     *
     * @param raw field values by field name (see ShipmentFormInput.FIELDS and PREFERENCE)
     */
    public void submitAndCompare(Map<String, String> raw) {
        save(raw, onSavedAndCompare == null ? onSaved : onSavedAndCompare);
    }

    /**
     * Validates and saves the entered values ("Save as draft" / "Save changes").
     *
     * @param raw field values by field name (see ShipmentFormInput.FIELDS and PREFERENCE)
     */
    public void submit(Map<String, String> raw) {
        save(raw, onSaved);
    }

    private void save(Map<String, String> raw, Consumer<Long> then) {
        Long editing = editId;
        Map<String, String> values = Map.copyOf(raw);
        view.showFieldErrors(Map.of());
        view.setBusy(true);
        runner.run(() -> save(editing, values), outcome -> {
            view.setBusy(false);
            if (outcome.errors.isEmpty()) {
                then.accept(outcome.shipmentId);
            } else {
                view.showFieldErrors(ShipmentFormInput.byField(outcome.errors));
            }
        }, error -> {
            view.setBusy(false);
            if (error instanceof NotFoundException || error instanceof ShipmentNotEditableException) {
                view.showUnavailable(ErrorMessages.of(error));
            } else {
                view.showFieldErrors(Map.of(ShipmentFormInput.GENERAL, List.of(ErrorMessages.of(error))));
            }
        });
    }

    private Outcome save(Long editing, Map<String, String> values) throws Exception {
        if (editing != null) {
            services.getShipmentService().editableDraft(user, editing);
        }
        List<String> cities = services.getRouteDao().knownCities();
        ShipmentFormInput input = ShipmentFormInput.parse(values::get, user.getUserId());
        Set<String> errors = new LinkedHashSet<>(input.getParseErrors());
        try {
            services.getValidator().validate(input.getShipment(), cities);
        } catch (InvalidShipmentException e) {
            errors.addAll(e.getErrors());
        }
        if (!errors.isEmpty()) {
            return new Outcome(new ArrayList<>(errors), 0);
        }
        if (editing == null) {
            return new Outcome(List.of(), services.getShipmentDao().insert(input.getShipment()));
        }
        try {
            services.getShipmentService().updateDraft(user, editing, input.getShipment(), cities);
        } catch (InvalidShipmentException e) {
            errors.addAll(e.getErrors());
            return new Outcome(new ArrayList<>(errors), 0);
        }
        return new Outcome(List.of(), editing);
    }

    private void unavailable(Throwable error) {
        view.showUnavailable(ErrorMessages.of(error));
    }

    /** @return the form model for a new (editId null) or edited shipment */
    static ShipmentFormModel model(Long editId, Map<String, String> values, List<String> cities) {
        List<String> cargo = new ArrayList<>();
        for (CargoType type : CargoType.values()) {
            cargo.add(type.name());
        }
        if (editId == null) {
            return new ShipmentFormModel("New shipment", "Choose the cities served by a configured route and what "
                    + "matters most, then save it as a draft or compare routes.", null, cities, cargo, values,
                    WEIGHT_HINT, "Save as draft", ViewModels.preferenceOptions());
        }
        return new ShipmentFormModel("Edit shipment #" + editId, "Only draft shipments can be edited. Booked, cancelled "
                + "and completed shipments stay as they are.", editId, cities, cargo, values, WEIGHT_HINT, "Save changes",
                ViewModels.preferenceOptions());
    }
}

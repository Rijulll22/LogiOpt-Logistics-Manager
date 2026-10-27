package com.logiopt.service;

import com.logiopt.dao.ShipmentDAO;
import com.logiopt.exception.DataAccessException;
import com.logiopt.exception.InvalidShipmentException;
import com.logiopt.exception.NotFoundException;
import com.logiopt.exception.ShipmentNotEditableException;
import com.logiopt.model.Shipment;
import com.logiopt.model.ShipmentStatus;
import com.logiopt.model.User;

import java.util.Collection;

/**
 * Draft editing. A shipment can be edited only by its owner and only while it is DRAFT, with the
 * same validation as creation (future dispatch date, weight bounds, known cities). A shipment that
 * is booked, planned, cancelled or completed is refused with a clear message and nothing is
 * changed; a shipment returned to DRAFT by cancelling an unpaid booking is editable again.
 * Bookings keep their own snapshot rows, which an edit never touches.
 */
public class ShipmentService {

    private final ShipmentDAO shipments;
    private final ShipmentValidator validator;

    /**
     * Creates the service.
     *
     * @param shipments shipment DAO
     * @param validator the validator used for creation
     */
    public ShipmentService(ShipmentDAO shipments, ShipmentValidator validator) {
        if (shipments == null || validator == null) {
            throw new IllegalArgumentException("Shipment dependencies must not be null");
        }
        this.shipments = shipments;
        this.validator = validator;
    }

    /**
     * Loads a shipment of the user for editing.
     *
     * @param user       signed-in user
     * @param shipmentId shipment id
     * @return the draft shipment
     * @throws NotFoundException            if missing or someone else's
     * @throws ShipmentNotEditableException if it is not a draft
     * @throws DataAccessException          on database failure
     */
    public Shipment editableDraft(User user, long shipmentId)
            throws NotFoundException, ShipmentNotEditableException, DataAccessException {
        Shipment shipment = shipments.findByIdForUser(shipmentId, user.getUserId())
                .orElseThrow(() -> new NotFoundException("Shipment " + shipmentId));
        requireDraft(shipmentId, shipment.getStatus());
        return shipment;
    }

    /**
     * Saves new field values of a draft. The id is the edited shipment's; the owner is the signed-in
     * user, never a request value.
     *
     * @param user        signed-in user
     * @param shipmentId  shipment id
     * @param values      new values (id, owner and status are ignored)
     * @param knownCities cities served by at least one route
     * @throws NotFoundException            if missing or someone else's
     * @throws ShipmentNotEditableException if it is not (or no longer) a draft
     * @throws InvalidShipmentException     if the new values are invalid
     * @throws DataAccessException          on database failure
     */
    public void updateDraft(User user, long shipmentId, Shipment values, Collection<String> knownCities)
            throws NotFoundException, ShipmentNotEditableException, InvalidShipmentException, DataAccessException {
        editableDraft(user, shipmentId);
        Shipment edited = new Shipment(shipmentId, user.getUserId(), values.getSource(), values.getDestination(),
                values.getCargoType(), values.getWeightKg(), values.getDimensions(), values.getDispatchDate(),
                ShipmentStatus.DRAFT, values.hasPreference() ? values.getPreference() : null);
        validator.validate(edited, knownCities);
        ShipmentDAO.UpdateResult result = shipments.updateDraft(edited);
        switch (result.getOutcome()) {
            case UPDATED:
                return;
            case NOT_FOUND:
                throw new NotFoundException("Shipment " + shipmentId);
            default:
                requireDraft(shipmentId, result.getStatus());
                throw new ShipmentNotEditableException(message(shipmentId, ShipmentStatus.BOOKED));
        }
    }

    private static void requireDraft(long shipmentId, ShipmentStatus status) throws ShipmentNotEditableException {
        if (status != ShipmentStatus.DRAFT) {
            throw new ShipmentNotEditableException(message(shipmentId, status));
        }
    }

    /**
     * @param shipmentId shipment id
     * @param status     current status
     * @return why the shipment cannot be edited
     */
    static String message(long shipmentId, ShipmentStatus status) {
        String prefix = "Shipment " + shipmentId + " cannot be edited because ";
        switch (status) {
            case BOOKED:
                return prefix + "it is booked. Only draft shipments can be edited; cancelling an unpaid booking "
                        + "makes the shipment a draft again.";
            case CANCELLED:
                return prefix + "it is cancelled. Only draft shipments can be edited.";
            case COMPLETED:
                return prefix + "it is completed (delivered). Only draft shipments can be edited.";
            default:
                return prefix + "its status is " + status + ". Only draft shipments can be edited.";
        }
    }
}

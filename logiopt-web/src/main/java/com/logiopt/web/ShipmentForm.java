package com.logiopt.web;

import com.logiopt.app.ShipmentFormInput;
import com.logiopt.model.Shipment;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The shipment form's posted fields, parsed by the shared {@link ShipmentFormInput} (the desktop
 * client parses its form the same way). Keeps the raw values for redisplay (request attribute "form")
 * and the errors found while parsing; business validation is the ShipmentValidator's job.
 */
final class ShipmentForm {

    /** Request attribute holding the field values shown in the form. */
    static final String ATTRIBUTE = "form";
    /** Maximum length of the text fields. */
    static final int MAX_TEXT_LENGTH = ShipmentFormInput.MAX_TEXT_LENGTH;

    private final ShipmentFormInput input;

    private ShipmentForm(ShipmentFormInput input) {
        this.input = input;
    }

    /**
     * Parses the posted fields. The owner always comes from the session, never from the request.
     *
     * @param req         request
     * @param ownerUserId signed-in user
     * @return parsed form
     */
    static ShipmentForm fromRequest(HttpServletRequest req, long ownerUserId) {
        return new ShipmentForm(ShipmentFormInput.parse(req::getParameter, ownerUserId));
    }

    /**
     * @return values of an empty form
     */
    static Map<String, String> emptyValues() {
        return Collections.emptyMap();
    }

    /**
     * @param s saved shipment
     * @return its values as shown in the form (weight without trailing zeros, never rounded)
     */
    static Map<String, String> valuesOf(Shipment s) {
        return ShipmentFormInput.valuesOf(s);
    }

    /** @return raw values for redisplay */
    Map<String, String> getValues() {
        return input.getValues();
    }

    /** @return errors found while parsing */
    List<String> getParseErrors() {
        return input.getParseErrors();
    }

    /** @return the parsed shipment (id 0, status DRAFT) */
    Shipment getShipment() {
        return input.getShipment();
    }
}

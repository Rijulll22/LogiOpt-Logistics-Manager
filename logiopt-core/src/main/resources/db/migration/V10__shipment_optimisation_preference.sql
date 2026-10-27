-- V10: the customer's optimisation preference per shipment (desktop New/Edit shipment form).
-- Nullable: shipments saved before V10, and shipments saved by the web form, have none, and the
-- application treats a missing value as BALANCED. The CHECK allows only the four strategy keys, in
-- capitals exactly (binary collation), so every stored value maps back to the Java enum.
-- No existing row is changed.

ALTER TABLE shipments
    ADD COLUMN optimisation_preference VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER status,
    ADD CONSTRAINT chk_shipments_preference
        CHECK (optimisation_preference IS NULL
            OR optimisation_preference IN ('CHEAPEST', 'FASTEST', 'GREENEST', 'BALANCED'));

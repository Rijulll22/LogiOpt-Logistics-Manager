-- V5: bookings with an immutable route snapshot, and simulated payment attempts (Review 3 Phase 3).
-- The V1 bookings and payments tables are empty in Review 2 databases; a row with an old status
-- such as 'BOOKED' makes the CHECK below fail and the runner stop, so nothing is reinterpreted.
--
-- Booking states: PENDING_PAYMENT -> CONFIRMED (on a PAID attempt) or -> CANCELLED (unpaid only).
-- active_shipment_id is the shipment id while a booking is PENDING_PAYMENT or CONFIRMED and NULL
-- otherwise, so its UNIQUE key allows one active booking per shipment (MySQL has no partial index).
-- The snapshot columns are written once on INSERT and never updated by the application.

ALTER TABLE bookings
    MODIFY COLUMN status VARCHAR(20) NOT NULL DEFAULT 'PENDING_PAYMENT',
    ADD COLUMN previous_shipment_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' AFTER status,
    ADD COLUMN strategy_name VARCHAR(50) NOT NULL AFTER strategy_key,
    ADD COLUMN route_name VARCHAR(100) NOT NULL AFTER route_id,
    ADD COLUMN route_mode VARCHAR(20) NOT NULL AFTER route_name,
    ADD COLUMN shipment_source VARCHAR(100) NOT NULL AFTER route_mode,
    ADD COLUMN shipment_destination VARCHAR(100) NOT NULL AFTER shipment_source,
    ADD COLUMN cargo_type VARCHAR(20) NOT NULL AFTER shipment_destination,
    ADD COLUMN weight_kg DECIMAL(12, 3) NOT NULL AFTER cargo_type,
    ADD COLUMN dispatch_date DATE NOT NULL AFTER weight_kg,
    ADD COLUMN transport_cost DECIMAL(14, 2) NOT NULL AFTER dispatch_date,
    ADD COLUMN handling_cost DECIMAL(14, 2) NOT NULL AFTER transport_cost,
    ADD COLUMN transfer_cost DECIMAL(14, 2) NOT NULL AFTER handling_cost,
    ADD COLUMN waiting_cost DECIMAL(14, 2) NOT NULL AFTER transfer_cost,
    ADD COLUMN duration_hours DECIMAL(10, 2) NULL AFTER total_cost,
    ADD COLUMN estimated_arrival DATETIME NULL AFTER duration_hours,
    ADD COLUMN emissions_kg DECIMAL(14, 2) NULL AFTER estimated_arrival,
    ADD COLUMN reliability DECIMAL(4, 3) NULL AFTER emissions_kg,
    ADD COLUMN balanced_score DECIMAL(10, 6) NULL AFTER reliability,
    ADD COLUMN recommendation_reason VARCHAR(1000) NOT NULL AFTER balanced_score,
    ADD COLUMN evaluation_notes VARCHAR(1000) NOT NULL DEFAULT '' AFTER recommendation_reason,
    ADD COLUMN confirmed_at TIMESTAMP NULL AFTER booked_at,
    ADD COLUMN cancelled_at TIMESTAMP NULL AFTER confirmed_at,
    ADD COLUMN active_shipment_id BIGINT GENERATED ALWAYS AS
        (CASE WHEN status IN ('PENDING_PAYMENT', 'CONFIRMED') THEN shipment_id ELSE NULL END) STORED,
    ADD UNIQUE KEY uk_bookings_active_shipment (active_shipment_id),
    ADD CONSTRAINT chk_bookings_status CHECK (status IN ('PENDING_PAYMENT', 'CONFIRMED', 'CANCELLED')),
    ADD CONSTRAINT chk_bookings_strategy CHECK (strategy_key IN ('CHEAPEST', 'FASTEST', 'GREENEST', 'BALANCED')),
    ADD CONSTRAINT chk_bookings_costs CHECK (transport_cost >= 0 AND handling_cost >= 0 AND transfer_cost >= 0
        AND waiting_cost >= 0 AND total_cost >= 0),
    ADD CONSTRAINT chk_bookings_timestamps CHECK ((status = 'CONFIRMED') = (confirmed_at IS NOT NULL)
        AND (status = 'CANCELLED') = (cancelled_at IS NOT NULL));

CREATE TABLE booking_legs (
    booking_id        BIGINT         NOT NULL,
    sequence_no       INT            NOT NULL,
    mode              VARCHAR(20)    NOT NULL,
    distance_km       DECIMAL(10, 2) NOT NULL,
    rate_per_tonne_km DECIMAL(10, 4) NOT NULL,
    speed_kmh         DECIMAL(8, 2)  NULL,
    buffer_hours      DECIMAL(8, 2)  NOT NULL,
    emission_factor   DECIMAL(8, 5)  NULL,
    PRIMARY KEY (booking_id, sequence_no),
    CONSTRAINT fk_booking_legs_booking FOREIGN KEY (booking_id) REFERENCES bookings (booking_id),
    CONSTRAINT chk_booking_legs_mode CHECK (mode IN ('ROAD', 'RAIL')),
    CONSTRAINT chk_booking_legs_values CHECK (distance_km > 0 AND rate_per_tonne_km >= 0 AND buffer_hours >= 0
        AND (speed_kmh IS NULL OR speed_kmh > 0) AND (emission_factor IS NULL OR emission_factor >= 0))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Payment attempts: PENDING -> PAID | FAILED; a FAILED attempt is retried as a new attempt_no.
-- At most one PENDING and at most one PAID attempt per booking (generated columns + UNIQUE).
ALTER TABLE payments
    ADD COLUMN attempt_no INT NOT NULL AFTER booking_id,
    ADD COLUMN created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) AFTER status,
    ADD COLUMN failed_at TIMESTAMP NULL AFTER paid_at,
    ADD COLUMN pending_booking_id BIGINT GENERATED ALWAYS AS
        (CASE WHEN status = 'PENDING' THEN booking_id ELSE NULL END) STORED,
    ADD COLUMN paid_booking_id BIGINT GENERATED ALWAYS AS
        (CASE WHEN status = 'PAID' THEN booking_id ELSE NULL END) STORED,
    ADD UNIQUE KEY uk_payments_attempt (booking_id, attempt_no),
    ADD UNIQUE KEY uk_payments_one_pending (pending_booking_id),
    ADD UNIQUE KEY uk_payments_one_paid (paid_booking_id),
    ADD CONSTRAINT chk_payments_status CHECK (status IN ('PENDING', 'PAID', 'FAILED')),
    ADD CONSTRAINT chk_payments_method CHECK (method IN ('UPI', 'CARD', 'NET_BANKING')),
    ADD CONSTRAINT chk_payments_attempt CHECK (attempt_no >= 1),
    ADD CONSTRAINT chk_payments_timestamps CHECK ((status = 'PAID') = (paid_at IS NOT NULL)
        AND (status = 'FAILED') = (failed_at IS NOT NULL));

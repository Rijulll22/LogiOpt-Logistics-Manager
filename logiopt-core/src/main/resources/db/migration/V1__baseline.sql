-- V1 baseline: the Review 2 schema (9 tables), identical to the superseded schema.sql minus
-- CREATE DATABASE / USE. On a database that already has these tables with this structure the
-- runner records V1 as BASELINE instead of executing it. MySQL DDL is not transactional.

CREATE TABLE users (
    user_id       BIGINT       NOT NULL AUTO_INCREMENT,
    name          VARCHAR(100) NOT NULL,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(20)  NOT NULL DEFAULT 'CUSTOMER',
    enabled       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id),
    UNIQUE KEY uk_users_email (email),
    CONSTRAINT chk_users_role CHECK (role IN ('CUSTOMER', 'ADMIN'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE shipments (
    shipment_id   BIGINT         NOT NULL AUTO_INCREMENT,
    user_id       BIGINT         NOT NULL,
    source        VARCHAR(100)   NOT NULL,
    destination   VARCHAR(100)   NOT NULL,
    cargo_type    VARCHAR(20)    NOT NULL,
    weight_kg     DECIMAL(12, 3) NOT NULL,
    dimensions    VARCHAR(100)   NULL,
    dispatch_date DATE           NOT NULL,
    status        VARCHAR(20)    NOT NULL DEFAULT 'DRAFT',
    created_at    TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (shipment_id),
    KEY idx_shipments_user (user_id),
    CONSTRAINT fk_shipments_user FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT chk_shipments_cargo CHECK (cargo_type IN ('GENERAL', 'FRAGILE', 'PERISHABLE', 'HAZARDOUS')),
    CONSTRAINT chk_shipments_weight CHECK (weight_kg > 0),
    CONSTRAINT chk_shipments_status CHECK (status IN ('DRAFT', 'PLANNED', 'BOOKED', 'CANCELLED', 'COMPLETED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE routes (
    route_id                     BIGINT         NOT NULL,
    name                         VARCHAR(100)   NOT NULL,
    source                       VARCHAR(100)   NOT NULL,
    destination                  VARCHAR(100)   NOT NULL,
    mode                         VARCHAR(20)    NOT NULL,
    reliability                  DECIMAL(4, 3)  NULL,
    capacity_kg                  DECIMAL(12, 2) NOT NULL,
    handling_charge              DECIMAL(12, 2) NOT NULL DEFAULT 0,
    transfer_charge_per_transfer DECIMAL(12, 2) NOT NULL DEFAULT 0,
    transfer_hours_per_transfer  DECIMAL(8, 2)  NOT NULL DEFAULT 0,
    waiting_hours                DECIMAL(8, 2)  NOT NULL DEFAULT 0,
    waiting_rate_per_hour        DECIMAL(12, 2) NOT NULL DEFAULT 0,
    unsupported_cargo            VARCHAR(100)   NOT NULL DEFAULT '',
    PRIMARY KEY (route_id),
    KEY idx_routes_pair (source, destination),
    CONSTRAINT chk_routes_mode CHECK (mode IN ('ROAD', 'RAIL', 'MULTIMODAL')),
    CONSTRAINT chk_routes_reliability CHECK (reliability IS NULL OR (reliability >= 0 AND reliability <= 1)),
    CONSTRAINT chk_routes_capacity CHECK (capacity_kg > 0),
    CONSTRAINT chk_routes_charges CHECK (handling_charge >= 0 AND transfer_charge_per_transfer >= 0
        AND transfer_hours_per_transfer >= 0 AND waiting_hours >= 0 AND waiting_rate_per_hour >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE route_legs (
    leg_id            BIGINT         NOT NULL AUTO_INCREMENT,
    route_id          BIGINT         NOT NULL,
    sequence_no       INT            NOT NULL,
    mode              VARCHAR(20)    NOT NULL,
    distance_km       DECIMAL(10, 2) NOT NULL,
    rate_per_tonne_km DECIMAL(10, 4) NOT NULL,
    speed_kmh         DECIMAL(8, 2)  NULL,
    buffer_hours      DECIMAL(8, 2)  NOT NULL DEFAULT 0,
    emission_factor   DECIMAL(8, 5)  NULL,
    PRIMARY KEY (leg_id),
    UNIQUE KEY uk_route_legs_seq (route_id, sequence_no),
    CONSTRAINT fk_route_legs_route FOREIGN KEY (route_id) REFERENCES routes (route_id) ON DELETE CASCADE,
    CONSTRAINT chk_route_legs_mode CHECK (mode IN ('ROAD', 'RAIL')),
    CONSTRAINT chk_route_legs_values CHECK (distance_km > 0 AND rate_per_tonne_km >= 0 AND buffer_hours >= 0
        AND (speed_kmh IS NULL OR speed_kmh > 0) AND (emission_factor IS NULL OR emission_factor >= 0))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE bookings (
    booking_id   BIGINT         NOT NULL AUTO_INCREMENT,
    shipment_id  BIGINT         NOT NULL,
    route_id     BIGINT         NOT NULL,
    strategy_key VARCHAR(20)    NOT NULL,
    total_cost   DECIMAL(14, 2) NOT NULL,
    status       VARCHAR(20)    NOT NULL DEFAULT 'BOOKED',
    booked_at    TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (booking_id),
    CONSTRAINT fk_bookings_shipment FOREIGN KEY (shipment_id) REFERENCES shipments (shipment_id),
    CONSTRAINT fk_bookings_route FOREIGN KEY (route_id) REFERENCES routes (route_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE tracking (
    tracking_id BIGINT       NOT NULL AUTO_INCREMENT,
    booking_id  BIGINT       NOT NULL,
    location    VARCHAR(100) NOT NULL,
    status      VARCHAR(30)  NOT NULL,
    note        VARCHAR(255) NULL,
    event_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tracking_id),
    CONSTRAINT fk_tracking_booking FOREIGN KEY (booking_id) REFERENCES bookings (booking_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE payments (
    payment_id BIGINT         NOT NULL AUTO_INCREMENT,
    booking_id BIGINT         NOT NULL,
    amount     DECIMAL(14, 2) NOT NULL,
    method     VARCHAR(30)    NOT NULL,
    status     VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    paid_at    TIMESTAMP      NULL,
    PRIMARY KEY (payment_id),
    CONSTRAINT fk_payments_booking FOREIGN KEY (booking_id) REFERENCES bookings (booking_id),
    CONSTRAINT chk_payments_amount CHECK (amount >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE notifications (
    notification_id BIGINT       NOT NULL AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL,
    message         VARCHAR(500) NOT NULL,
    is_read         BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (notification_id),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE reports (
    report_id    BIGINT       NOT NULL AUTO_INCREMENT,
    shipment_id  BIGINT       NOT NULL,
    strategy_key VARCHAR(20)  NOT NULL,
    file_name    VARCHAR(255) NOT NULL,
    status       VARCHAR(20)  NOT NULL,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (report_id),
    CONSTRAINT fk_reports_shipment FOREIGN KEY (shipment_id) REFERENCES shipments (shipment_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

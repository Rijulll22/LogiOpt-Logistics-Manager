-- V6: tracking milestones and in-app notifications (Review 3 Phase 4). The V1 tracking and
-- notifications tables are empty in Review 2 databases; rows that do not fit the CHECKs below make
-- V6 stop instead of being reinterpreted.
--
-- Tracking: one row per (booking, milestone), enforced by uk_tracking_booking_milestone, so a
-- repeated or concurrent "advance" can never record a milestone twice. Order of milestones and the
-- rule that only CONFIRMED bookings are tracked are enforced by the application under row locks.

ALTER TABLE tracking
    ADD COLUMN shipment_id BIGINT NOT NULL AFTER booking_id,
    MODIFY COLUMN location VARCHAR(100) NULL,
    ADD COLUMN description VARCHAR(500) NOT NULL AFTER status,
    ADD COLUMN source VARCHAR(20) NOT NULL AFTER description,
    ADD COLUMN recorded_by_user_id BIGINT NULL AFTER source,
    MODIFY COLUMN event_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    ADD UNIQUE KEY uk_tracking_booking_milestone (booking_id, status),
    ADD CONSTRAINT fk_tracking_shipment FOREIGN KEY (shipment_id) REFERENCES shipments (shipment_id),
    ADD CONSTRAINT fk_tracking_recorded_by FOREIGN KEY (recorded_by_user_id) REFERENCES users (user_id),
    ADD CONSTRAINT chk_tracking_status CHECK (status IN ('BOOKED', 'PICKED_UP', 'IN_TRANSIT', 'AT_TRANSFER',
        'OUT_FOR_DELIVERY', 'DELIVERED')),
    ADD CONSTRAINT chk_tracking_source CHECK (source IN ('SYSTEM', 'ADMIN', 'SIMULATED'));

-- Notifications: one row per (recipient, type, booking, event), so retries never duplicate.
ALTER TABLE notifications
    ADD COLUMN type VARCHAR(30) NOT NULL AFTER user_id,
    ADD COLUMN booking_id BIGINT NOT NULL AFTER type,
    ADD COLUMN event_key VARCHAR(60) NOT NULL AFTER booking_id,
    MODIFY COLUMN created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    ADD COLUMN read_at TIMESTAMP NULL AFTER is_read,
    ADD UNIQUE KEY uk_notifications_dedupe (user_id, type, booking_id, event_key),
    ADD KEY idx_notifications_unread (user_id, is_read),
    ADD CONSTRAINT fk_notifications_booking FOREIGN KEY (booking_id) REFERENCES bookings (booking_id),
    ADD CONSTRAINT chk_notifications_type CHECK (type IN ('BOOKING_CREATED', 'PAYMENT_SUCCEEDED', 'PAYMENT_FAILED',
        'BOOKING_CANCELLED', 'TRACKING_UPDATE')),
    ADD CONSTRAINT chk_notifications_read CHECK (is_read = (read_at IS NOT NULL));

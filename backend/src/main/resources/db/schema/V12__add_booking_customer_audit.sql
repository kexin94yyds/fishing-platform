ALTER TABLE booking
    ADD COLUMN contact_name VARCHAR(100);

ALTER TABLE booking
    ADD COLUMN contact_phone VARCHAR(32);

ALTER TABLE booking
    ADD COLUMN active_customer_key VARCHAR(128);

UPDATE booking
SET contact_name = COALESCE(
        (SELECT m.name FROM member m WHERE m.id = booking.member_id),
        '历史散客'),
    contact_phone = (SELECT m.phone FROM member m WHERE m.id = booking.member_id);

UPDATE booking
SET active_customer_key = CASE
    WHEN status = 'CONFIRMED' AND member_id IS NOT NULL THEN CONCAT('M:', member_id)
    WHEN status = 'CONFIRMED' AND contact_phone IS NOT NULL
        THEN CONCAT('P:', REPLACE(REPLACE(contact_phone, ' ', ''), '-', ''))
    ELSE NULL
END;

CREATE UNIQUE INDEX uk_booking_active_customer_slot
    ON booking (fishing_date, time_slot, active_customer_key);

CREATE TABLE booking_audit_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    actor_user_id BIGINT,
    actor_username VARCHAR(64) NOT NULL,
    booking_id BIGINT NOT NULL,
    booking_no VARCHAR(40) NOT NULL,
    action VARCHAR(32) NOT NULL,
    before_status VARCHAR(24),
    after_status VARCHAR(24),
    before_payment_status VARCHAR(24),
    after_payment_status VARCHAR(24),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_booking_audit_actor FOREIGN KEY (actor_user_id) REFERENCES app_user (id),
    CONSTRAINT fk_booking_audit_booking FOREIGN KEY (booking_id) REFERENCES booking (id)
);

CREATE INDEX idx_booking_audit_booking_created
    ON booking_audit_log (booking_id, created_at);

ALTER TABLE fishing_spot
    ADD COLUMN default_price DECIMAL(12, 2) NOT NULL DEFAULT 0;

ALTER TABLE fishing_spot
    ADD CONSTRAINT ck_spot_default_price CHECK (default_price >= 0);

ALTER TABLE fishing_slot_inventory
    ADD COLUMN price DECIMAL(12, 2) NOT NULL DEFAULT 0;

ALTER TABLE fishing_slot_inventory
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE fishing_slot_inventory
    ADD CONSTRAINT ck_inventory_price CHECK (price >= 0);

ALTER TABLE fishing_slot_inventory
    ADD CONSTRAINT ck_inventory_version CHECK (version >= 0);

ALTER TABLE booking
    ADD COLUMN payment_status VARCHAR(24) NOT NULL DEFAULT 'PENDING';

ALTER TABLE booking
    ADD CONSTRAINT ck_booking_payment_status
        CHECK (payment_status IN ('PENDING', 'PAID', 'CANCELLED'));

ALTER TABLE catch_record
    ADD COLUMN time_slot VARCHAR(32) NOT NULL DEFAULT 'MORNING';

ALTER TABLE catch_record
    ADD CONSTRAINT ck_catch_time_slot
        CHECK (time_slot IN ('MORNING', 'AFTERNOON', 'EVENING'));

UPDATE fishing_slot_inventory i
SET price = (
    SELECT s.default_price
    FROM fishing_spot s
    WHERE s.id = i.spot_id
);

UPDATE booking
SET payment_status = CASE
    WHEN status = 'CANCELLED' THEN 'CANCELLED'
    WHEN amount = 0 THEN 'PAID'
    ELSE 'PENDING'
END;

INSERT INTO payment
    (payment_no, business_type, business_id, amount, method, status)
SELECT CONCAT('PAY-BK-', b.booking_no), 'BOOKING', b.id, b.amount, NULL,
       CASE WHEN b.status = 'CANCELLED' THEN 'CANCELLED' ELSE 'PENDING' END
FROM booking b
WHERE b.amount > 0
  AND NOT EXISTS (
      SELECT 1
      FROM payment p
      WHERE p.business_type = 'BOOKING' AND p.business_id = b.id
  );

CREATE TABLE member_audit_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    actor_user_id BIGINT,
    actor_username VARCHAR(64) NOT NULL,
    target_member_id BIGINT NOT NULL,
    target_member_no VARCHAR(40) NOT NULL,
    action VARCHAR(40) NOT NULL,
    before_level VARCHAR(24),
    after_level VARCHAR(24),
    before_points INT,
    after_points INT,
    before_status VARCHAR(24),
    after_status VARCHAR(24),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_member_audit_actor FOREIGN KEY (actor_user_id) REFERENCES app_user (id),
    CONSTRAINT fk_member_audit_target FOREIGN KEY (target_member_id) REFERENCES member (id)
);

CREATE INDEX idx_member_audit_target_created
    ON member_audit_log (target_member_id, created_at);

ALTER TABLE app_user
    DROP CONSTRAINT ck_app_user_role;

ALTER TABLE app_user
    ADD CONSTRAINT ck_app_user_role
    CHECK (role IN ('ADMIN', 'OPERATOR', 'USER'));

ALTER TABLE booking
    ADD COLUMN active_contact_key VARCHAR(64);

CREATE TEMPORARY TABLE booking_contact_key_keeper (
    booking_id BIGINT PRIMARY KEY
);

INSERT INTO booking_contact_key_keeper (booking_id)
SELECT MIN(id)
FROM booking
WHERE status = 'CONFIRMED'
  AND contact_phone IS NOT NULL
GROUP BY fishing_date,
         time_slot,
         REPLACE(REPLACE(contact_phone, ' ', ''), '-', '');

UPDATE booking
SET active_contact_key = CASE
    WHEN id IN (SELECT booking_id FROM booking_contact_key_keeper)
        THEN CONCAT('P:', REPLACE(REPLACE(contact_phone, ' ', ''), '-', ''))
    ELSE NULL
END;

DROP TABLE booking_contact_key_keeper;

CREATE UNIQUE INDEX uk_booking_active_contact_slot
    ON booking (fishing_date, time_slot, active_contact_key);

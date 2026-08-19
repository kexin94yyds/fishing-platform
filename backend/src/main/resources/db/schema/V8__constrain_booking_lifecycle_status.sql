ALTER TABLE booking
    ADD CONSTRAINT ck_booking_lifecycle_status
    CHECK (status IN ('CONFIRMED', 'CANCELLED', 'COMPLETED', 'NO_SHOW'));

ALTER TABLE traffic_daily
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE traffic_daily
    ADD COLUMN notes VARCHAR(500);

ALTER TABLE traffic_daily
    ADD COLUMN created_by BIGINT;

ALTER TABLE traffic_daily
    ADD COLUMN updated_by BIGINT;

ALTER TABLE traffic_daily
    ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE traffic_daily
    ADD CONSTRAINT ck_traffic_daily_visits_nonnegative CHECK (visits >= 0);

ALTER TABLE traffic_daily
    ADD CONSTRAINT ck_traffic_daily_unique_visitors_range
        CHECK (unique_visitors >= 0 AND unique_visitors <= visits);

ALTER TABLE traffic_daily
    ADD CONSTRAINT fk_traffic_daily_created_by
        FOREIGN KEY (created_by) REFERENCES app_user(id);

ALTER TABLE traffic_daily
    ADD CONSTRAINT fk_traffic_daily_updated_by
        FOREIGN KEY (updated_by) REFERENCES app_user(id);

ALTER TABLE app_user
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE app_user
    ADD COLUMN session_version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE account_admin_guard (
    id BIGINT PRIMARY KEY,
    CONSTRAINT ck_account_admin_guard_singleton CHECK (id = 1)
);

INSERT INTO account_admin_guard (id) VALUES (1);

CREATE TABLE account_audit_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    actor_user_id BIGINT,
    actor_username VARCHAR(64) NOT NULL,
    target_user_id BIGINT NOT NULL,
    target_username VARCHAR(64) NOT NULL,
    action VARCHAR(40) NOT NULL,
    before_role VARCHAR(32),
    after_role VARCHAR(32),
    before_enabled BOOLEAN,
    after_enabled BOOLEAN,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_account_audit_actor FOREIGN KEY (actor_user_id) REFERENCES app_user (id),
    CONSTRAINT fk_account_audit_target FOREIGN KEY (target_user_id) REFERENCES app_user (id)
);

CREATE INDEX idx_account_audit_target_created
    ON account_audit_log (target_user_id, created_at);

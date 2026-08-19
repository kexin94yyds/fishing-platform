ALTER TABLE app_user
    ALTER COLUMN role SET DEFAULT 'OPERATOR';

ALTER TABLE app_user
    ADD CONSTRAINT ck_app_user_role
    CHECK (role IN ('ADMIN', 'OPERATOR'));

ALTER TABLE app_user
    ADD CONSTRAINT ck_app_user_username_length
    CHECK (CHAR_LENGTH(username) BETWEEN 4 AND 32);

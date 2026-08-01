CREATE TABLE app_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    role VARCHAR(32) NOT NULL DEFAULT 'ADMIN',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_app_user_username UNIQUE (username)
);

CREATE TABLE fishing_zone (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_zone_code UNIQUE (code)
);

CREATE TABLE fishing_spot (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    zone_id BIGINT NOT NULL,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(100) NOT NULL,
    map_x DECIMAL(5, 2),
    map_y DECIMAL(5, 2),
    capacity INT NOT NULL DEFAULT 1,
    status VARCHAR(24) NOT NULL DEFAULT 'OPEN',
    note VARCHAR(500),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_spot_code UNIQUE (code),
    CONSTRAINT fk_spot_zone FOREIGN KEY (zone_id) REFERENCES fishing_zone (id),
    CONSTRAINT ck_spot_capacity CHECK (capacity > 0),
    CONSTRAINT ck_spot_map_x CHECK (map_x IS NULL OR (map_x >= 0 AND map_x <= 100)),
    CONSTRAINT ck_spot_map_y CHECK (map_y IS NULL OR (map_y >= 0 AND map_y <= 100))
);
CREATE INDEX idx_spot_zone ON fishing_spot (zone_id);

CREATE TABLE member (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    member_no VARCHAR(40) NOT NULL,
    name VARCHAR(100) NOT NULL,
    phone VARCHAR(32) NOT NULL,
    level VARCHAR(24) NOT NULL DEFAULT 'NORMAL',
    points INT NOT NULL DEFAULT 0,
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_member_no UNIQUE (member_no),
    CONSTRAINT uk_member_phone UNIQUE (phone),
    CONSTRAINT ck_member_points CHECK (points >= 0)
);

CREATE TABLE fishing_slot_inventory (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    spot_id BIGINT NOT NULL,
    fishing_date DATE NOT NULL,
    time_slot VARCHAR(32) NOT NULL,
    capacity INT NOT NULL,
    reserved_count INT NOT NULL DEFAULT 0,
    status VARCHAR(24) NOT NULL DEFAULT 'AVAILABLE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_slot_inventory UNIQUE (spot_id, fishing_date, time_slot),
    CONSTRAINT fk_inventory_spot FOREIGN KEY (spot_id) REFERENCES fishing_spot (id),
    CONSTRAINT ck_inventory_capacity CHECK (capacity > 0),
    CONSTRAINT ck_inventory_reserved CHECK (reserved_count >= 0 AND reserved_count <= capacity)
);
CREATE INDEX idx_inventory_date ON fishing_slot_inventory (fishing_date, time_slot);

CREATE TABLE booking (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    booking_no VARCHAR(40) NOT NULL,
    member_id BIGINT,
    spot_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    fishing_date DATE NOT NULL,
    time_slot VARCHAR(32) NOT NULL,
    guests INT NOT NULL,
    amount DECIMAL(12, 2) NOT NULL DEFAULT 0,
    status VARCHAR(24) NOT NULL DEFAULT 'CONFIRMED',
    notes VARCHAR(500),
    cancelled_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_booking_no UNIQUE (booking_no),
    CONSTRAINT fk_booking_member FOREIGN KEY (member_id) REFERENCES member (id),
    CONSTRAINT fk_booking_spot FOREIGN KEY (spot_id) REFERENCES fishing_spot (id),
    CONSTRAINT fk_booking_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT ck_booking_guests CHECK (guests > 0),
    CONSTRAINT ck_booking_amount CHECK (amount >= 0)
);
CREATE INDEX idx_booking_slot ON booking (spot_id, fishing_date, time_slot, status);
CREATE INDEX idx_booking_member ON booking (member_id);

CREATE TABLE catch_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    catch_no VARCHAR(40) NOT NULL,
    booking_id BIGINT,
    spot_id BIGINT NOT NULL,
    member_id BIGINT,
    fishing_date DATE NOT NULL,
    species VARCHAR(100) NOT NULL,
    weight DECIMAL(10, 2) NOT NULL,
    quantity INT NOT NULL,
    notes VARCHAR(500),
    status VARCHAR(24) NOT NULL DEFAULT 'RECORDED',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_catch_no UNIQUE (catch_no),
    CONSTRAINT fk_catch_booking FOREIGN KEY (booking_id) REFERENCES booking (id),
    CONSTRAINT fk_catch_spot FOREIGN KEY (spot_id) REFERENCES fishing_spot (id),
    CONSTRAINT fk_catch_member FOREIGN KEY (member_id) REFERENCES member (id),
    CONSTRAINT ck_catch_weight CHECK (weight >= 0),
    CONSTRAINT ck_catch_quantity CHECK (quantity > 0)
);
CREATE INDEX idx_catch_date ON catch_record (fishing_date);

CREATE TABLE product (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    sku VARCHAR(40) NOT NULL,
    name VARCHAR(120) NOT NULL,
    category VARCHAR(60) NOT NULL,
    price DECIMAL(12, 2) NOT NULL,
    stock_quantity INT NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_product_sku UNIQUE (sku),
    CONSTRAINT ck_product_price CHECK (price >= 0),
    CONSTRAINT ck_product_stock CHECK (stock_quantity >= 0)
);

CREATE TABLE sales_order (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_no VARCHAR(40) NOT NULL,
    member_id BIGINT,
    total_amount DECIMAL(12, 2) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING_PAYMENT',
    payment_status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sales_order_no UNIQUE (order_no),
    CONSTRAINT fk_sales_member FOREIGN KEY (member_id) REFERENCES member (id),
    CONSTRAINT fk_sales_user FOREIGN KEY (created_by) REFERENCES app_user (id),
    CONSTRAINT ck_sales_total CHECK (total_amount >= 0)
);
CREATE INDEX idx_sales_created ON sales_order (created_at);

CREATE TABLE sales_order_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    product_name VARCHAR(120) NOT NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(12, 2) NOT NULL,
    line_amount DECIMAL(12, 2) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_item_order FOREIGN KEY (order_id) REFERENCES sales_order (id),
    CONSTRAINT fk_item_product FOREIGN KEY (product_id) REFERENCES product (id),
    CONSTRAINT ck_item_quantity CHECK (quantity > 0),
    CONSTRAINT ck_item_amount CHECK (unit_price >= 0 AND line_amount >= 0)
);
CREATE INDEX idx_item_order ON sales_order_item (order_id);

CREATE TABLE payment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    payment_no VARCHAR(40) NOT NULL,
    business_type VARCHAR(32) NOT NULL,
    business_id BIGINT NOT NULL,
    amount DECIMAL(12, 2) NOT NULL,
    method VARCHAR(32),
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    confirmed_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_payment_no UNIQUE (payment_no),
    CONSTRAINT ck_payment_amount CHECK (amount >= 0)
);
CREATE INDEX idx_payment_business ON payment (business_type, business_id);

CREATE TABLE traffic_daily (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    stat_date DATE NOT NULL,
    visits INT NOT NULL DEFAULT 0,
    unique_visitors INT NOT NULL DEFAULT 0,
    new_members INT NOT NULL DEFAULT 0,
    booking_count INT NOT NULL DEFAULT 0,
    revenue DECIMAL(12, 2) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_traffic_date UNIQUE (stat_date)
);

CREATE TABLE visitor_flow_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    recorded_at DATETIME NOT NULL,
    entrance VARCHAR(60) NOT NULL,
    visitor_count INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_visitor_count CHECK (visitor_count >= 0)
);
CREATE INDEX idx_visitor_recorded_at ON visitor_flow_record (recorded_at);

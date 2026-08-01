INSERT INTO app_user (username, password_hash, display_name, role, enabled)
VALUES ('admin', '$2y$10$g10ppELimqMPmCQzalBPa.x0GbwCZdDWR0M6QVPr/0ybxWFxX5q1G', '运营管理员', 'ADMIN', TRUE);

INSERT INTO fishing_zone (code, name, description, status) VALUES
('EAST', '东岸生态区', '水草丰茂，适合休闲垂钓', 'ACTIVE'),
('NORTH', '北岸竞技区', '标准化竞技钓位', 'ACTIVE'),
('FAMILY', '亲子体验区', '靠近服务中心，设有安全护栏', 'ACTIVE');

INSERT INTO fishing_spot (zone_id, code, name, map_x, map_y, capacity, status, note) VALUES
(1, 'E-01', '东岸 1 号位', 22.00, 58.00, 2, 'OPEN', '树荫钓位'),
(1, 'E-02', '东岸 2 号位', 31.00, 66.00, 2, 'OPEN', '深水区'),
(2, 'N-01', '竞技 1 号位', 67.00, 28.00, 1, 'OPEN', '标准竞技位'),
(3, 'F-01', '亲子 1 号位', 48.00, 82.00, 4, 'OPEN', '配备安全护栏');

INSERT INTO member (member_no, name, phone, level, points, status) VALUES
('M20260001', '张海', '13800000001', 'GOLD', 860, 'ACTIVE'),
('M20260002', '李青', '13800000002', 'NORMAL', 120, 'ACTIVE'),
('M20260003', '王远', '13800000003', 'SILVER', 420, 'ACTIVE');

INSERT INTO fishing_slot_inventory
    (spot_id, fishing_date, time_slot, capacity, reserved_count, status)
VALUES
    (1, CURRENT_DATE, 'MORNING', 2, 1, 'AVAILABLE'),
    (4, CURRENT_DATE, 'AFTERNOON', 4, 2, 'AVAILABLE');

INSERT INTO booking
    (booking_no, member_id, spot_id, user_id, fishing_date, time_slot, guests, amount, status, notes)
VALUES
    ('BK-SEED-001', 1, 1, 1, CURRENT_DATE, 'MORNING', 1, 80.00, 'CONFIRMED', '演示预约'),
    ('BK-SEED-002', 2, 4, 1, CURRENT_DATE, 'AFTERNOON', 2, 120.00, 'CONFIRMED', '亲子体验预约');

INSERT INTO catch_record
    (catch_no, booking_id, spot_id, member_id, fishing_date, species, weight, quantity, notes, status)
VALUES
('CATCH-SEED-001', 1, 1, 1, CURRENT_DATE, '鲫鱼', 2.35, 5, '生态放流记录', 'RECORDED'),
('CATCH-SEED-002', NULL, 3, 2, CURRENT_DATE, '鲤鱼', 3.80, 1, '竞技区样本', 'RECORDED');

INSERT INTO product (sku, name, category, price, stock_quantity, status) VALUES
('BAIT-001', '通用鲫鱼饵', '鱼饵', 28.00, 78, 'ACTIVE'),
('LINE-001', '尼龙主线 2.0', '钓具', 36.00, 25, 'ACTIVE'),
('DRINK-001', '瓶装饮用水', '饮品', 3.00, 120, 'ACTIVE'),
('ROD-001', '4.5 米综合竿', '钓具', 268.00, 8, 'ACTIVE');

INSERT INTO sales_order
    (order_no, member_id, total_amount, status, payment_status, created_by)
VALUES
    ('SO-SEED-001', 2, 56.00, 'PENDING_PAYMENT', 'PENDING', 1);

INSERT INTO sales_order_item
    (order_id, product_id, product_name, quantity, unit_price, line_amount)
VALUES
    (1, 1, '通用鲫鱼饵', 2, 28.00, 56.00);

INSERT INTO payment
    (payment_no, business_type, business_id, amount, method, status)
VALUES
    ('PAY-SEED-001', 'SALES_ORDER', 1, 56.00, 'WECHAT', 'PENDING');

INSERT INTO traffic_daily (stat_date, visits, unique_visitors, new_members, booking_count, revenue)
VALUES
(CURRENT_DATE - INTERVAL '6' DAY, 108, 82, 2, 9, 980.00),
(CURRENT_DATE - INTERVAL '5' DAY, 126, 96, 3, 11, 1260.00),
(CURRENT_DATE - INTERVAL '4' DAY, 119, 91, 1, 10, 1120.00),
(CURRENT_DATE - INTERVAL '3' DAY, 158, 121, 4, 15, 1880.00),
(CURRENT_DATE - INTERVAL '2' DAY, 172, 133, 5, 17, 2150.00),
(CURRENT_DATE - INTERVAL '1' DAY, 164, 128, 3, 14, 1960.00),
(CURRENT_DATE, 186, 142, 6, 18, 2360.00);

INSERT INTO visitor_flow_record (recorded_at, entrance, visitor_count) VALUES
(CURRENT_TIMESTAMP, '主入口', 96),
(CURRENT_TIMESTAMP, '北岸入口', 46);


-- Migration: V1__init_webpos_schema.sql
CREATE TABLE users (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(50) NOT NULL,
  role ENUM('admin','manager','super_waiter','waiter') NOT NULL,
  pin_hash VARCHAR(255) NOT NULL,
  active TINYINT(1) NOT NULL DEFAULT 1,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_users_username (username),
  KEY idx_users_role_active (role, active),
  KEY idx_users_active (active)
);

CREATE TABLE products (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(120) NOT NULL,
  tab ENUM('kitchen','bar') NOT NULL,
  subcategory VARCHAR(40) NOT NULL,
  price_ksh DECIMAL(12,2) NOT NULL,
  image_url VARCHAR(500) NOT NULL,
  active TINYINT(1) NOT NULL DEFAULT 1,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_products_tab_subcategory_active (tab, subcategory, active),
  KEY idx_products_active_name (active, name)
);

CREATE TABLE orders (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_no VARCHAR(20) NOT NULL,
  waiter_id BIGINT NOT NULL,
  status ENUM('open','pending','closed') NOT NULL DEFAULT 'open',
  total_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_orders_order_no (order_no),
  KEY idx_orders_waiter_status (waiter_id, status),
  KEY idx_orders_status_created (status, created_at),
  CONSTRAINT fk_orders_waiter FOREIGN KEY (waiter_id) REFERENCES users(id)
);

CREATE TABLE order_items (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  product_id BIGINT NULL,
  item_name VARCHAR(120) NOT NULL,
  qty INT NOT NULL,
  unit_price DECIMAL(12,2) NOT NULL,
  line_total DECIMAL(12,2) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_order_items_order (order_id),
  KEY idx_order_items_product (product_id),
  CONSTRAINT fk_items_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
  CONSTRAINT fk_items_product FOREIGN KEY (product_id) REFERENCES products(id)
);

CREATE TABLE payments (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  mode ENUM('cash','mpesa','card') NOT NULL,
  status ENUM('pending','confirmed','rejected','auto_confirmed') NOT NULL,
  amount DECIMAL(12,2) NOT NULL,
  mpesa_last4 VARCHAR(4),
  paynet_time VARCHAR(5),
  confirmed_by_user_id BIGINT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_payments_order (order_id),
  KEY idx_payments_status_created (status, created_at),
  KEY idx_payments_mode_status (mode, status),
  KEY idx_payments_confirmer (confirmed_by_user_id),
  CONSTRAINT fk_pay_order FOREIGN KEY (order_id) REFERENCES orders(id),
  CONSTRAINT fk_pay_confirmer FOREIGN KEY (confirmed_by_user_id) REFERENCES users(id)
);

CREATE TABLE manager_messages (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(120) NOT NULL,
  body TEXT NOT NULL,
  created_by_user_id BIGINT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_messages_created_at (created_at),
  KEY idx_messages_creator (created_by_user_id),
  CONSTRAINT fk_msg_creator FOREIGN KEY (created_by_user_id) REFERENCES users(id)
);

CREATE TABLE auth_sessions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  token VARCHAR(255) NOT NULL,
  user_id BIGINT NOT NULL,
  expires_at DATETIME NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_auth_sessions_token (token),
  KEY idx_session_user_expiry (user_id, expires_at),
  KEY idx_session_expiry (expires_at),
  CONSTRAINT fk_session_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE payment_confirmation_audit (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  payment_id BIGINT NOT NULL,
  order_id BIGINT NOT NULL,
  previous_status VARCHAR(30),
  new_status VARCHAR(30) NOT NULL,
  action VARCHAR(30) NOT NULL,
  acted_by_user_id BIGINT NOT NULL,
  acted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  notes VARCHAR(255),
  KEY idx_payment_audit_payment (payment_id, acted_at),
  KEY idx_payment_audit_actor (acted_by_user_id, acted_at),
  CONSTRAINT fk_payment_audit_payment FOREIGN KEY (payment_id) REFERENCES payments(id),
  CONSTRAINT fk_payment_audit_order FOREIGN KEY (order_id) REFERENCES orders(id),
  CONSTRAINT fk_payment_audit_actor FOREIGN KEY (acted_by_user_id) REFERENCES users(id)
);

CREATE TABLE user_action_audit (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  actor_user_id BIGINT,
  target_user_id BIGINT,
  action VARCHAR(60) NOT NULL,
  entity_type VARCHAR(60) NOT NULL,
  entity_id BIGINT,
  details TEXT,
  acted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_user_action_actor (actor_user_id, acted_at),
  KEY idx_user_action_target (target_user_id, acted_at),
  KEY idx_user_action_entity (entity_type, entity_id),
  CONSTRAINT fk_user_action_actor FOREIGN KEY (actor_user_id) REFERENCES users(id),
  CONSTRAINT fk_user_action_target FOREIGN KEY (target_user_id) REFERENCES users(id)
);

INSERT INTO users (username, role, pin_hash, active)
SELECT 'admin', 'admin', SHA2('11112222', 256), 1
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'admin');

INSERT INTO users (username, role, pin_hash, active)
SELECT 'manager', 'manager', SHA2('22223333', 256), 1
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'manager');

INSERT INTO users (username, role, pin_hash, active)
SELECT 'super_waiter', 'super_waiter', SHA2('33334444', 256), 1
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'super_waiter');

INSERT INTO users (username, role, pin_hash, active)
SELECT 'waiter', 'waiter', SHA2('44445555', 256), 1
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'waiter');

INSERT INTO products (name, tab, subcategory, price_ksh, image_url, active)
SELECT 'Ugali + Beef Stew', 'kitchen', 'mains', 450, 'https://images.unsplash.com/photo-1604908176997-125f25cc6f3d?auto=format&fit=crop&w=600&q=80', 1
WHERE NOT EXISTS (SELECT 1 FROM products WHERE name='Ugali + Beef Stew');
INSERT INTO products (name, tab, subcategory, price_ksh, image_url, active)
SELECT 'Pilau Beef', 'kitchen', 'mains', 500, 'https://images.unsplash.com/photo-1512058564366-18510be2db19?auto=format&fit=crop&w=600&q=80', 1
WHERE NOT EXISTS (SELECT 1 FROM products WHERE name='Pilau Beef');
INSERT INTO products (name, tab, subcategory, price_ksh, image_url, active)
SELECT 'Goat Choma', 'kitchen', 'grills', 900, 'https://images.unsplash.com/photo-1529692236671-f1f6cf9683ba?auto=format&fit=crop&w=600&q=80', 1
WHERE NOT EXISTS (SELECT 1 FROM products WHERE name='Goat Choma');
INSERT INTO products (name, tab, subcategory, price_ksh, image_url, active)
SELECT 'French Fries', 'kitchen', 'sides', 250, 'https://images.unsplash.com/photo-1630384060421-cb20d0e0649d?auto=format&fit=crop&w=600&q=80', 1
WHERE NOT EXISTS (SELECT 1 FROM products WHERE name='French Fries');
INSERT INTO products (name, tab, subcategory, price_ksh, image_url, active)
SELECT 'Tusker Lager 500ml', 'bar', 'beers', 300, 'https://images.unsplash.com/photo-1608270586620-248524c67de9?auto=format&fit=crop&w=600&q=80', 1
WHERE NOT EXISTS (SELECT 1 FROM products WHERE name='Tusker Lager 500ml');
INSERT INTO products (name, tab, subcategory, price_ksh, image_url, active)
SELECT 'White Cap 500ml', 'bar', 'beers', 320, 'https://images.unsplash.com/photo-1436076863939-06870fe779c2?auto=format&fit=crop&w=600&q=80', 1
WHERE NOT EXISTS (SELECT 1 FROM products WHERE name='White Cap 500ml');
INSERT INTO products (name, tab, subcategory, price_ksh, image_url, active)
SELECT 'Jameson Shot', 'bar', 'whisky', 350, 'https://images.unsplash.com/photo-1569529465841-dfecdab7503b?auto=format&fit=crop&w=600&q=80', 1
WHERE NOT EXISTS (SELECT 1 FROM products WHERE name='Jameson Shot');
INSERT INTO products (name, tab, subcategory, price_ksh, image_url, active)
SELECT 'Soda 300ml', 'bar', 'beverages', 100, 'https://images.unsplash.com/photo-1596803244618-8dbee441d70b?auto=format&fit=crop&w=600&q=80', 1
WHERE NOT EXISTS (SELECT 1 FROM products WHERE name='Soda 300ml');

-- Migration: V2__optimize_indexes_and_audit.sql
ALTER TABLE users
  ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

ALTER TABLE products
  ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

ALTER TABLE orders
  ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

ALTER TABLE order_items
  ADD COLUMN IF NOT EXISTS product_id BIGINT NULL,
  ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

ALTER TABLE payments
  ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

ALTER TABLE manager_messages
  ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

ALTER TABLE auth_sessions
  ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_users_role_active ON users (role, active);
CREATE INDEX IF NOT EXISTS idx_users_active ON users (active);
CREATE INDEX IF NOT EXISTS idx_products_tab_subcategory_active ON products (tab, subcategory, active);
CREATE INDEX IF NOT EXISTS idx_products_active_name ON products (active, name);
CREATE INDEX IF NOT EXISTS idx_orders_waiter_status ON orders (waiter_id, status);
CREATE INDEX IF NOT EXISTS idx_orders_status_created ON orders (status, created_at);
CREATE INDEX IF NOT EXISTS idx_order_items_order ON order_items (order_id);
CREATE INDEX IF NOT EXISTS idx_order_items_product ON order_items (product_id);
CREATE INDEX IF NOT EXISTS idx_payments_order ON payments (order_id);
CREATE INDEX IF NOT EXISTS idx_payments_status_created ON payments (status, created_at);
CREATE INDEX IF NOT EXISTS idx_payments_mode_status ON payments (mode, status);
CREATE INDEX IF NOT EXISTS idx_payments_confirmer ON payments (confirmed_by_user_id);
CREATE INDEX IF NOT EXISTS idx_messages_created_at ON manager_messages (created_at);
CREATE INDEX IF NOT EXISTS idx_messages_creator ON manager_messages (created_by_user_id);
CREATE INDEX IF NOT EXISTS idx_session_user_expiry ON auth_sessions (user_id, expires_at);

CREATE TABLE IF NOT EXISTS payment_confirmation_audit (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  payment_id BIGINT NOT NULL,
  order_id BIGINT NOT NULL,
  previous_status VARCHAR(30),
  new_status VARCHAR(30) NOT NULL,
  action VARCHAR(30) NOT NULL,
  acted_by_user_id BIGINT NOT NULL,
  acted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  notes VARCHAR(255),
  KEY idx_payment_audit_payment (payment_id, acted_at),
  KEY idx_payment_audit_actor (acted_by_user_id, acted_at),
  CONSTRAINT fk_payment_audit_payment FOREIGN KEY (payment_id) REFERENCES payments(id),
  CONSTRAINT fk_payment_audit_order FOREIGN KEY (order_id) REFERENCES orders(id),
  CONSTRAINT fk_payment_audit_actor FOREIGN KEY (acted_by_user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS user_action_audit (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  actor_user_id BIGINT,
  target_user_id BIGINT,
  action VARCHAR(60) NOT NULL,
  entity_type VARCHAR(60) NOT NULL,
  entity_id BIGINT,
  details TEXT,
  acted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_user_action_actor (actor_user_id, acted_at),
  KEY idx_user_action_target (target_user_id, acted_at),
  KEY idx_user_action_entity (entity_type, entity_id),
  CONSTRAINT fk_user_action_actor FOREIGN KEY (actor_user_id) REFERENCES users(id),
  CONSTRAINT fk_user_action_target FOREIGN KEY (target_user_id) REFERENCES users(id)
);

-- Migration: V3__add_branches_and_stations.sql
CREATE TABLE IF NOT EXISTS branches (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(120) NOT NULL,
  active TINYINT(1) NOT NULL DEFAULT 1,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_branches_name (name),
  KEY idx_branches_active_name (active, name)
);

CREATE TABLE IF NOT EXISTS stations (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(120) NOT NULL,
  type ENUM('COUNTER','TABLE','DELIVERY','KITCHEN') NOT NULL,
  ip_address VARCHAR(45),
  status ENUM('ACTIVE','INACTIVE','OFFLINE') NOT NULL DEFAULT 'ACTIVE',
  registered_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_heartbeat TIMESTAMP,
  branch_id BIGINT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_stations_branch_type_status (branch_id, type, status),
  KEY idx_stations_last_heartbeat (last_heartbeat),
  CONSTRAINT fk_stations_branch FOREIGN KEY (branch_id) REFERENCES branches(id)
);

INSERT INTO branches (name, active)
SELECT 'Main Branch', 1
WHERE NOT EXISTS (SELECT 1 FROM branches WHERE name = 'Main Branch');

-- Migration: V4__add_order_station_sync.sql
ALTER TABLE orders
  ADD COLUMN IF NOT EXISTS station_id BIGINT NULL,
  ADD COLUMN IF NOT EXISTS ticket_number VARCHAR(50) NULL,
  ADD KEY IF NOT EXISTS idx_orders_station_status (station_id, status),
  ADD KEY IF NOT EXISTS idx_orders_ticket_number (ticket_number),
  ADD CONSTRAINT fk_orders_station FOREIGN KEY (station_id) REFERENCES stations(id);

UPDATE orders
SET ticket_number = order_no
WHERE ticket_number IS NULL;

CREATE TABLE IF NOT EXISTS order_events (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  event_type VARCHAR(50) NOT NULL,
  station_id BIGINT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_order_events_order_created (order_id, created_at),
  KEY idx_order_events_station_created (station_id, created_at),
  CONSTRAINT fk_order_events_order FOREIGN KEY (order_id) REFERENCES orders(id),
  CONSTRAINT fk_order_events_station FOREIGN KEY (station_id) REFERENCES stations(id)
);

-- Migration: V5__add_order_sequences.sql
CREATE TABLE IF NOT EXISTS order_sequences (
  sequence_date DATE PRIMARY KEY,
  next_value INT NOT NULL
);

INSERT INTO order_sequences (sequence_date, next_value)
SELECT CURRENT_DATE, COALESCE(MAX(CAST(SUBSTRING(order_no, 7) AS UNSIGNED)), 0)
FROM orders
WHERE order_no LIKE CONCAT(DATE_FORMAT(CURRENT_DATE, '%d%m%y'), '%')
ON DUPLICATE KEY UPDATE next_value = GREATEST(next_value, VALUES(next_value));

-- Migration: V6__add_receipts_and_bills.sql
CREATE TABLE IF NOT EXISTS receipt_sequences (
  sequence_date DATE NOT NULL,
  prefix VARCHAR(10) NOT NULL,
  next_value INT NOT NULL,
  PRIMARY KEY (sequence_date, prefix)
);

CREATE TABLE IF NOT EXISTS station_receipts (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  receipt_no VARCHAR(40) NOT NULL UNIQUE,
  order_id BIGINT NOT NULL,
  station_id BIGINT NOT NULL,
  station_type ENUM('KITCHEN','BAR') NOT NULL,
  status ENUM('printed','sent') NOT NULL DEFAULT 'printed',
  payload_json TEXT,
  printed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_station_receipts_order FOREIGN KEY (order_id) REFERENCES orders(id),
  INDEX idx_station_receipts_order (order_id),
  INDEX idx_station_receipts_station (station_id, station_type, printed_at)
);

CREATE TABLE IF NOT EXISTS receipts (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  receipt_no VARCHAR(40) NOT NULL UNIQUE,
  order_id BIGINT NOT NULL,
  receipt_type ENUM('single','split') NOT NULL DEFAULT 'single',
  amount DECIMAL(12,2) NOT NULL,
  payload_json TEXT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_receipts_order FOREIGN KEY (order_id) REFERENCES orders(id),
  INDEX idx_receipts_order (order_id),
  INDEX idx_receipts_created (created_at)
);

CREATE TABLE IF NOT EXISTS bills (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  bill_no VARCHAR(40) NOT NULL UNIQUE,
  receipt_id BIGINT NOT NULL,
  order_id BIGINT NOT NULL,
  amount DECIMAL(12,2) NOT NULL,
  status ENUM('pending','paid') NOT NULL DEFAULT 'pending',
  payment_method ENUM('cash','mpesa','card') NULL,
  paid_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_bills_receipt FOREIGN KEY (receipt_id) REFERENCES receipts(id),
  CONSTRAINT fk_bills_order FOREIGN KEY (order_id) REFERENCES orders(id),
  INDEX idx_bills_status_created (status, created_at),
  INDEX idx_bills_order (order_id)
);

-- Migration: V7__add_purchases_inventory.sql
ALTER TABLE products
  ADD COLUMN IF NOT EXISTS stock_qty DECIMAL(12,3) NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS stock_unit VARCHAR(20) NOT NULL DEFAULT 'unit';

CREATE TABLE IF NOT EXISTS suppliers (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(160) NOT NULL UNIQUE,
  phone VARCHAR(40),
  contact_person VARCHAR(120),
  active TINYINT(1) NOT NULL DEFAULT 1,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS purchases (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  purchase_no VARCHAR(40) NOT NULL UNIQUE,
  supplier_id BIGINT,
  status ENUM('draft','received','cancelled') NOT NULL DEFAULT 'received',
  total_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
  invoice_no VARCHAR(80),
  notes TEXT,
  purchased_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_by_user_id BIGINT NOT NULL,
  CONSTRAINT fk_purchases_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers(id),
  CONSTRAINT fk_purchases_user FOREIGN KEY (created_by_user_id) REFERENCES users(id),
  INDEX idx_purchases_supplier_date (supplier_id, purchased_at),
  INDEX idx_purchases_status_date (status, purchased_at)
);

CREATE TABLE IF NOT EXISTS purchase_items (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  purchase_id BIGINT NOT NULL,
  product_id BIGINT,
  item_name VARCHAR(160) NOT NULL,
  qty DECIMAL(12,3) NOT NULL,
  unit_cost DECIMAL(12,2) NOT NULL,
  line_total DECIMAL(12,2) NOT NULL,
  CONSTRAINT fk_purchase_items_purchase FOREIGN KEY (purchase_id) REFERENCES purchases(id),
  CONSTRAINT fk_purchase_items_product FOREIGN KEY (product_id) REFERENCES products(id),
  INDEX idx_purchase_items_purchase (purchase_id),
  INDEX idx_purchase_items_product (product_id)
);

CREATE TABLE IF NOT EXISTS stock_movements (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  product_id BIGINT NOT NULL,
  source_type VARCHAR(30) NOT NULL,
  source_id BIGINT NOT NULL,
  qty_delta DECIMAL(12,3) NOT NULL,
  unit_cost DECIMAL(12,2),
  created_by_user_id BIGINT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_stock_movements_product FOREIGN KEY (product_id) REFERENCES products(id),
  CONSTRAINT fk_stock_movements_user FOREIGN KEY (created_by_user_id) REFERENCES users(id),
  INDEX idx_stock_movements_product_date (product_id, created_at),
  INDEX idx_stock_movements_source (source_type, source_id)
);

-- Migration: V8__add_user_real_names.sql
ALTER TABLE users
  ADD COLUMN IF NOT EXISTS real_name VARCHAR(120) NULL AFTER username;

UPDATE users SET real_name = 'Administrator' WHERE username = 'admin' AND real_name IS NULL;
UPDATE users SET real_name = 'Manager' WHERE username = 'manager' AND real_name IS NULL;
UPDATE users SET real_name = 'Super Waiter' WHERE username = 'super_waiter' AND real_name IS NULL;
UPDATE users SET real_name = 'Waiter' WHERE username = 'waiter' AND real_name IS NULL;

-- Migration: V9__add_bill_items.sql
CREATE TABLE IF NOT EXISTS bill_items (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  bill_id BIGINT NOT NULL,
  order_item_id BIGINT NOT NULL,
  item_name VARCHAR(160) NOT NULL,
  qty INT NOT NULL,
  unit_price DECIMAL(12,2) NOT NULL,
  line_total DECIMAL(12,2) NOT NULL,
  CONSTRAINT fk_bill_items_bill FOREIGN KEY (bill_id) REFERENCES bills(id),
  CONSTRAINT fk_bill_items_order_item FOREIGN KEY (order_item_id) REFERENCES order_items(id),
  INDEX idx_bill_items_bill (bill_id),
  INDEX idx_bill_items_order_item (order_item_id)
);

ALTER TABLE payments
  ADD COLUMN bill_id BIGINT NULL,
  ADD INDEX idx_payments_bill (bill_id),
  ADD CONSTRAINT fk_pay_bill FOREIGN KEY (bill_id) REFERENCES bills(id);

-- Migration: V10__add_supervisor_role.sql
ALTER TABLE users
  MODIFY role ENUM('admin','manager','supervisor','super_waiter','waiter') NOT NULL;

INSERT INTO users (username, real_name, role, pin_hash, active)
SELECT 'supervisor', 'Supervisor', 'supervisor', SHA2('55556666', 256), 1
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'supervisor');

-- Migration: V11__widen_payment_confirmation_fields.sql
ALTER TABLE payments
  MODIFY mpesa_last4 VARCHAR(20),
  MODIFY paynet_time VARCHAR(8);

-- Migration: V12__add_system_settings.sql
CREATE TABLE IF NOT EXISTS system_settings (
  setting_key VARCHAR(80) PRIMARY KEY,
  setting_value VARCHAR(255) NOT NULL,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

INSERT INTO system_settings(setting_key, setting_value)
VALUES('service_mode', 'both')
ON DUPLICATE KEY UPDATE setting_value = setting_value;

-- Migration: V13__add_payment_verified_at.sql
ALTER TABLE payments
  ADD COLUMN verified_at TIMESTAMP NULL,
  ADD INDEX idx_payments_verified_at (verified_at);

-- Migration: V14__add_order_voiding.sql
ALTER TABLE orders
  MODIFY status ENUM('open','pending','closed','voided') NOT NULL DEFAULT 'open',
  ADD COLUMN IF NOT EXISTS voided_by_user_id BIGINT NULL,
  ADD COLUMN IF NOT EXISTS voided_at TIMESTAMP NULL,
  ADD COLUMN IF NOT EXISTS void_reason VARCHAR(255) NULL;

ALTER TABLE bills
  MODIFY status ENUM('pending','paid','voided') NOT NULL DEFAULT 'pending';

CREATE INDEX IF NOT EXISTS idx_orders_voided_by ON orders (voided_by_user_id, voided_at);

-- Migration: V15__rename_restaurant_service_mode.sql
UPDATE system_settings
SET setting_value = 'kitchen'
WHERE setting_key = 'service_mode' AND setting_value = 'restaurant';

-- Migration: V16__track_printed_order_item_qty.sql
ALTER TABLE order_items
  ADD COLUMN IF NOT EXISTS printed_qty INT NOT NULL DEFAULT 0;

UPDATE order_items oi
JOIN orders o ON o.id = oi.order_id
SET oi.printed_qty = oi.qty
WHERE o.status IN ('pending', 'closed', 'voided') AND oi.printed_qty = 0;

-- Migration: V17__add_login_image_setting.sql
ALTER TABLE system_settings
  MODIFY setting_value LONGTEXT NOT NULL;

INSERT INTO system_settings(setting_key, setting_value)
VALUES('login_image_url', '/brand/restaurant-service.svg')
ON DUPLICATE KEY UPDATE setting_value = setting_value;

-- Migration: V18__add_commission_settings.sql
INSERT INTO system_settings(setting_key, setting_value)
VALUES
  ('commission_mode', 'fixed'),
  ('commission_fixed_percent', '1'),
  ('commission_tiers', '100000:1,200000:2,300000:3,400000:4,500000:5,600000:6,700000:7,800000:8,900000:9,1000000:10'),
  ('kitchen_commission_percent', '2')
ON DUPLICATE KEY UPDATE setting_value = setting_value;

-- Migration: V19__repair_payment_confirmation_columns.sql
ALTER TABLE payments
  MODIFY COLUMN mpesa_last4 VARCHAR(20),
  MODIFY COLUMN paynet_time VARCHAR(8);

-- Migration: V20__add_product_categories.sql
CREATE TABLE IF NOT EXISTS product_categories (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tab ENUM('kitchen','bar') NOT NULL,
  category_key VARCHAR(40) NOT NULL,
  label VARCHAR(80) NOT NULL,
  active TINYINT(1) NOT NULL DEFAULT 1,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_product_categories_tab_key (tab, category_key),
  INDEX idx_product_categories_tab_active (tab, active, label)
);

INSERT INTO product_categories(tab, category_key, label, active)
VALUES
  ('kitchen', 'mains', 'Mains', 1),
  ('kitchen', 'grills', 'Grills', 1),
  ('kitchen', 'sides', 'Sides', 1),
  ('bar', 'beers', 'Beers', 1),
  ('bar', 'whisky', 'Whisky', 1),
  ('bar', 'beverages', 'Beverages', 1),
  ('bar', 'cocktails', 'Cocktails', 1)
ON DUPLICATE KEY UPDATE label = VALUES(label), active = 1;

-- Migration: V21__repair_bill_payment_pending_columns.sql
ALTER TABLE payments
  ADD COLUMN IF NOT EXISTS bill_id BIGINT NULL,
  ADD INDEX IF NOT EXISTS idx_payments_bill (bill_id),
  MODIFY COLUMN mpesa_last4 VARCHAR(20),
  MODIFY COLUMN paynet_time VARCHAR(20);

-- Migration: V22__add_user_shifts.sql
CREATE TABLE user_shifts (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  status ENUM('active','ended','reconciled') NOT NULL DEFAULT 'active',
  started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  ended_at TIMESTAMP NULL,
  reconciled_at TIMESTAMP NULL,
  started_by_user_id BIGINT NULL,
  ended_by_user_id BIGINT NULL,
  reconciled_by_user_id BIGINT NULL,
  manager_notes VARCHAR(500),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_user_shifts_user_status (user_id, status),
  KEY idx_user_shifts_status_ended (status, ended_at),
  CONSTRAINT fk_user_shifts_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_user_shifts_started_by FOREIGN KEY (started_by_user_id) REFERENCES users(id),
  CONSTRAINT fk_user_shifts_ended_by FOREIGN KEY (ended_by_user_id) REFERENCES users(id),
  CONSTRAINT fk_user_shifts_reconciled_by FOREIGN KEY (reconciled_by_user_id) REFERENCES users(id)
);

INSERT INTO user_shifts (user_id, status, started_by_user_id)
SELECT u.id, 'active', u.id
FROM users u
WHERE u.role IN ('waiter', 'super_waiter')
  AND NOT EXISTS (
    SELECT 1 FROM user_shifts s
    WHERE s.user_id = u.id AND s.status = 'active'
  );

-- Migration: V23__rename_card_payment_mode_to_pdq.sql
ALTER TABLE payments
  MODIFY COLUMN mode ENUM('cash','mpesa','card','pdq') NOT NULL;

ALTER TABLE bills
  MODIFY COLUMN payment_method ENUM('cash','mpesa','card','pdq') NULL;

-- Migration: V24__add_companies.sql
-- Add companies table for multi-company management
CREATE TABLE IF NOT EXISTS companies (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(255) NOT NULL,
  active TINYINT(1) NOT NULL DEFAULT 1,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_companies_name (name)
);

-- Seed a default company if none exists
INSERT INTO companies (name, active)
SELECT 'Default Company', 1
WHERE NOT EXISTS (SELECT 1 FROM companies WHERE name = 'Default Company');


-- Migration: V25__add_company_archival_support.sql
-- Add company relationship support and archival tracking
-- Add columns for company_id and archived_at if they don't exist
ALTER TABLE users ADD COLUMN IF NOT EXISTS company_id BIGINT AFTER id;
ALTER TABLE companies ADD COLUMN IF NOT EXISTS archived_at TIMESTAMP NULL DEFAULT NULL;
-- Create indexes (correct MySQL/MariaDB syntax)
CREATE INDEX IF NOT EXISTS idx_users_company ON users (company_id);
CREATE INDEX IF NOT EXISTS idx_companies_active ON companies (active);


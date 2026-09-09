CREATE TABLE IF NOT EXISTS users (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(50) NOT NULL UNIQUE,
  real_name VARCHAR(100),
  role ENUM('admin','manager','super_waiter','waiter') NOT NULL,
  pin_hash VARCHAR(255) NOT NULL,
  active TINYINT(1) NOT NULL DEFAULT 1,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_users_role_active (role, active),
  KEY idx_users_active (active)
);

-- Insert an admin user with PIN 12345678
INSERT IGNORE INTO users (username, real_name, role, pin_hash, active)
VALUES ('admin', 'Administrator', 'admin', SHA2('12345678', 256), 1);
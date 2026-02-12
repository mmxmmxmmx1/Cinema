ALTER TABLE member_orders
  ADD COLUMN payment_attempts INT NOT NULL DEFAULT 0,
  ADD COLUMN failed_at TIMESTAMP NULL DEFAULT NULL,
  ADD COLUMN expired_at TIMESTAMP NULL DEFAULT NULL,
  ADD COLUMN failure_reason VARCHAR(255) NULL DEFAULT NULL,
  ADD COLUMN payment_reference VARCHAR(100) NULL DEFAULT NULL,
  ADD COLUMN payment_mode VARCHAR(20) NULL DEFAULT NULL,
  ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  ADD KEY idx_member_orders_status_created (status, created_at);

CREATE TABLE IF NOT EXISTS payment_transactions (
  id BIGINT NOT NULL AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  member_id BIGINT NOT NULL,
  amount INT NOT NULL,
  mode VARCHAR(20) NOT NULL,
  gateway VARCHAR(50) NOT NULL,
  status VARCHAR(20) NOT NULL,
  reference VARCHAR(100) NULL DEFAULT NULL,
  error_message VARCHAR(255) NULL DEFAULT NULL,
  requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  completed_at TIMESTAMP NULL DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_payment_tx_order_time (order_id, requested_at),
  KEY idx_payment_tx_member_time (member_id, requested_at),
  CONSTRAINT fk_payment_tx_order FOREIGN KEY (order_id) REFERENCES member_orders (id) ON DELETE CASCADE,
  CONSTRAINT fk_payment_tx_member FOREIGN KEY (member_id) REFERENCES members (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS notifications (
  id BIGINT NOT NULL AUTO_INCREMENT,
  member_id BIGINT NOT NULL,
  category VARCHAR(30) NOT NULL,
  title VARCHAR(150) NOT NULL,
  message VARCHAR(500) NOT NULL,
  read_at TIMESTAMP NULL DEFAULT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_notifications_member_time (member_id, created_at),
  KEY idx_notifications_member_read (member_id, read_at),
  CONSTRAINT fk_notifications_member FOREIGN KEY (member_id) REFERENCES members (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS audit_logs (
  id BIGINT NOT NULL AUTO_INCREMENT,
  actor_type VARCHAR(30) NOT NULL,
  actor_id VARCHAR(100) NULL DEFAULT NULL,
  action VARCHAR(50) NOT NULL,
  target_type VARCHAR(30) NULL DEFAULT NULL,
  target_id VARCHAR(100) NULL DEFAULT NULL,
  result VARCHAR(20) NOT NULL,
  detail VARCHAR(1000) NULL DEFAULT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_audit_logs_time (created_at),
  KEY idx_audit_logs_action_time (action, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

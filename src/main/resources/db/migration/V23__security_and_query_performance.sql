CREATE TABLE IF NOT EXISTS member_password_reset_tokens (
  token VARCHAR(64) NOT NULL,
  member_id BIGINT NOT NULL,
  expires_at TIMESTAMP NOT NULL,
  used_at TIMESTAMP NULL DEFAULT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (token),
  KEY idx_member_password_reset_tokens_member_expiry (member_id, expires_at),
  KEY idx_member_password_reset_tokens_expiry (expires_at),
  KEY idx_member_password_reset_tokens_used (used_at),
  CONSTRAINT fk_member_password_reset_tokens_member
    FOREIGN KEY (member_id) REFERENCES members (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE member_orders
  ADD KEY idx_member_orders_status_paid_at (status, paid_at),
  ADD KEY idx_member_orders_status_cancelled_at (status, cancelled_at),
  ADD KEY idx_member_orders_member_status_created (member_id, status, created_at);

ALTER TABLE member_point_ledger
  ADD KEY idx_member_point_ledger_happened_at (happened_at);

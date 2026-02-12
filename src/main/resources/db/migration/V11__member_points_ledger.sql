CREATE TABLE IF NOT EXISTS member_point_ledger (
  id BIGINT NOT NULL AUTO_INCREMENT,
  member_id BIGINT NOT NULL,
  event_key VARCHAR(100) NOT NULL,
  event_type VARCHAR(20) NOT NULL,
  ref_order_id BIGINT NULL,
  ref_redemption_id BIGINT NULL,
  amount INT NOT NULL DEFAULT 0,
  points_delta INT NOT NULL,
  description VARCHAR(255) NOT NULL,
  happened_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_member_point_ledger_event_key (event_key),
  KEY idx_member_point_ledger_member_time (member_id, happened_at),
  CONSTRAINT fk_member_point_ledger_member FOREIGN KEY (member_id) REFERENCES members (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS member_point_balance (
  member_id BIGINT NOT NULL,
  points_balance INT NOT NULL DEFAULT 0,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (member_id),
  CONSTRAINT fk_member_point_balance_member FOREIGN KEY (member_id) REFERENCES members (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO member_point_ledger
  (member_id, event_key, event_type, ref_order_id, amount, points_delta, description, happened_at)
SELECT
  mo.member_id,
  CONCAT('ORDER_PAID:', mo.id),
  'ORDER_PAID',
  mo.id,
  mo.total_price,
  FLOOR(mo.total_price / 10),
  CONCAT('訂單 #', mo.id, ' 付款回饋'),
  COALESCE(mo.paid_at, mo.created_at)
FROM member_orders mo
WHERE mo.status = 'PAID'
  AND mo.total_price > 0;

INSERT IGNORE INTO member_point_ledger
  (member_id, event_key, event_type, ref_redemption_id, amount, points_delta, description, happened_at)
SELECT
  pr.member_id,
  CONCAT('REDEEM:', pr.id),
  'REDEEM',
  pr.id,
  0,
  -pr.points_spent,
  CONCAT('點數兌換：', pr.reward_name),
  pr.created_at
FROM member_point_redemptions pr;

INSERT INTO member_point_balance (member_id, points_balance)
SELECT member_id, COALESCE(SUM(points_delta), 0)
FROM member_point_ledger
GROUP BY member_id
ON DUPLICATE KEY UPDATE points_balance = VALUES(points_balance);

CREATE TABLE IF NOT EXISTS member_orders (
  id BIGINT NOT NULL AUTO_INCREMENT,
  member_id BIGINT NOT NULL,
  movie_id VARCHAR(50) NOT NULL,
  showtime_id VARCHAR(50) NOT NULL,
  auditorium VARCHAR(100) NOT NULL,
  total_qty INT NOT NULL,
  status VARCHAR(20) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  paid_at TIMESTAMP NULL DEFAULT NULL,
  cancelled_at TIMESTAMP NULL DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_member_orders_member_time (member_id, created_at),
  CONSTRAINT fk_member_orders_member FOREIGN KEY (member_id) REFERENCES members (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS member_order_items (
  order_id BIGINT NOT NULL,
  seat_id VARCHAR(10) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (order_id, seat_id),
  CONSTRAINT fk_member_order_items_order FOREIGN KEY (order_id) REFERENCES member_orders (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE member_tickets
  ADD COLUMN order_id BIGINT NULL;

ALTER TABLE member_tickets
  ADD KEY idx_member_tickets_order (order_id);

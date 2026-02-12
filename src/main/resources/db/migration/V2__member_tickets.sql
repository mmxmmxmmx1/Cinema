CREATE TABLE IF NOT EXISTS member_tickets (
  id BIGINT NOT NULL AUTO_INCREMENT,
  member_id BIGINT NOT NULL,
  movie_id VARCHAR(50) NOT NULL,
  showtime_id VARCHAR(50) NOT NULL,
  auditorium VARCHAR(100) NOT NULL,
  seat_id VARCHAR(10) NOT NULL,
  purchased_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_member_tickets_showtime_seat (showtime_id, seat_id),
  KEY idx_member_tickets_member_time (member_id, purchased_at),
  CONSTRAINT fk_member_tickets_member FOREIGN KEY (member_id) REFERENCES members (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


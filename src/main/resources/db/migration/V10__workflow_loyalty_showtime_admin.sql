ALTER TABLE maintenance_requests
  ADD COLUMN assignee VARCHAR(100) NULL AFTER requester,
  ADD COLUMN started_at TIMESTAMP NULL DEFAULT NULL AFTER updated_at,
  ADD COLUMN resolved_at TIMESTAMP NULL DEFAULT NULL AFTER started_at,
  ADD COLUMN closed_at TIMESTAMP NULL DEFAULT NULL AFTER resolved_at,
  ADD COLUMN closed_by VARCHAR(100) NULL AFTER closed_at,
  ADD COLUMN resolution_note VARCHAR(500) NULL AFTER closed_by;

ALTER TABLE maintenance_requests
  ADD KEY idx_maintenance_assignee (assignee),
  ADD KEY idx_maintenance_closed_at (closed_at);

CREATE TABLE IF NOT EXISTS member_point_redemptions (
  id BIGINT NOT NULL AUTO_INCREMENT,
  member_id BIGINT NOT NULL,
  reward_code VARCHAR(50) NOT NULL,
  reward_name VARCHAR(100) NOT NULL,
  points_spent INT NOT NULL,
  note VARCHAR(255) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_point_redemptions_member_time (member_id, created_at),
  CONSTRAINT fk_point_redemptions_member FOREIGN KEY (member_id) REFERENCES members (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS showtime_overrides (
  id BIGINT NOT NULL AUTO_INCREMENT,
  movie_id VARCHAR(50) NOT NULL,
  showtime_id VARCHAR(50) NOT NULL,
  start_time VARCHAR(5) NOT NULL,
  duration_minutes INT NOT NULL,
  auditorium VARCHAR(100) NOT NULL,
  enabled TINYINT NOT NULL DEFAULT 1,
  updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_showtime_overrides_movie_showtime (movie_id, showtime_id),
  KEY idx_showtime_overrides_movie (movie_id),
  KEY idx_showtime_overrides_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

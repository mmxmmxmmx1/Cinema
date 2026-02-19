CREATE TABLE IF NOT EXISTS login_attempt_state (
  realm VARCHAR(32) NOT NULL,
  username VARCHAR(255) NOT NULL,
  failed_attempts INT NOT NULL DEFAULT 0,
  locked_until DATETIME NULL,
  last_seen DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (realm, username),
  KEY idx_login_attempt_state_locked_until (locked_until),
  KEY idx_login_attempt_state_last_seen (last_seen)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS api_rate_limit_events (
  id BIGINT NOT NULL AUTO_INCREMENT,
  action VARCHAR(120) NOT NULL,
  subject VARCHAR(255) NOT NULL,
  happened_at DATETIME NOT NULL,
  PRIMARY KEY (id),
  KEY idx_api_rate_limit_events_action_subject_time (action, subject, happened_at),
  KEY idx_api_rate_limit_events_happened_at (happened_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


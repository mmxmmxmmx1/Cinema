-- Align watchlist movie_id with actual catalog IDs (e.g. mv-01).
-- Some pre-Flyway-baseline dev databases may not have this table yet.
CREATE TABLE IF NOT EXISTS user_watchlist (
  user_id BIGINT NOT NULL,
  movie_id VARCHAR(50) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, movie_id),
  CONSTRAINT fk_watchlist_member FOREIGN KEY (user_id) REFERENCES members (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- If the table already exists from old schema, enforce string movie_id type.
ALTER TABLE user_watchlist
  MODIFY COLUMN movie_id VARCHAR(50) NOT NULL;

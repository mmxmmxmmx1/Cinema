CREATE TABLE IF NOT EXISTS cinema_locations (
  location_code VARCHAR(50) NOT NULL,
  location_name VARCHAR(120) NOT NULL,
  city VARCHAR(60) NULL,
  enabled TINYINT NOT NULL DEFAULT 1,
  sort_order INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (location_code),
  KEY idx_cinema_locations_enabled_sort (enabled, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO cinema_locations (location_code, location_name, city, enabled, sort_order) VALUES
  ('taipei-main', '台北信義館', '台北', 1, 10),
  ('taichung-park', '台中站前館', '台中', 1, 20),
  ('kaohsiung-bay', '高雄港灣館', '高雄', 1, 30)
AS incoming
ON DUPLICATE KEY UPDATE
  location_name = incoming.location_name,
  city = incoming.city,
  enabled = incoming.enabled,
  sort_order = incoming.sort_order;

SET @has_ms_location_code := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'movie_showtimes'
    AND column_name = 'location_code'
);
SET @sql := IF(
  @has_ms_location_code = 0,
  'ALTER TABLE movie_showtimes ADD COLUMN location_code VARCHAR(50) NULL AFTER auditorium',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_ms_location_name := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'movie_showtimes'
    AND column_name = 'location_name'
);
SET @sql := IF(
  @has_ms_location_name = 0,
  'ALTER TABLE movie_showtimes ADD COLUMN location_name VARCHAR(120) NULL AFTER location_code',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_so_location_code := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'showtime_overrides'
    AND column_name = 'location_code'
);
SET @sql := IF(
  @has_so_location_code = 0,
  'ALTER TABLE showtime_overrides ADD COLUMN location_code VARCHAR(50) NULL AFTER auditorium',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_so_location_name := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'showtime_overrides'
    AND column_name = 'location_name'
);
SET @sql := IF(
  @has_so_location_name = 0,
  'ALTER TABLE showtime_overrides ADD COLUMN location_name VARCHAR(120) NULL AFTER location_code',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE movie_showtimes
SET location_code = CASE
  WHEN showtime_id REGEXP '(-st1|-st4)$' THEN 'taipei-main'
  WHEN showtime_id REGEXP '(-st2|-st5)$' THEN 'taichung-park'
  WHEN showtime_id REGEXP '(-st3|-st6)$' THEN 'kaohsiung-bay'
  ELSE 'taipei-main'
END
WHERE location_code IS NULL OR location_code = '';

UPDATE movie_showtimes ms
LEFT JOIN cinema_locations cl ON cl.location_code = ms.location_code
SET ms.location_name = COALESCE(cl.location_name, '台北信義館')
WHERE ms.location_name IS NULL OR ms.location_name = '';

UPDATE showtime_overrides
SET location_code = 'taipei-main'
WHERE location_code IS NULL OR location_code = '';

UPDATE showtime_overrides sox
LEFT JOIN cinema_locations cl ON cl.location_code = sox.location_code
SET sox.location_name = COALESCE(cl.location_name, '台北信義館')
WHERE sox.location_name IS NULL OR sox.location_name = '';

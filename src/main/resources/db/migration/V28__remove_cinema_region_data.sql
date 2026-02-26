-- 本專案不提供電影院地區/地址資訊；避免再次出現台北/台中/高雄等地區資料。
INSERT INTO cinema_locations (location_code, location_name, city, enabled, sort_order) VALUES
  ('cinema-default', '未提供', NULL, 1, 0)
ON DUPLICATE KEY UPDATE
  location_name = VALUES(location_name),
  city = VALUES(city),
  enabled = VALUES(enabled),
  sort_order = VALUES(sort_order);

DELETE FROM cinema_locations
WHERE location_code <> 'cinema-default';

SET @has_ms_location_code := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'movie_showtimes'
    AND column_name = 'location_code'
);
SET @has_ms_location_name := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'movie_showtimes'
    AND column_name = 'location_name'
);

SET @sql := IF(
  @has_ms_location_code = 1 AND @has_ms_location_name = 1,
  'UPDATE movie_showtimes
      SET location_code = NULL,
          location_name = NULL',
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
SET @has_so_location_name := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'showtime_overrides'
    AND column_name = 'location_name'
);

SET @sql := IF(
  @has_so_location_code = 1 AND @has_so_location_name = 1,
  'UPDATE showtime_overrides
      SET location_code = NULL,
          location_name = NULL',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

INSERT INTO cinema_locations (location_code, location_name, city, enabled, sort_order) VALUES
  ('taipei-main', '台北信義館', '台北', 1, 10)
ON DUPLICATE KEY UPDATE
  location_name = VALUES(location_name),
  city = VALUES(city),
  enabled = VALUES(enabled),
  sort_order = VALUES(sort_order);

DELETE FROM cinema_locations
WHERE location_code <> 'taipei-main';

UPDATE movie_showtimes
SET location_code = 'taipei-main',
    location_name = '台北信義館'
WHERE location_code IS NULL
   OR location_code <> 'taipei-main'
   OR location_name IS NULL
   OR location_name <> '台北信義館';

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
     SET location_code = ''taipei-main'',
         location_name = ''台北信義館''
   WHERE location_code IS NULL
      OR location_code <> ''taipei-main''
      OR location_name IS NULL
      OR location_name <> ''台北信義館''',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

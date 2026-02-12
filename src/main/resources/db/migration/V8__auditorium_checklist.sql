CREATE TABLE IF NOT EXISTS auditorium_checklist (
  id BIGINT NOT NULL AUTO_INCREMENT,
  check_date DATE NOT NULL,
  auditorium VARCHAR(100) NOT NULL,
  item_code VARCHAR(50) NOT NULL,
  item_label VARCHAR(100) NOT NULL,
  checked TINYINT NOT NULL DEFAULT 0,
  note VARCHAR(255) NULL,
  updated_by VARCHAR(100) NOT NULL,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_auditorium_checklist_daily (check_date, auditorium, item_code),
  KEY idx_auditorium_checklist_date (check_date),
  KEY idx_auditorium_checklist_hall (auditorium)
);

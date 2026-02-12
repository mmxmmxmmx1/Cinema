CREATE TABLE IF NOT EXISTS maintenance_requests (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tracking_no VARCHAR(40) DEFAULT NULL,
  requester VARCHAR(100) NOT NULL,
  auditorium VARCHAR(100) DEFAULT NULL,
  title VARCHAR(150) NOT NULL,
  description VARCHAR(1000) NOT NULL,
  priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
  status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_maintenance_tracking_no (tracking_no),
  KEY idx_maintenance_requester (requester),
  KEY idx_maintenance_status (status),
  KEY idx_maintenance_created_at (created_at)
);

CREATE TABLE IF NOT EXISTS employee_todos (
  id BIGINT NOT NULL AUTO_INCREMENT,
  todo_date DATE NOT NULL,
  line_no INT NOT NULL,
  item_text VARCHAR(255) NOT NULL,
  updated_by VARCHAR(100) NOT NULL,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_employee_todos_date_line (todo_date, line_no),
  KEY idx_employee_todos_date (todo_date)
);

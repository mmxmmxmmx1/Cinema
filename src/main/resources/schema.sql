-- 帳號（認證重點）
DROP TABLE IF EXISTS user_roles;
DROP TABLE IF EXISTS role_permissions;
DROP TABLE IF EXISTS permissions;
DROP TABLE IF EXISTS roles;
DROP TABLE IF EXISTS users;

CREATE TABLE users (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(50) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,   -- 存 {bcrypt}... 或 $2b$
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 角色
CREATE TABLE roles (
  id    BIGINT PRIMARY KEY AUTO_INCREMENT,
  code  VARCHAR(50) NOT NULL UNIQUE,     -- e.g. 'ROLE_ADMIN', 'ROLE_MANAGER'
  name  VARCHAR(100) NOT NULL            -- 顯示用名稱
);

-- 使用者與角色對應（多對多）
CREATE TABLE user_roles (
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  PRIMARY KEY (user_id, role_id),
  FOREIGN KEY (user_id) REFERENCES users(id),
  FOREIGN KEY (role_id) REFERENCES roles(id)
);

--（可選，細粒度權限）
CREATE TABLE permissions (
  id    BIGINT PRIMARY KEY AUTO_INCREMENT,
  code  VARCHAR(100) NOT NULL UNIQUE,    -- e.g. 'ORDER_READ','ORDER_WRITE'
  name  VARCHAR(100) NOT NULL
);

CREATE TABLE role_permissions (
  role_id BIGINT NOT NULL,
  perm_id BIGINT NOT NULL,
  PRIMARY KEY (role_id, perm_id),
  FOREIGN KEY (role_id) REFERENCES roles(id),
  FOREIGN KEY (perm_id) REFERENCES permissions(id)
);

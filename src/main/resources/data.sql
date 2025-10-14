SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- 插入角色資料
INSERT INTO roles (code, name) VALUES ('ROLE_USER', '一般使用者');
INSERT INTO roles (code, name) VALUES ('ROLE_EMPLOYEE', '員工');
INSERT INTO roles (code, name) VALUES ('ROLE_ADMIN', '管理員');

-- 純會員帳號
INSERT INTO users (username, password, first_name, last_name, email, phone, user_type)
VALUES ('member001', '{noop}member123', '小明', '王', 'member001@example.com', '0912345678', 'CUSTOMER');

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u JOIN roles r ON r.code = 'ROLE_USER' WHERE u.username = 'member001';

-- 純員工帳號
INSERT INTO users (username, password, first_name, last_name, email, phone, user_type)
VALUES ('employee001', '{noop}emp123', '小華', '李', 'employee001@example.com', '0923456789', 'EMPLOYEE');

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u JOIN roles r ON r.code = 'ROLE_EMPLOYEE' WHERE u.username = 'employee001';

-- 既是會員又是員工的帳號
INSERT INTO users (username, password, first_name, last_name, email, phone, user_type)
VALUES ('abc123', '{noop}abc123', '小美', '陳', 'abc123@example.com', '0934567890', 'BOTH');

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u JOIN roles r ON r.code = 'ROLE_USER' WHERE u.username = 'abc123';

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u JOIN roles r ON r.code = 'ROLE_EMPLOYEE' WHERE u.username = 'abc123';

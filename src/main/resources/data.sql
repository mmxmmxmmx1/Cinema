INSERT INTO roles (code, name) VALUES ('ROLE_USER', '一般使用者');
INSERT INTO roles (code, name) VALUES ('ROLE_ADMIN', '管理員');

INSERT INTO users (username, password, first_name, last_name, email, phone)
VALUES ('abc123', '{noop}abc123', 'Default', 'Admin', 'abc123@example.com', NULL);

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u JOIN roles r ON r.code = 'ROLE_USER' WHERE u.username = 'abc123';

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u JOIN roles r ON r.code = 'ROLE_ADMIN' WHERE u.username = 'abc123';

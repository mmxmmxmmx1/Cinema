-- Dev-only demo data (loaded only when spring.flyway.locations includes db/dev-migration).

INSERT INTO members (nickname, first_name, last_name, email, phone, password) VALUES
  ('member01', 'Test', 'Member', NULL, NULL, '{bcrypt}$2b$12$Y4ca9muqdHe3BzeEXn77OeE2uMzPUY.UOafXk.Mn48T3uSqEKZgYG'),
  ('test123', 'Test', 'Member', NULL, NULL, '{bcrypt}$2b$12$fvutf6O.2MQcS.h7p988VucK5//J3WzcNHXZQzQgmRM.dm50ABkCO'),
  ('emp01', 'Test', 'Member', NULL, NULL, '{bcrypt}$2b$12$5Z1WA.GNPjRcbtDOsXD/6eKCHg6AZGqACQyqP9F/PIl3z8AWSjmjO'),
  ('em01', 'Test', 'Member', NULL, NULL, '{bcrypt}$2b$12$Xl5femwENwwR9pZ/0Nv5Ket0I4GsYZutuwgXgz75at1mx5hFrnvgu')
AS incoming
ON DUPLICATE KEY UPDATE
  password = incoming.password;

INSERT INTO employee (nickname, first_name, last_name, email, phone, password, role_id) VALUES
  ('member01', 'Test', 'Employee', NULL, NULL, '{bcrypt}$2b$12$Y4ca9muqdHe3BzeEXn77OeE2uMzPUY.UOafXk.Mn48T3uSqEKZgYG', (SELECT id FROM roles WHERE code = 'EMPLOYEE' LIMIT 1)),
  ('test123', 'Test', 'Employee', NULL, NULL, '{bcrypt}$2b$12$fvutf6O.2MQcS.h7p988VucK5//J3WzcNHXZQzQgmRM.dm50ABkCO', (SELECT id FROM roles WHERE code = 'EMPLOYEE' LIMIT 1)),
  ('emp01', 'Test', 'Employee', NULL, NULL, '{bcrypt}$2b$12$5Z1WA.GNPjRcbtDOsXD/6eKCHg6AZGqACQyqP9F/PIl3z8AWSjmjO', (SELECT id FROM roles WHERE code = 'EMPLOYEE' LIMIT 1)),
  ('em01', 'Test', 'Employee', NULL, NULL, '{bcrypt}$2b$12$Xl5femwENwwR9pZ/0Nv5Ket0I4GsYZutuwgXgz75at1mx5hFrnvgu', (SELECT id FROM roles WHERE code = 'EMPLOYEE' LIMIT 1))
AS incoming
ON DUPLICATE KEY UPDATE
  password = incoming.password,
  role_id = incoming.role_id;

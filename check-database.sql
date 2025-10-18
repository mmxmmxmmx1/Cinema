-- 查看所有資料表
SHOW TABLES;

-- 查看 roles 表結構
DESCRIBE roles;

-- 查看 roles 表內容
SELECT * FROM roles;

-- 查看 members 表結構
DESCRIBE members;

-- 查看 members 表記錄數
SELECT COUNT(*) as member_count FROM members;

-- 查看 employee 表結構
DESCRIBE employee;

-- 查看 employee 表記錄數
SELECT COUNT(*) as employee_count FROM employee;

-- 查看所有表的記錄數統計
SELECT 
    'roles' as table_name, COUNT(*) as record_count FROM roles
UNION ALL
SELECT 
    'members' as table_name, COUNT(*) as record_count FROM members
UNION ALL
SELECT 
    'employee' as table_name, COUNT(*) as record_count FROM employee
UNION ALL
SELECT 
    'user_roles' as table_name, COUNT(*) as record_count FROM user_roles
UNION ALL
SELECT 
    'user_watchlist' as table_name, COUNT(*) as record_count FROM user_watchlist;

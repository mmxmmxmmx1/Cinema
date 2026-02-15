package com.example.cinema.dao;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EmployeeAdminDao {

    private final JdbcTemplate jdbcTemplate;

    public EmployeeAdminDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> findRecentFailures(int hours) {
        return jdbcTemplate.queryForList(
                "SELECT created_at, actor_type, actor_id, action, target_type, target_id, result, detail " +
                        "FROM audit_logs " +
                        "WHERE created_at >= DATE_SUB(NOW(), INTERVAL ? HOUR) AND result <> 'SUCCESS' " +
                        "ORDER BY created_at DESC LIMIT 100",
                hours);
    }

    public List<Map<String, Object>> findActionStats(int hours) {
        return jdbcTemplate.queryForList(
                "SELECT action, result, COUNT(*) AS cnt " +
                        "FROM audit_logs " +
                        "WHERE created_at >= DATE_SUB(NOW(), INTERVAL ? HOUR) " +
                        "GROUP BY action, result " +
                        "ORDER BY cnt DESC, action ASC",
                hours);
    }

    public int countTotalEvents(int hours) {
        Integer value = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE created_at >= DATE_SUB(NOW(), INTERVAL ? HOUR)",
                Integer.class,
                hours);
        return value == null ? 0 : value.intValue();
    }

    public int countFailedEvents(int hours) {
        Integer value = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE created_at >= DATE_SUB(NOW(), INTERVAL ? HOUR) AND result <> 'SUCCESS'",
                Integer.class,
                hours);
        return value == null ? 0 : value.intValue();
    }

    public int countPaymentFailures(int hours) {
        Integer value = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs " +
                        "WHERE created_at >= DATE_SUB(NOW(), INTERVAL ? HOUR) AND action = 'ORDER_PAY' AND result <> 'SUCCESS'",
                Integer.class,
                hours);
        return value == null ? 0 : value.intValue();
    }

    public int countCancelledOrders(int hours) {
        Integer value = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM member_orders " +
                        "WHERE cancelled_at IS NOT NULL AND cancelled_at >= DATE_SUB(NOW(), INTERVAL ? HOUR)",
                Integer.class,
                hours);
        return value == null ? 0 : value.intValue();
    }

    public List<Map<String, Object>> findRoles() {
        return jdbcTemplate.queryForList("SELECT id, code, name, level FROM roles ORDER BY level ASC");
    }

    public List<Map<String, Object>> findEmployees() {
        return jdbcTemplate.queryForList(
                "SELECT e.id, e.nickname, r.code AS role_code, r.level AS role_level " +
                        "FROM employee e " +
                        "JOIN roles r ON r.id = e.role_id " +
                        "ORDER BY r.level DESC, e.nickname ASC");
    }

    public int updateEmployeeRole(String nickname, String roleCode) {
        return jdbcTemplate.update(
                "UPDATE employee " +
                        "SET role_id = (SELECT id FROM roles WHERE code = ? LIMIT 1) " +
                        "WHERE nickname = ?",
                roleCode,
                nickname);
    }

    public void insertRoleUpdateAudit(String nickname, String roleCode) {
        jdbcTemplate.update(
                "INSERT INTO audit_logs (actor_type, actor_id, action, target_type, target_id, result, detail) " +
                        "VALUES ('EMPLOYEE', 'admin', 'EMPLOYEE_ROLE_UPDATE', 'EMPLOYEE', ?, 'SUCCESS', ?)",
                nickname,
                "role=" + roleCode);
    }
}

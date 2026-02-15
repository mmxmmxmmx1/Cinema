package com.example.cinema.service;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class OperationsDashboardService {

    private final JdbcTemplate jdbcTemplate;
    private final int pendingTimeoutMinutes;
    private final int notificationRetentionDays;

    public OperationsDashboardService(
            JdbcTemplate jdbcTemplate,
            @Value("${app.order.pending-timeout-minutes:15}") int pendingTimeoutMinutes,
            @Value("${app.notification.retention-days:30}") int notificationRetentionDays) {
        this.jdbcTemplate = jdbcTemplate;
        this.pendingTimeoutMinutes = Math.max(1, pendingTimeoutMinutes);
        this.notificationRetentionDays = Math.max(1, notificationRetentionDays);
    }

    public ManagerDashboardMetrics managerMetrics() {
        return new ManagerDashboardMetrics(
                intQuery("SELECT COALESCE(ROUND(COUNT(*) / NULLIF((SELECT COUNT(*) FROM member_tickets), 0) * 100, 0), 0) " +
                        "FROM member_orders WHERE status = 'PAID'"),
                stringQuery("SELECT DATE_FORMAT(paid_at, '%H:%i') FROM member_orders " +
                        "WHERE paid_at IS NOT NULL AND DATE(paid_at) = CURRENT_DATE " +
                        "GROUP BY DATE_FORMAT(paid_at, '%H:%i') ORDER BY COUNT(*) DESC LIMIT 1", "--"),
                intQuery("SELECT COUNT(*) FROM maintenance_requests WHERE status IN ('OPEN','IN_PROGRESS')"),
                intQuery("SELECT COUNT(*) FROM member_orders WHERE status = 'CANCELLED' AND DATE(cancelled_at) = CURRENT_DATE"),
                intQuery("SELECT COUNT(*) FROM member_orders WHERE status = 'PAID' AND DATE(paid_at) = CURRENT_DATE"));
    }

    public ItDashboardMetrics itMetrics() {
        int total24h = intQuery("SELECT COUNT(*) FROM audit_logs WHERE created_at >= DATE_SUB(NOW(), INTERVAL 24 HOUR)");
        int failed24h = intQuery(
                "SELECT COUNT(*) FROM audit_logs WHERE created_at >= DATE_SUB(NOW(), INTERVAL 24 HOUR) AND result <> 'SUCCESS'");
        int paymentFailed24h = intQuery(
                "SELECT COUNT(*) FROM audit_logs WHERE created_at >= DATE_SUB(NOW(), INTERVAL 24 HOUR) " +
                        "AND action = 'ORDER_PAY' AND result <> 'SUCCESS'");
        int paymentTotal24h = intQuery(
                "SELECT COUNT(*) FROM audit_logs WHERE created_at >= DATE_SUB(NOW(), INTERVAL 24 HOUR) " +
                        "AND action = 'ORDER_PAY'");
        int errorRate = total24h <= 0 ? 0 : (failed24h * 100 / total24h);
        int paymentFailRate = paymentTotal24h <= 0 ? 0 : (paymentFailed24h * 100 / paymentTotal24h);
        return new ItDashboardMetrics(
                Math.max(0, 100 - errorRate),
                total24h,
                failed24h,
                paymentFailed24h,
                paymentFailRate);
    }

    public AdminDashboardMetrics adminMetrics() {
        int todayRevenue = intQuery(
                "SELECT COALESCE(SUM(total_price), 0) FROM member_orders WHERE status = 'PAID' AND DATE(paid_at) = CURRENT_DATE");
        int todayAudience = intQuery(
                "SELECT COALESCE(SUM(total_qty), 0) FROM member_orders WHERE status = 'PAID' AND DATE(paid_at) = CURRENT_DATE");
        int todayCancel = intQuery(
                "SELECT COUNT(*) FROM member_orders WHERE status = 'CANCELLED' AND DATE(cancelled_at) = CURRENT_DATE");
        int roleChanges7d = intQuery(
                "SELECT COUNT(*) FROM audit_logs WHERE action = 'EMPLOYEE_ROLE_UPDATE' " +
                        "AND created_at >= DATE_SUB(NOW(), INTERVAL 7 DAY)");
        List<Map<String, Object>> roleSummary = safeList(
                "SELECT r.code AS role_code, r.name AS role_name, COUNT(e.id) AS member_count " +
                        "FROM roles r LEFT JOIN employee e ON e.role_id = r.id " +
                        "GROUP BY r.id, r.code, r.name ORDER BY r.level DESC");
        return new AdminDashboardMetrics(todayRevenue, todayAudience, todayCancel, roleChanges7d, roleSummary);
    }

    public CleanupSnapshot cleanupSnapshot() {
        int expiredPendingOrders = intQuery(
                "SELECT COUNT(*) FROM member_orders WHERE status = 'PENDING' " +
                        "AND created_at < DATE_SUB(NOW(), INTERVAL " + pendingTimeoutMinutes + " MINUTE)");
        int expiredNotifications = intQuery(
                "SELECT COUNT(*) FROM notifications WHERE created_at < DATE_SUB(NOW(), INTERVAL " +
                        notificationRetentionDays + " DAY)");
        return new CleanupSnapshot(expiredPendingOrders, expiredNotifications);
    }

    private int intQuery(String sql) {
        try {
            Integer value = jdbcTemplate.queryForObject(sql, Integer.class);
            return value == null ? 0 : value.intValue();
        } catch (DataAccessException ex) {
            return 0;
        }
    }

    private String stringQuery(String sql, String fallback) {
        try {
            String value = jdbcTemplate.queryForObject(sql, String.class);
            if (value == null || value.isBlank()) {
                return fallback;
            }
            return value;
        } catch (DataAccessException ex) {
            return fallback;
        }
    }

    private List<Map<String, Object>> safeList(String sql) {
        try {
            return jdbcTemplate.queryForList(sql);
        } catch (DataAccessException ex) {
            return List.of();
        }
    }

    public record ManagerDashboardMetrics(
            int occupancyRate,
            String peakTime,
            int openMaintenanceCount,
            int todayCancellationCount,
            int todayPaidOrders) {
    }

    public record ItDashboardMetrics(
            int apiAvailabilityPercent,
            int totalEvents24h,
            int failedEvents24h,
            int paymentFailed24h,
            int paymentFailRatePercent) {
    }

    public record AdminDashboardMetrics(
            int todayRevenue,
            int todayAudience,
            int todayCancel,
            int roleChanges7d,
            List<Map<String, Object>> roleSummary) {
    }

    public record CleanupSnapshot(
            int expiredPendingOrders,
            int expiredNotifications) {
    }
}

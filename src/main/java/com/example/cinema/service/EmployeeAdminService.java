package com.example.cinema.service;

import java.util.List;
import java.util.Map;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import com.example.cinema.dao.EmployeeAdminDao;

@Service
public class EmployeeAdminService {

    private final EmployeeAdminDao employeeAdminDao;

    public EmployeeAdminService(EmployeeAdminDao employeeAdminDao) {
        this.employeeAdminDao = employeeAdminDao;
    }

    public AuditDashboardData loadAuditDashboard(int hours) {
        int safeHours = Math.max(1, Math.min(168, hours));
        List<Map<String, Object>> recentFailures = employeeAdminDao.findRecentFailures(safeHours);
        List<Map<String, Object>> actionStats = employeeAdminDao.findActionStats(safeHours);
        int totalEvents = employeeAdminDao.countTotalEvents(safeHours);
        int failedEvents = employeeAdminDao.countFailedEvents(safeHours);
        int paymentFailures = employeeAdminDao.countPaymentFailures(safeHours);
        int cancellationCount = employeeAdminDao.countCancelledOrders(safeHours);
        return new AuditDashboardData(
                safeHours,
                recentFailures,
                actionStats,
                totalEvents,
                failedEvents,
                paymentFailures,
                cancellationCount);
    }

    public RoleManagementData loadRoleManagement() {
        return new RoleManagementData(
                employeeAdminDao.findRoles(),
                employeeAdminDao.findEmployees());
    }

    public boolean updateEmployeeRole(String nickname, String roleCode) {
        String safeNickname = nickname == null ? "" : nickname.trim();
        String safeRole = roleCode == null ? "" : roleCode.trim().toUpperCase();
        if (safeNickname.isBlank() || safeRole.isBlank()) {
            throw new IllegalArgumentException("請選擇要調整的帳號與角色。");
        }

        int updated = employeeAdminDao.updateEmployeeRole(safeNickname, safeRole);
        if (updated <= 0) {
            return false;
        }
        try {
            employeeAdminDao.insertRoleUpdateAudit(safeNickname, safeRole);
        } catch (DataAccessException ignored) {
            // Audit table may be unavailable in lightweight schemas.
        }
        return true;
    }

    public record AuditDashboardData(
            int hours,
            List<Map<String, Object>> recentFailures,
            List<Map<String, Object>> actionStats,
            int totalEvents,
            int failedEvents,
            int paymentFailures,
            int cancellationCount) {
    }

    public record RoleManagementData(
            List<Map<String, Object>> roles,
            List<Map<String, Object>> employees) {
    }
}

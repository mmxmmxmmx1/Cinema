package com.example.cinema.controller;

import java.util.List;
import java.util.Map;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/employee/admin")
public class EmployeeAdminController {

    private final JdbcTemplate jdbcTemplate;

    public EmployeeAdminController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/audit")
    public String auditDashboard(
            @RequestParam(name = "hours", defaultValue = "24") int hours,
            Model model) {
        int safeHours = Math.max(1, Math.min(168, hours));
        List<Map<String, Object>> recentFailures = List.of();
        List<Map<String, Object>> actionStats = List.of();
        int totalEvents = 0;
        int failedEvents = 0;
        int paymentFailures = 0;
        int cancellationCount = 0;

        try {
            recentFailures = jdbcTemplate.queryForList(
                    "SELECT created_at, actor_type, actor_id, action, target_type, target_id, result, detail " +
                            "FROM audit_logs " +
                            "WHERE created_at >= DATE_SUB(NOW(), INTERVAL ? HOUR) AND result <> 'SUCCESS' " +
                            "ORDER BY created_at DESC LIMIT 100",
                    safeHours);
            actionStats = jdbcTemplate.queryForList(
                    "SELECT action, result, COUNT(*) AS cnt " +
                            "FROM audit_logs " +
                            "WHERE created_at >= DATE_SUB(NOW(), INTERVAL ? HOUR) " +
                            "GROUP BY action, result " +
                            "ORDER BY cnt DESC, action ASC",
                    safeHours);
            Integer total = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM audit_logs WHERE created_at >= DATE_SUB(NOW(), INTERVAL ? HOUR)",
                    Integer.class,
                    safeHours);
            Integer failed = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM audit_logs WHERE created_at >= DATE_SUB(NOW(), INTERVAL ? HOUR) AND result <> 'SUCCESS'",
                    Integer.class,
                    safeHours);
            Integer payFailed = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM audit_logs " +
                            "WHERE created_at >= DATE_SUB(NOW(), INTERVAL ? HOUR) AND action = 'ORDER_PAY' AND result <> 'SUCCESS'",
                    Integer.class,
                    safeHours);
            Integer cancelled = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM member_orders " +
                            "WHERE cancelled_at IS NOT NULL AND cancelled_at >= DATE_SUB(NOW(), INTERVAL ? HOUR)",
                    Integer.class,
                    safeHours);

            totalEvents = total == null ? 0 : total.intValue();
            failedEvents = failed == null ? 0 : failed.intValue();
            paymentFailures = payFailed == null ? 0 : payFailed.intValue();
            cancellationCount = cancelled == null ? 0 : cancelled.intValue();
        } catch (DataAccessException ex) {
            model.addAttribute("error", "稽核資料表尚未就緒，請先完成資料庫 migration。");
        }

        model.addAttribute("title", "管理員：稽核監控");
        model.addAttribute("hours", safeHours);
        model.addAttribute("totalEvents", totalEvents);
        model.addAttribute("failedEvents", failedEvents);
        model.addAttribute("paymentFailures", paymentFailures);
        model.addAttribute("cancellationCount", cancellationCount);
        model.addAttribute("actionStats", actionStats);
        model.addAttribute("recentFailures", recentFailures);
        return "admin-audit";
    }

    @GetMapping("/roles")
    public String roleManagement(Model model) {
        List<Map<String, Object>> roles = jdbcTemplate.queryForList(
                "SELECT id, code, name, level FROM roles ORDER BY level ASC");
        List<Map<String, Object>> employees = jdbcTemplate.queryForList(
                "SELECT e.id, e.nickname, r.code AS role_code, r.level AS role_level " +
                        "FROM employee e " +
                        "JOIN roles r ON r.id = e.role_id " +
                        "ORDER BY r.level DESC, e.nickname ASC");

        model.addAttribute("title", "管理員：角色管理");
        model.addAttribute("roles", roles);
        model.addAttribute("employees", employees);
        return "admin-roles";
    }

    @PostMapping("/roles")
    public String updateEmployeeRole(
            @RequestParam("nickname") String nickname,
            @RequestParam("roleCode") String roleCode,
            RedirectAttributes redirectAttributes) {

        String safeNickname = nickname == null ? "" : nickname.trim();
        String safeRole = roleCode == null ? "" : roleCode.trim().toUpperCase();
        if (safeNickname.isBlank() || safeRole.isBlank()) {
            redirectAttributes.addFlashAttribute("error", "請選擇要調整的帳號與角色。");
            return "redirect:/employee/admin/roles";
        }

        int updated = jdbcTemplate.update(
                "UPDATE employee " +
                        "SET role_id = (SELECT id FROM roles WHERE code = ? LIMIT 1) " +
                        "WHERE nickname = ?",
                safeRole, safeNickname);

        if (updated > 0) {
            redirectAttributes.addFlashAttribute("success",
                    "已更新 " + safeNickname + " 的角色為 " + safeRole + "。");
        } else {
            redirectAttributes.addFlashAttribute("error",
                    "更新失敗：找不到員工帳號 " + safeNickname + "。");
        }

        return "redirect:/employee/admin/roles";
    }
}

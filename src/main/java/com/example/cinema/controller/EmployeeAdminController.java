package com.example.cinema.controller;

import java.util.List;
import java.util.Map;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.cinema.service.EmployeeTodoService;
import com.example.cinema.service.MemberLoyaltyService;
import com.example.cinema.service.MemberNotificationService;
import com.example.cinema.service.MemberOrderService;
import com.example.cinema.service.MovieService;
import com.example.cinema.service.EmployeeAdminService;
import com.example.cinema.service.OperationsDashboardService;

@Controller
@RequestMapping("/employee/admin")
public class EmployeeAdminController {

    private final EmployeeAdminService employeeAdminService;
    private final MemberNotificationService memberNotificationService;
    private final MemberOrderService memberOrderService;
    private final MemberLoyaltyService memberLoyaltyService;
    private final EmployeeTodoService employeeTodoService;
    private final OperationsDashboardService operationsDashboardService;
    private final MovieService movieService;

    public EmployeeAdminController(
            EmployeeAdminService employeeAdminService,
            MemberNotificationService memberNotificationService,
            MemberOrderService memberOrderService,
            MemberLoyaltyService memberLoyaltyService,
            EmployeeTodoService employeeTodoService,
            OperationsDashboardService operationsDashboardService,
            MovieService movieService) {
        this.employeeAdminService = employeeAdminService;
        this.memberNotificationService = memberNotificationService;
        this.memberOrderService = memberOrderService;
        this.memberLoyaltyService = memberLoyaltyService;
        this.employeeTodoService = employeeTodoService;
        this.operationsDashboardService = operationsDashboardService;
        this.movieService = movieService;
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
            EmployeeAdminService.AuditDashboardData data = employeeAdminService.loadAuditDashboard(safeHours);
            safeHours = data.hours();
            recentFailures = data.recentFailures();
            actionStats = data.actionStats();
            totalEvents = data.totalEvents();
            failedEvents = data.failedEvents();
            paymentFailures = data.paymentFailures();
            cancellationCount = data.cancellationCount();
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
        EmployeeAdminService.RoleManagementData data = employeeAdminService.loadRoleManagement();

        model.addAttribute("title", "管理員：角色管理");
        model.addAttribute("roles", data.roles());
        model.addAttribute("employees", data.employees());
        return "admin-roles";
    }

    @PostMapping("/roles")
    public String updateEmployeeRole(
            @RequestParam("nickname") String nickname,
            @RequestParam("roleCode") String roleCode,
            RedirectAttributes redirectAttributes) {
        String safeNickname = nickname == null ? "" : nickname.trim();
        String safeRole = roleCode == null ? "" : roleCode.trim().toUpperCase();
        try {
            boolean updated = employeeAdminService.updateEmployeeRole(safeNickname, safeRole);
            if (updated) {
                redirectAttributes.addFlashAttribute("success",
                        "已更新 " + safeNickname + " 的角色為 " + safeRole + "。");
            } else {
                redirectAttributes.addFlashAttribute("error",
                        "更新失敗：找不到員工帳號 " + safeNickname + "。");
            }
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/employee/admin/roles";
    }

    @GetMapping("/tools")
    public String tools(Model model) {
        model.addAttribute("title", "管理員：維護工具");
        model.addAttribute("snapshot", operationsDashboardService.cleanupSnapshot());
        model.addAttribute("catalogItems", movieService.listCatalogItems());
        return "admin-tools";
    }

    @PostMapping("/tools/update-poster")
    public String updateMoviePoster(
            @RequestParam("movieId") String movieId,
            @RequestParam("posterUrl") String posterUrl,
            RedirectAttributes redirectAttributes) {
        try {
            movieService.updatePosterUrl(movieId, posterUrl, "admin-tools");
            redirectAttributes.addFlashAttribute("success", "海報更新成功：" + movieId);
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", "海報更新失敗，請確認資料庫狀態後再試。");
        }
        return "redirect:/employee/admin/tools";
    }

    @PostMapping("/tools/expire-orders")
    public String expirePendingOrders(RedirectAttributes redirectAttributes) {
        memberOrderService.expireTimedOutPendingOrders();
        redirectAttributes.addFlashAttribute("success", "已執行逾時訂單檢查。");
        return "redirect:/employee/admin/tools";
    }

    @PostMapping("/tools/repair-order-status")
    public String repairOrderStatus(RedirectAttributes redirectAttributes) {
        try {
            var summary = memberOrderService.repairOrderStatuses();
            redirectAttributes.addFlashAttribute(
                    "success",
                    "訂單修復完成：PENDING→PAID " + summary.pendingToPaid()
                            + " 筆，PAID→CANCELLED " + summary.paidToCancelled()
                            + " 筆，逾時→EXPIRED " + summary.pendingToExpired()
                            + " 筆，釋放票券 " + summary.releasedTickets() + " 張。");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", "訂單修復失敗，請確認資料庫狀態後再試。");
        }
        return "redirect:/employee/admin/tools";
    }

    @PostMapping("/tools/recalculate-points")
    public String recalculatePoints(RedirectAttributes redirectAttributes) {
        try {
            int updatedMembers = memberLoyaltyService.recalculateAllMemberPointBalances();
            redirectAttributes.addFlashAttribute("success", "點數重算完成，共更新 " + updatedMembers + " 位會員。");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", "點數重算失敗，請確認資料庫狀態後再試。");
        }
        return "redirect:/employee/admin/tools";
    }

    @PostMapping("/tools/purge-notifications")
    public String purgeNotifications(RedirectAttributes redirectAttributes) {
        memberNotificationService.purgeExpiredNotifications();
        redirectAttributes.addFlashAttribute("success", "已執行通知過期清理。");
        return "redirect:/employee/admin/tools";
    }

    @PostMapping("/tools/reset-todos")
    public String resetTodos(RedirectAttributes redirectAttributes) {
        employeeTodoService.replaceTodayTodos(List.of(), "admin-tools");
        redirectAttributes.addFlashAttribute("success", "今日待辦已重置為預設內容。");
        return "redirect:/employee/admin/tools";
    }
}

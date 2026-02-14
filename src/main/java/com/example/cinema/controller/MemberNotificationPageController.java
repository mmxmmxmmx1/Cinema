package com.example.cinema.controller;

import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.cinema.config.AppClock;
import com.example.cinema.dto.MemberNotificationResponse;
import com.example.cinema.service.MemberNotificationService;

@Controller
public class MemberNotificationPageController {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(AppClock.zoneId());

    private final MemberNotificationService memberNotificationService;

    public MemberNotificationPageController(MemberNotificationService memberNotificationService) {
        this.memberNotificationService = memberNotificationService;
    }

    @GetMapping("/member/notifications")
    public String list(
            Authentication authentication,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "state", defaultValue = "all") String state,
            @RequestParam(name = "limit", defaultValue = "50") int limit,
            Model model) {
        String username = authentication == null ? null : authentication.getName();
        int safeLimit = Math.max(1, Math.min(100, limit));
        try {
            Boolean unreadOnly = toUnreadOnly(state);
            List<MemberNotificationResponse> rows = memberNotificationService.listForMember(username, safeLimit, category, unreadOnly);
            model.addAttribute("notifications", rows.stream()
                    .map(n -> new NotificationVm(
                            n.id(),
                            n.category(),
                            n.title(),
                            n.message(),
                            n.read(),
                            TS.format(n.createdAt()),
                            n.readAt() == null ? "" : TS.format(n.readAt())))
                    .toList());
            model.addAttribute("unreadCount", memberNotificationService.unreadCount(username));
            model.addAttribute("category", normalizeViewCategory(category));
            model.addAttribute("state", normalizeState(state));
            model.addAttribute("limit", safeLimit);
        } catch (Exception ex) {
            model.addAttribute("notifications", Collections.emptyList());
            model.addAttribute("unreadCount", 0);
            model.addAttribute("category", normalizeViewCategory(category));
            model.addAttribute("state", normalizeState(state));
            model.addAttribute("limit", safeLimit);
            model.addAttribute("error", "無法載入通知（資料庫可能尚未啟動）。");
        }
        return "member-notifications";
    }

    @PostMapping("/member/notifications/{notificationId}/read")
    public String markRead(Authentication authentication, @PathVariable long notificationId,
            RedirectAttributes redirectAttributes) {
        String username = authentication == null ? null : authentication.getName();
        try {
            int updated = memberNotificationService.markRead(username, notificationId);
            if (updated > 0) {
                redirectAttributes.addFlashAttribute("success", "通知已標記為已讀。");
            }
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", "標記通知失敗，請稍後再試。");
        }
        return "redirect:/member/notifications";
    }

    @PostMapping("/member/notifications/read-all")
    public String markAllRead(Authentication authentication, RedirectAttributes redirectAttributes) {
        String username = authentication == null ? null : authentication.getName();
        try {
            int updated = memberNotificationService.markAllRead(username);
            redirectAttributes.addFlashAttribute("success", "已全部標記為已讀（" + updated + " 筆）。");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", "批次標記失敗，請稍後再試。");
        }
        return "redirect:/member/notifications";
    }

    private static Boolean toUnreadOnly(String state) {
        String safe = normalizeState(state);
        if ("unread".equals(safe)) {
            return Boolean.TRUE;
        }
        if ("read".equals(safe)) {
            return Boolean.FALSE;
        }
        return null;
    }

    private static String normalizeState(String state) {
        if (state == null || state.isBlank()) {
            return "all";
        }
        String safe = state.trim().toLowerCase();
        if ("unread".equals(safe) || "read".equals(safe) || "all".equals(safe)) {
            return safe;
        }
        return "all";
    }

    private static String normalizeViewCategory(String category) {
        if (category == null || category.isBlank()) {
            return "ALL";
        }
        String safe = category.trim().toUpperCase();
        if (!safe.matches("[A-Z_]+")) {
            return "ALL";
        }
        return safe;
    }

    public record NotificationVm(
            long id,
            String category,
            String title,
            String message,
            boolean read,
            String createdAt,
            String readAt) {
    }
}

package com.example.cinema.controller;

import com.example.cinema.service.MemberLoyaltyService;
import com.example.cinema.service.MemberNotificationService;
import com.example.cinema.service.MemberOrderService;
import com.example.cinema.service.SessionService.Realm;

import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class MemberHomeController {

    private final ObjectProvider<MemberLoyaltyService> memberLoyaltyServiceProvider;
    private final ObjectProvider<MemberOrderService> memberOrderServiceProvider;
    private final ObjectProvider<MemberNotificationService> memberNotificationServiceProvider;
    private final PageSessionSupport pageSessionSupport;

    public MemberHomeController(
            ObjectProvider<MemberLoyaltyService> memberLoyaltyServiceProvider,
            ObjectProvider<MemberOrderService> memberOrderServiceProvider,
            ObjectProvider<MemberNotificationService> memberNotificationServiceProvider,
            PageSessionSupport pageSessionSupport) {
        this.memberLoyaltyServiceProvider = memberLoyaltyServiceProvider;
        this.memberOrderServiceProvider = memberOrderServiceProvider;
        this.memberNotificationServiceProvider = memberNotificationServiceProvider;
        this.pageSessionSupport = pageSessionSupport;
    }

    @GetMapping("/member")
    public String memberPage(Model model, HttpSession session, RedirectAttributes redirectAttributes,
            Authentication authentication) {
        if (!pageSessionSupport.hasActiveSession(session, Realm.MEMBER)) {
            redirectAttributes.addFlashAttribute("error", "請先登入會員帳號。");
            return "redirect:/member/login";
        }

        pageSessionSupport.updateLastActivity(session, Realm.MEMBER);
        populateMemberModel(model);

        String username = authentication == null ? null : authentication.getName();
        if (username != null && !username.isBlank()) {
            try {
                MemberLoyaltyService loyaltyService = memberLoyaltyServiceProvider.getIfAvailable();
                if (loyaltyService != null) {
                    model.addAttribute("memberPoints", loyaltyService.currentPoints(username));
                }
            } catch (Exception ex) {
                model.addAttribute("memberPoints", 0);
            }

            try {
                MemberOrderService memberOrderService = memberOrderServiceProvider.getIfAvailable();
                if (memberOrderService != null) {
                    model.addAttribute("activeOrders", memberOrderService.listActiveOrders(username, 5));
                    model.addAttribute("historyOrders", memberOrderService.listHistoryOrders(username, 5));
                    model.addAttribute("upcomingBookings", memberOrderService.listUpcomingBookings(username, 1));
                }
            } catch (Exception ex) {
                model.addAttribute("orderLoadError", "訂單暫時無法顯示，請稍後再試。");
            }
        }

        try {
            if (username != null && !username.isBlank()) {
                MemberNotificationService notificationService = memberNotificationServiceProvider.getIfAvailable();
                if (notificationService != null) {
                    model.addAttribute("recentNotifications", notificationService.listForMember(username, 5));
                    model.addAttribute("unreadNotificationCount", notificationService.unreadCount(username));
                }
            }
        } catch (Exception ex) {
            model.addAttribute("notificationLoadError", "無法載入通知（資料庫可能尚未啟動）。");
        }
        return "member";
    }

    @PostMapping("/member/activity")
    public ResponseEntity<Void> memberActivity(HttpSession session) {
        if (!pageSessionSupport.hasActiveSession(session, Realm.MEMBER)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        pageSessionSupport.updateLastActivity(session, Realm.MEMBER);
        return ResponseEntity.noContent().build();
    }

    private void populateMemberModel(Model model) {
        model.addAttribute("title", "很好睡電影院會員專區");
        model.addAttribute("message", "歡迎回來！立即查看個人優惠與專屬影城體驗。");
    }
}

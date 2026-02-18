package com.example.cinema.controller;

import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.cinema.config.AppClock;
import com.example.cinema.dto.MemberPointLog;
import com.example.cinema.exception.TicketPurchaseRuleViolationException;
import com.example.cinema.service.MemberLoyaltyService;

@Controller
public class MemberPointsPageController {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(AppClock.zoneId());

    private final MemberLoyaltyService memberLoyaltyService;

    public MemberPointsPageController(MemberLoyaltyService memberLoyaltyService) {
        this.memberLoyaltyService = memberLoyaltyService;
    }

    @GetMapping("/member/points")
    public String points(Authentication authentication, Model model) {
        String username = authentication == null ? null : authentication.getName();
        model.addAttribute("pointLogRetentionDays", memberLoyaltyService.pointLogRetentionDays());
        try {
            int points = memberLoyaltyService.currentPoints(username);
            List<MemberPointLog> logs = memberLoyaltyService.recentPointLogs(username, 20);
            model.addAttribute("rewardOptions", memberLoyaltyService.listRewardOptions());
            model.addAttribute("points", points);
            model.addAttribute("logs", logs.stream().map(log -> new MemberPointLogVm(
                    log.orderId(),
                    log.amount(),
                    log.points(),
                    log.paidAt() == null ? "" : TS.format(log.paidAt()),
                    log.description())).toList());
        } catch (Exception ex) {
            model.addAttribute("points", 0);
            model.addAttribute("logs", Collections.emptyList());
            model.addAttribute("error", "無法載入點數資訊（資料庫可能尚未啟動）。");
        }
        return "member-points";
    }

    @PostMapping("/member/points/redeem")
    public String redeem(
            Authentication authentication,
            @RequestParam("rewardCode") String rewardCode,
            RedirectAttributes redirectAttributes) {
        String username = authentication == null ? null : authentication.getName();
        try {
            int remaining = memberLoyaltyService.redeem(username, rewardCode);
            redirectAttributes.addFlashAttribute("success", "兌換成功，剩餘點數：" + remaining + " 點。");
        } catch (TicketPurchaseRuleViolationException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", "兌換失敗（資料庫可能尚未啟動）。");
        }
        return "redirect:/member/points";
    }

    public record MemberPointLogVm(
            long orderId,
            int amount,
            int points,
            String paidAt,
            String description) {
    }
}

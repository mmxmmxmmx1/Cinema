package com.example.cinema.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.cinema.dto.MemberNotificationResponse;
import com.example.cinema.service.ApiRateLimitService;
import com.example.cinema.service.MemberNotificationService;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;

@RestController
@RequestMapping("/member/api/notifications")
@Validated
public class MemberNotificationController {

    private final MemberNotificationService memberNotificationService;
    private final ApiRateLimitService apiRateLimitService;
    private final int readLimitPerMinute;

    public MemberNotificationController(
            MemberNotificationService memberNotificationService,
            ApiRateLimitService apiRateLimitService,
            @Value("${app.rate-limit.notification-read-per-minute:60}") int readLimitPerMinute) {
        this.memberNotificationService = memberNotificationService;
        this.apiRateLimitService = apiRateLimitService;
        this.readLimitPerMinute = Math.max(1, readLimitPerMinute);
    }

    @GetMapping
    public ResponseEntity<List<MemberNotificationResponse>> list(Authentication authentication,
            @RequestParam(name = "limit", defaultValue = "20") @Min(1) @Max(100) int limit) {
        String username = authentication == null ? null : authentication.getName();
        return ResponseEntity.ok(memberNotificationService.listForMember(username, limit));
    }

    @PostMapping("/{notificationId}/read")
    public ResponseEntity<Void> markRead(Authentication authentication, HttpServletRequest request,
            @PathVariable long notificationId) {
        String username = authentication == null ? null : authentication.getName();
        apiRateLimitService.check(
                "member-notification-read",
                requestKey(username, request),
                readLimitPerMinute,
                Duration.ofMinutes(1));
        memberNotificationService.markRead(username, notificationId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllRead(Authentication authentication, HttpServletRequest request) {
        String username = authentication == null ? null : authentication.getName();
        apiRateLimitService.check(
                "member-notification-read-all",
                requestKey(username, request),
                readLimitPerMinute,
                Duration.ofMinutes(1));
        memberNotificationService.markAllRead(username);
        return ResponseEntity.noContent().build();
    }

    private String requestKey(String username, HttpServletRequest request) {
        String userKey = (username == null || username.isBlank()) ? "anonymous" : username.trim();
        String ip = request == null ? null : request.getRemoteAddr();
        String ipKey = (ip == null || ip.isBlank()) ? "unknown-ip" : ip.trim();
        return userKey + "|" + ipKey;
    }
}

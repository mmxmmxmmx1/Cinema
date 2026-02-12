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
import com.example.cinema.service.MemberNotificationService;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/member/api/notifications")
@Validated
public class MemberNotificationController {

    private final MemberNotificationService memberNotificationService;

    public MemberNotificationController(MemberNotificationService memberNotificationService) {
        this.memberNotificationService = memberNotificationService;
    }

    @GetMapping
    public ResponseEntity<List<MemberNotificationResponse>> list(Authentication authentication,
            @RequestParam(name = "limit", defaultValue = "20") @Min(1) @Max(100) int limit) {
        String username = authentication == null ? null : authentication.getName();
        return ResponseEntity.ok(memberNotificationService.listForMember(username, limit));
    }

    @PostMapping("/{notificationId}/read")
    public ResponseEntity<Void> markRead(Authentication authentication, @PathVariable long notificationId) {
        String username = authentication == null ? null : authentication.getName();
        memberNotificationService.markRead(username, notificationId);
        return ResponseEntity.noContent().build();
    }
}


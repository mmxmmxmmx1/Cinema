package com.example.cinema.dto;

import java.time.Instant;

public record MemberNotificationResponse(
        long id,
        String category,
        String title,
        String message,
        boolean read,
        Instant createdAt,
        Instant readAt) {
}


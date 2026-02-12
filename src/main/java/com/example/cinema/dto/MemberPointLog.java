package com.example.cinema.dto;

import java.time.Instant;

public record MemberPointLog(
        long orderId,
        int amount,
        int points,
        Instant paidAt,
        String description) {
}

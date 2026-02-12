package com.example.cinema.dto;

import java.time.Instant;

public record OrderSummaryResponse(
        long orderId,
        String movieId,
        String showtimeId,
        String auditorium,
        int totalQty,
        int totalPrice,
        String status,
        Instant createdAt,
        Instant paidAt,
        Instant showStartAt) {
}

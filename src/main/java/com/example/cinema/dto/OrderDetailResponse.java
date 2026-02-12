package com.example.cinema.dto;

import java.time.Instant;
import java.util.List;

public record OrderDetailResponse(
        long orderId,
        String movieId,
        String showtimeId,
        String auditorium,
        int totalQty,
        int unitPrice,
        int totalPrice,
        String status,
        Instant createdAt,
        Instant paidAt,
        Instant showStartAt,
        Instant cancelledAt,
        Instant failedAt,
        Instant expiredAt,
        String failureReason,
        int paymentAttempts,
        List<String> seatIds) {
}

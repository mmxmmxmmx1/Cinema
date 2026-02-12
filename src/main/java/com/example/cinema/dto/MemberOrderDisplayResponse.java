package com.example.cinema.dto;

import java.time.Instant;

public record MemberOrderDisplayResponse(
        long orderId,
        String movieId,
        String movieTitle,
        String auditorium,
        int totalQty,
        int totalPrice,
        String status,
        Instant showStartAt) {
}


package com.example.cinema.dto;

import java.time.Instant;
import java.util.List;

public record TicketPurchaseResponse(
        String movieId,
        String showtimeId,
        String auditorium,
        List<String> seatIds,
        Instant purchasedAt) {
}


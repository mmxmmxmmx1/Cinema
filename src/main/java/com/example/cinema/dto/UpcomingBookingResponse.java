package com.example.cinema.dto;

import java.time.Instant;

public record UpcomingBookingResponse(
        long orderId,
        String movieId,
        String movieTitle,
        String showtimeId,
        String auditorium,
        int ticketCount,
        Instant showStartAt,
        String showStartLabel) {
}

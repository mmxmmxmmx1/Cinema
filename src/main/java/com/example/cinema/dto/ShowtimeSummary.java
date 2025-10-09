package com.example.cinema.dto;

public record ShowtimeSummary(
        String movieTitle,
        String showtime,
        String hall,
        int soldSeats,
        int totalSeats,
        int percentage
) {
}

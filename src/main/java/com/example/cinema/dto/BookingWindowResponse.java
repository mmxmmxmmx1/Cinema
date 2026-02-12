package com.example.cinema.dto;

public record BookingWindowResponse(
        boolean bookingOpen,
        boolean warning,
        String now,
        String openTime,
        String closeTime,
        String message) {
}

package com.example.cinema.service;

public record NotificationCommand(
        long memberId,
        String category,
        String title,
        String message) {
}


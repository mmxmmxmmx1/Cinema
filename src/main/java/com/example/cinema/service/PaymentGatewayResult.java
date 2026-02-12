package com.example.cinema.service;

public record PaymentGatewayResult(
        PaymentOutcome outcome,
        String reference,
        String message) {
}


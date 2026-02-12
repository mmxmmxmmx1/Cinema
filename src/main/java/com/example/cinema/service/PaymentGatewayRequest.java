package com.example.cinema.service;

public record PaymentGatewayRequest(
        long orderId,
        long memberId,
        int amount,
        PaymentMode mode) {
}


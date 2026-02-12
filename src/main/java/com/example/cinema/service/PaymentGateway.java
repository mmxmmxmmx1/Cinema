package com.example.cinema.service;

public interface PaymentGateway {
    PaymentGatewayResult charge(PaymentGatewayRequest request);
}


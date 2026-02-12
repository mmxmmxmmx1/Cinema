package com.example.cinema.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.example.cinema.config.AppClock;

@Component
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "mock", matchIfMissing = true)
public class MockPaymentGateway implements PaymentGateway {

    @Override
    public PaymentGatewayResult charge(PaymentGatewayRequest request) {
        PaymentMode mode = request == null || request.mode() == null ? PaymentMode.SUCCESS : request.mode();
        String ref = "MOCK-" + request.orderId() + "-" + AppClock.nowInstant().toEpochMilli();
        return switch (mode) {
            case SUCCESS -> new PaymentGatewayResult(PaymentOutcome.SUCCESS, ref, "模擬付款成功");
            case FAILED -> new PaymentGatewayResult(PaymentOutcome.FAILED, ref, "模擬付款失敗");
            case TIMEOUT -> new PaymentGatewayResult(PaymentOutcome.TIMEOUT, ref, "模擬付款逾時");
        };
    }
}

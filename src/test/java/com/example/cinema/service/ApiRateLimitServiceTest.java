package com.example.cinema.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.cinema.exception.RateLimitExceededException;

@DisplayName("API 限流服務測試")
class ApiRateLimitServiceTest {

    @Test
    @DisplayName("在視窗內超過上限時應拒絕請求")
    void shouldRejectWhenExceededLimit() {
        ApiRateLimitService service = new ApiRateLimitService();
        Duration window = Duration.ofMinutes(1);

        assertDoesNotThrow(() -> service.check("order-create", "u1|127.0.0.1", 2, window));
        assertDoesNotThrow(() -> service.check("order-create", "u1|127.0.0.1", 2, window));
        assertThrows(RateLimitExceededException.class,
                () -> service.check("order-create", "u1|127.0.0.1", 2, window));
    }
}

package com.example.cinema.service;

import java.util.Locale;

public enum OrderStatus {
    PENDING,
    PAID,
    FAILED,
    EXPIRED,
    CANCELLED;

    public static OrderStatus fromDb(String raw) {
        if (raw == null || raw.isBlank()) {
            return PENDING;
        }
        try {
            return OrderStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return PENDING;
        }
    }

    public boolean canTransitionTo(OrderStatus next) {
        if (next == null || next == this) {
            return true;
        }
        return switch (this) {
            case PENDING -> next == PAID || next == FAILED || next == EXPIRED || next == CANCELLED;
            case FAILED -> next == PAID || next == CANCELLED || next == EXPIRED;
            case PAID -> next == CANCELLED;
            case EXPIRED, CANCELLED -> false;
        };
    }
}

package com.example.cinema.service;

import java.util.Locale;

public enum PaymentMode {
    SUCCESS,
    FAILED,
    TIMEOUT;

    public static PaymentMode fromNullable(String raw) {
        if (raw == null || raw.isBlank()) {
            return SUCCESS;
        }
        try {
            return PaymentMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return SUCCESS;
        }
    }
}


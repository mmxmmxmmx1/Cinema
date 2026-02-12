package com.example.cinema.exception;

public class TicketPurchaseConflictException extends RuntimeException {
    public TicketPurchaseConflictException(String message) {
        super(message);
    }

    public TicketPurchaseConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}


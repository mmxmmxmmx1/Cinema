package com.example.cinema.exception;

public class TicketPurchaseRuleViolationException extends RuntimeException {
    public TicketPurchaseRuleViolationException(String message) {
        super(message);
    }
}


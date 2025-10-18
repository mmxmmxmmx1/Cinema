package com.example.cinema.exception;

/**
 * 用戶註冊過程中發生的異常
 */
public class UserRegistrationException extends RuntimeException {

    public UserRegistrationException(String message) {
        super(message);
    }

    public UserRegistrationException(String message, Throwable cause) {
        super(message, cause);
    }
}

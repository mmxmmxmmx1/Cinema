package com.example.cinema.config;

public final class SessionConstants {
    private SessionConstants() {}

    public static final String MEMBER_SESSION_KEY = "memberAuthenticated";
    public static final String EMPLOYEE_SESSION_KEY = "employeeAuthenticated";
    public static final String MEMBER_ACCESS_TOKEN = "memberAccessToken";
    public static final String EMPLOYEE_ACCESS_TOKEN = "employeeAccessToken";
    public static final String MEMBER_ATTEMPT_COUNT = "memberLoginAttempts";
    public static final String MEMBER_LOCK_UNTIL = "memberLoginLockUntil";
    public static final String EMPLOYEE_ATTEMPT_COUNT = "employeeLoginAttempts";
    public static final String EMPLOYEE_LOCK_UNTIL = "employeeLoginLockUntil";
    public static final String MEMBER_LAST_ACTIVITY = "memberLastActivity";
    public static final String EMPLOYEE_LAST_ACTIVITY = "employeeLastActivity";
    public static final String GUEST_WATCHLIST = "GUEST_WATCHLIST";
}

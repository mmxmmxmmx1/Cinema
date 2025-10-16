package com.example.cinema.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class LoginAttemptService {

    public static final int MAX_ATTEMPTS = 5;
    public static final Duration LOCK_DURATION = Duration.ofMinutes(10);

    private final Map<String, AttemptState> attempts = new ConcurrentHashMap<>();

    public LoginAttemptStatus getStatus(SessionService.Realm realm, String username) {
        String key = key(realm, username);
        AttemptState state = attempts.get(key);
        if (state == null) {
            return new LoginAttemptStatus(false, MAX_ATTEMPTS, Duration.ZERO);
        }
        if (state.lockedUntil != null) {
            if (state.lockedUntil.isAfter(Instant.now())) {
                return new LoginAttemptStatus(true, 0, Duration.between(Instant.now(), state.lockedUntil));
            }
            attempts.remove(key);
            return new LoginAttemptStatus(false, MAX_ATTEMPTS, Duration.ZERO);
        }
        int remaining = Math.max(0, MAX_ATTEMPTS - state.failedAttempts);
        return new LoginAttemptStatus(false, remaining, Duration.ZERO);
    }

    public LoginAttemptStatus registerFailure(SessionService.Realm realm, String username) {
        String key = key(realm, username);

        System.out.println("\n=== registerFailure START ===");
        System.out.println("Realm: " + realm);
        System.out.println("Username: " + username);
        System.out.println("Key: " + key);

        AttemptState state = attempts.get(key);
        System.out.println("State exists before computeIfAbsent: " + (state != null));
        if (state != null) {
            System.out.println("failedAttempts BEFORE increment: " + state.failedAttempts);
        }

        state = attempts.computeIfAbsent(key, k -> {
            System.out.println("Creating new AttemptState for key: " + k);
            return new AttemptState();
        });

        // 检查帐户是否被锁定且锁定未过期
        if (state.lockedUntil != null && state.lockedUntil.isAfter(Instant.now())) {
            System.out.println("Account is LOCKED. Returning locked status.");
            LoginAttemptStatus status = getStatus(key);
            System.out.println("=== registerFailure END (LOCKED) ===\n");
            return status;
        }

        // 如果锁定已过期，重置计数器和锁定时间
        if (state.lockedUntil != null && !state.lockedUntil.isAfter(Instant.now())) {
            System.out.println("Lock expired. Resetting failedAttempts and lockedUntil.");
            state.failedAttempts = 0;
            state.lockedUntil = null;
        }

        // 递增失败次数
        state.failedAttempts++;
        System.out.println("failedAttempts AFTER increment: " + state.failedAttempts);
        System.out.println("MAX_ATTEMPTS: " + MAX_ATTEMPTS);

        if (state.failedAttempts >= MAX_ATTEMPTS) {
            state.lockedUntil = Instant.now().plus(LOCK_DURATION);
            System.out.println("HIT MAX ATTEMPTS! Account is now LOCKED.");
        }

        System.out.println("All attempts in map: " + attempts.toString());

        LoginAttemptStatus status = getStatus(key);
        System.out.println("Returned status - locked: " + status.locked() +
                ", remainingAttempts: " + status.remainingAttempts());
        System.out.println("=== registerFailure END ===\n");

        return status;
    }

    public void registerSuccess(SessionService.Realm realm, String username) {
        System.out.println("\n=== registerSuccess ===");
        System.out.println("Removing key for: " + realm + " / " + username);
        attempts.remove(key(realm, username));
        System.out.println("=== registerSuccess END ===\n");
    }

    private static final class AttemptState {
        int failedAttempts;
        Instant lockedUntil;
    }

    public record LoginAttemptStatus(boolean locked, int remainingAttempts, Duration lockDuration) {
    }

    private String key(SessionService.Realm realm, String username) {
        String normalized = (username == null || username.isBlank())
                ? "_anonymous"
                : username.trim().toLowerCase();
        return realm.name().toLowerCase() + ':' + normalized;
    }

    private LoginAttemptStatus getStatus(String key) {
        AttemptState state = attempts.get(key);
        if (state == null) {
            return new LoginAttemptStatus(false, MAX_ATTEMPTS, Duration.ZERO);
        }
        if (state.lockedUntil != null) {
            if (state.lockedUntil.isAfter(Instant.now())) {
                return new LoginAttemptStatus(true, 0, Duration.between(Instant.now(), state.lockedUntil));
            }
            int remaining = Math.max(0, MAX_ATTEMPTS - state.failedAttempts);
            return new LoginAttemptStatus(false, remaining, Duration.ZERO);
        }
        int remaining = Math.max(0, MAX_ATTEMPTS - state.failedAttempts);
        return new LoginAttemptStatus(false, remaining, Duration.ZERO);
    }
}
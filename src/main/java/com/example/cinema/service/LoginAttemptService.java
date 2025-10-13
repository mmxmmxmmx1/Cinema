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
        AttemptState state = attempts.computeIfAbsent(key, k -> new AttemptState());
        if (state.lockedUntil != null && state.lockedUntil.isAfter(Instant.now())) {
            return getStatus(key);
        }
        state.failedAttempts++;
        if (state.failedAttempts >= MAX_ATTEMPTS) {
            state.lockedUntil = Instant.now().plus(LOCK_DURATION);
        }
        return getStatus(key);
    }

    public void registerSuccess(SessionService.Realm realm, String username) {
        attempts.remove(key(realm, username));
    }

    private static final class AttemptState {
        int failedAttempts;
        Instant lockedUntil;
    }

    public record LoginAttemptStatus(boolean locked, int remainingAttempts, Duration lockDuration) { }

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
            attempts.remove(key);
            return new LoginAttemptStatus(false, MAX_ATTEMPTS, Duration.ZERO);
        }
        int remaining = Math.max(0, MAX_ATTEMPTS - state.failedAttempts);
        return new LoginAttemptStatus(false, remaining, Duration.ZERO);
    }
}

package com.example.cinema.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LoginAttemptService {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(10);

    private final Map<String, AttemptState> attempts = new ConcurrentHashMap<>();

    public LoginAttemptStatus getStatus(String key) {
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

    public LoginAttemptStatus registerFailure(String key) {
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

    public void registerSuccess(String key) {
        attempts.remove(key);
    }

    private static final class AttemptState {
        int failedAttempts;
        Instant lockedUntil;
    }

    public record LoginAttemptStatus(boolean locked, int remainingAttempts, Duration lockDuration) { }
}

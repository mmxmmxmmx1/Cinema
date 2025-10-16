package com.example.cinema.service;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpSession;

import org.springframework.stereotype.Service;

@Service
public class SessionService {

    public enum Realm {
        MEMBER,
        EMPLOYEE
    }

    private static final String GUEST_WATCHLIST_KEY = "guestWatchlist";

    private static final class Keys {
        private final String authKey;
        private final String accessTokenKey;
        private final String attemptCountKey;
        private final String lockUntilKey;
        private final String lastActivityKey;
        private final String errorMessageKey;

        private Keys(String prefix) {
            this.authKey = prefix + "Authenticated";
            this.accessTokenKey = prefix + "AccessToken";
            this.attemptCountKey = prefix + "LoginAttempts";
            this.lockUntilKey = prefix + "LoginLockUntil";
            this.lastActivityKey = prefix + "LastActivity";
            this.errorMessageKey = prefix + "LoginErrorText";
        }
    }

    private static final Map<Realm, Keys> REALM_KEYS = new EnumMap<>(Realm.class);

    static {
        REALM_KEYS.put(Realm.MEMBER, new Keys("member"));
        REALM_KEYS.put(Realm.EMPLOYEE, new Keys("employee"));
    }

    public boolean isAuthenticated(HttpSession session, Realm realm) {
        return Boolean.TRUE.equals(session.getAttribute(keys(realm).authKey));
    }

    public void establishSession(HttpSession session, Realm realm) {
        Keys keys = keys(realm);
        session.setAttribute(keys.authKey, true);
        session.setAttribute(keys.accessTokenKey, UUID.randomUUID().toString());
        updateLastActivity(session, realm);
    }

    public void clearAuthentication(HttpSession session, Realm realm) {
        Keys keys = keys(realm);
        session.removeAttribute(keys.authKey);
        session.removeAttribute(keys.accessTokenKey);
        session.removeAttribute(keys.lastActivityKey);
    }

    public void updateLastActivity(HttpSession session, Realm realm) {
        session.setAttribute(keys(realm).lastActivityKey, Instant.now());
    }

    public boolean isSessionExpired(HttpSession session, Realm realm, Duration timeout) {
        Keys keys = keys(realm);
        Instant lastActivity = (Instant) session.getAttribute(keys.lastActivityKey);
        if (lastActivity == null || timeout == null || timeout.isZero() || timeout.isNegative()) {
            return false;
        }
        return lastActivity.plus(timeout).isBefore(Instant.now());
    }

    public void resetAttempts(HttpSession session, Realm realm) {
        Keys keys = keys(realm);
        session.removeAttribute(keys.attemptCountKey);
        session.removeAttribute(keys.lockUntilKey);
        session.removeAttribute(keys.errorMessageKey);
    }

    public void applyFailureFeedback(HttpSession session, Realm realm, LoginAttemptService.LoginAttemptStatus status) {
        Keys keys = keys(realm);

        if (status.locked()) {
            session.removeAttribute(keys.attemptCountKey);
            Instant lockUntil = Instant.now().plus(status.lockDuration());
            session.setAttribute(keys.lockUntilKey, lockUntil);
            session.setAttribute(keys.errorMessageKey, formatLockMessage(status.lockDuration()));
        } else {
            int attemptsUsed = LoginAttemptService.MAX_ATTEMPTS - status.remainingAttempts();
            session.setAttribute(keys.attemptCountKey, attemptsUsed);
            session.removeAttribute(keys.lockUntilKey);

            if (status.remainingAttempts() > 0) {
                session.setAttribute(keys.errorMessageKey, formatRemainingAttempts(status.remainingAttempts()));
            } else {
                session.setAttribute(keys.errorMessageKey, "帳號或密碼不正確");
            }
        }
    }

    public Duration remainingLockDuration(HttpSession session, Realm realm) {
        Instant lockUntil = (Instant) session.getAttribute(keys(realm).lockUntilKey);
        if (lockUntil == null || lockUntil.isBefore(Instant.now())) {
            session.removeAttribute(keys(realm).lockUntilKey);
            session.removeAttribute(keys(realm).attemptCountKey);
            return Duration.ZERO;
        }
        return Duration.between(Instant.now(), lockUntil);
    }

    public String consumeErrorMessage(HttpSession session, Realm realm) {
        Keys keys = keys(realm);
        String message = (String) session.getAttribute(keys.errorMessageKey);
        session.removeAttribute(keys.errorMessageKey);
        return message;
    }

    public void storeErrorMessage(HttpSession session, Realm realm, String message) {
        session.setAttribute(keys(realm).errorMessageKey, message);
    }

    public String guestWatchlistKey() {
        return GUEST_WATCHLIST_KEY;
    }

    private Keys keys(Realm realm) {
        return REALM_KEYS.get(realm);
    }

    private String formatLockMessage(Duration duration) {
        long minutes = Math.max(1, (duration.getSeconds() + 59) / 60);
        return "帳號已鎖定,請於 " + minutes + " 分鐘後再試。";
    }

    private String formatRemainingAttempts(int remaining) {
        return "帳號或密碼不正確,還可以再嘗試 " + remaining + " 次。";
    }
}

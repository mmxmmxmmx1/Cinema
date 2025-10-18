package com.example.cinema.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 登入嘗試服務，追蹤失敗的登入嘗試並實施帳號鎖定機制
 */
@Service
public class LoginAttemptService {
    private static final Logger logger = LoggerFactory.getLogger(LoginAttemptService.class);

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

    /**
     * 記錄登入失敗嘗試
     * 
     * @param realm 登入領域 (會員或員工)
     * @param username 用戶名
     * @return 登入嘗試狀態
     */
    public LoginAttemptStatus registerFailure(SessionService.Realm realm, String username) {
        String key = key(realm, username);
        logger.debug("記錄登入失敗 - 領域: {}, 用戶: {}", realm, username);

        AttemptState state = attempts.computeIfAbsent(key, k -> {
            logger.debug("為用戶 {} 創建新的嘗試狀態", username);
            return new AttemptState();
        });

        // 檢查帳戶是否被鎖定且鎖定未過期
        if (state.lockedUntil != null && state.lockedUntil.isAfter(Instant.now())) {
            logger.warn("帳戶已鎖定 - 領域: {}, 用戶: {}", realm, username);
            return getStatus(key);
        }

        // 如果鎖定已過期，重置計數器和鎖定時間
        if (state.lockedUntil != null && !state.lockedUntil.isAfter(Instant.now())) {
            logger.info("帳戶鎖定已過期，重置計數器 - 領域: {}, 用戶: {}", realm, username);
            state.failedAttempts = 0;
            state.lockedUntil = null;
        }

        // 遞增失敗次數
        state.failedAttempts++;
        logger.debug("登入失敗次數: {}/{} - 領域: {}, 用戶: {}", state.failedAttempts, MAX_ATTEMPTS, realm, username);

        if (state.failedAttempts >= MAX_ATTEMPTS) {
            state.lockedUntil = Instant.now().plus(LOCK_DURATION);
            logger.warn("達到最大失敗次數，帳戶已鎖定 {} 分鐘 - 領域: {}, 用戶: {}", LOCK_DURATION.toMinutes(), realm, username);
        }

        return getStatus(key);
    }

    /**
     * 記錄登入成功，清除失敗記錄
     * 
     * @param realm 登入領域
     * @param username 用戶名
     */
    public void registerSuccess(SessionService.Realm realm, String username) {
        String key = key(realm, username);
        AttemptState removed = attempts.remove(key);
        if (removed != null) {
            logger.info("登入成功，清除失敗記錄 - 領域: {}, 用戶: {}", realm, username);
        }
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
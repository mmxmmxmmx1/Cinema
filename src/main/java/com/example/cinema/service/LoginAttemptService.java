package com.example.cinema.service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.example.cinema.config.AppClock;

/**
 * 登入嘗試服務，追蹤失敗的登入嘗試並實施帳號鎖定機制
 */
@Service
public class LoginAttemptService {
    private static final Logger logger = LoggerFactory.getLogger(LoginAttemptService.class);

    public static final int MAX_ATTEMPTS = 5;
    public static final Duration LOCK_DURATION = Duration.ofMinutes(10);
    private static final Duration ENTRY_TTL = Duration.ofHours(1);

    private final Map<String, AttemptState> attempts = new ConcurrentHashMap<>();
    private volatile JdbcTemplate jdbcTemplate;
    private volatile Boolean dbTableAvailable;

    @Autowired(required = false)
    void setJdbcTemplate(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public LoginAttemptStatus getStatus(SessionService.Realm realm, String username) {
        NormalizedTarget target = normalizeTarget(realm, username);
        if (isDbStoreAvailable()) {
            try {
                return getStatusFromDb(target);
            } catch (DataAccessException ex) {
                markDbUnavailable(ex);
            }
        }
        return getStatusInMemory(target.key());
    }

    /**
     * 記錄登入失敗嘗試
     * 
     * @param realm 登入領域 (會員或員工)
     * @param username 用戶名
     * @return 登入嘗試狀態
     */
    public LoginAttemptStatus registerFailure(SessionService.Realm realm, String username) {
        NormalizedTarget target = normalizeTarget(realm, username);
        if (isDbStoreAvailable()) {
            try {
                return registerFailureInDb(realm, username, target);
            } catch (DataAccessException ex) {
                markDbUnavailable(ex);
            }
        }

        String key = target.key();
        logger.debug("記錄登入失敗 - 領域: {}, 用戶: {}", realm, username);

        AttemptState state = attempts.computeIfAbsent(key, k -> {
            logger.debug("為用戶 {} 創建新的嘗試狀態", username);
            return new AttemptState();
        });
        state.lastSeen = AppClock.nowInstant();

        // 檢查帳戶是否被鎖定且鎖定未過期
        if (state.lockedUntil != null && state.lockedUntil.isAfter(AppClock.nowInstant())) {
            logger.warn("帳戶已鎖定 - 領域: {}, 用戶: {}", realm, username);
            return getStatusInMemory(key);
        }

        // 如果鎖定已過期，重置計數器和鎖定時間
        if (state.lockedUntil != null && !state.lockedUntil.isAfter(AppClock.nowInstant())) {
            logger.info("帳戶鎖定已過期，重置計數器 - 領域: {}, 用戶: {}", realm, username);
            state.failedAttempts = 0;
            state.lockedUntil = null;
        }

        // 遞增失敗次數
        state.failedAttempts++;
        logger.debug("登入失敗次數: {}/{} - 領域: {}, 用戶: {}", state.failedAttempts, MAX_ATTEMPTS, realm, username);

        if (state.failedAttempts >= MAX_ATTEMPTS) {
            state.lockedUntil = AppClock.nowInstant().plus(LOCK_DURATION);
            logger.warn("達到最大失敗次數，帳戶已鎖定 {} 分鐘 - 領域: {}, 用戶: {}", LOCK_DURATION.toMinutes(), realm, username);
        }

        return getStatusInMemory(key);
    }

    /**
     * 記錄登入成功，清除失敗記錄
     * 
     * @param realm 登入領域
     * @param username 用戶名
     */
    public void registerSuccess(SessionService.Realm realm, String username) {
        NormalizedTarget target = normalizeTarget(realm, username);
        if (isDbStoreAvailable()) {
            try {
                deleteStateFromDb(target);
                return;
            } catch (DataAccessException ex) {
                markDbUnavailable(ex);
            }
        }

        String key = target.key();
        AttemptState removed = attempts.remove(key);
        if (removed != null) {
            logger.info("登入成功，清除失敗記錄 - 領域: {}, 用戶: {}", realm, username);
        }
    }

    private static final class AttemptState {
        int failedAttempts;
        Instant lockedUntil;
        Instant lastSeen = AppClock.nowInstant();
    }

    public record LoginAttemptStatus(boolean locked, int remainingAttempts, Duration lockDuration) {
    }

    private LoginAttemptStatus getStatusInMemory(String key) {
        AttemptState state = attempts.get(key);
        if (state == null) {
            return new LoginAttemptStatus(false, MAX_ATTEMPTS, Duration.ZERO);
        }
        state.lastSeen = AppClock.nowInstant();
        if (state.lockedUntil != null) {
            if (state.lockedUntil.isAfter(AppClock.nowInstant())) {
                return new LoginAttemptStatus(true, 0, Duration.between(AppClock.nowInstant(), state.lockedUntil));
            }
            attempts.remove(key);
            return new LoginAttemptStatus(false, MAX_ATTEMPTS, Duration.ZERO);
        }
        int remaining = Math.max(0, MAX_ATTEMPTS - state.failedAttempts);
        return new LoginAttemptStatus(false, remaining, Duration.ZERO);
    }

    private LoginAttemptStatus getStatusFromDb(NormalizedTarget target) {
        AttemptState state = loadStateFromDb(target);
        if (state == null) {
            return new LoginAttemptStatus(false, MAX_ATTEMPTS, Duration.ZERO);
        }

        Instant now = AppClock.nowInstant();
        state.lastSeen = now;
        if (state.lockedUntil != null) {
            if (state.lockedUntil.isAfter(now)) {
                saveStateToDb(target, state);
                return new LoginAttemptStatus(true, 0, Duration.between(now, state.lockedUntil));
            }
            deleteStateFromDb(target);
            return new LoginAttemptStatus(false, MAX_ATTEMPTS, Duration.ZERO);
        }

        saveStateToDb(target, state);
        int remaining = Math.max(0, MAX_ATTEMPTS - state.failedAttempts);
        return new LoginAttemptStatus(false, remaining, Duration.ZERO);
    }

    private LoginAttemptStatus registerFailureInDb(SessionService.Realm realm, String username, NormalizedTarget target) {
        logger.debug("記錄登入失敗 - 領域: {}, 用戶: {}", realm, username);
        Instant now = AppClock.nowInstant();

        AttemptState state = loadStateFromDb(target);
        if (state == null) {
            logger.debug("為用戶 {} 創建新的嘗試狀態", username);
            state = new AttemptState();
        }
        state.lastSeen = now;

        if (state.lockedUntil != null && state.lockedUntil.isAfter(now)) {
            logger.warn("帳戶已鎖定 - 領域: {}, 用戶: {}", realm, username);
            saveStateToDb(target, state);
            return new LoginAttemptStatus(true, 0, Duration.between(now, state.lockedUntil));
        }

        if (state.lockedUntil != null && !state.lockedUntil.isAfter(now)) {
            logger.info("帳戶鎖定已過期，重置計數器 - 領域: {}, 用戶: {}", realm, username);
            state.failedAttempts = 0;
            state.lockedUntil = null;
        }

        state.failedAttempts++;
        logger.debug("登入失敗次數: {}/{} - 領域: {}, 用戶: {}", state.failedAttempts, MAX_ATTEMPTS, realm, username);

        if (state.failedAttempts >= MAX_ATTEMPTS) {
            state.lockedUntil = now.plus(LOCK_DURATION);
            logger.warn("達到最大失敗次數，帳戶已鎖定 {} 分鐘 - 領域: {}, 用戶: {}", LOCK_DURATION.toMinutes(), realm, username);
        }

        saveStateToDb(target, state);
        return state.lockedUntil != null && state.lockedUntil.isAfter(now)
                ? new LoginAttemptStatus(true, 0, Duration.between(now, state.lockedUntil))
                : new LoginAttemptStatus(false, Math.max(0, MAX_ATTEMPTS - state.failedAttempts), Duration.ZERO);
    }

    private AttemptState loadStateFromDb(NormalizedTarget target) {
        JdbcTemplate jdbc = this.jdbcTemplate;
        if (jdbc == null) {
            return null;
        }
        List<AttemptState> rows = jdbc.query(
                "SELECT failed_attempts, locked_until, last_seen FROM login_attempt_state WHERE realm = ? AND username = ?",
                (rs, rowNum) -> {
                    AttemptState state = new AttemptState();
                    state.failedAttempts = rs.getInt("failed_attempts");
                    Timestamp locked = rs.getTimestamp("locked_until");
                    state.lockedUntil = locked == null ? null : locked.toInstant();
                    Timestamp lastSeen = rs.getTimestamp("last_seen");
                    state.lastSeen = lastSeen == null ? AppClock.nowInstant() : lastSeen.toInstant();
                    return state;
                },
                target.realm(), target.username());
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void saveStateToDb(NormalizedTarget target, AttemptState state) {
        JdbcTemplate jdbc = this.jdbcTemplate;
        if (jdbc == null) {
            return;
        }
        if (state.lastSeen == null) {
            state.lastSeen = AppClock.nowInstant();
        }
        jdbc.update(
                "INSERT INTO login_attempt_state (realm, username, failed_attempts, locked_until, last_seen) " +
                        "VALUES (?, ?, ?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE failed_attempts = VALUES(failed_attempts), " +
                        "locked_until = VALUES(locked_until), last_seen = VALUES(last_seen)",
                target.realm(),
                target.username(),
                state.failedAttempts,
                toTimestamp(state.lockedUntil),
                toTimestamp(state.lastSeen));
    }

    private void deleteStateFromDb(NormalizedTarget target) {
        JdbcTemplate jdbc = this.jdbcTemplate;
        if (jdbc == null) {
            return;
        }
        jdbc.update("DELETE FROM login_attempt_state WHERE realm = ? AND username = ?", target.realm(), target.username());
    }

    private static Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private NormalizedTarget normalizeTarget(SessionService.Realm realm, String username) {
        String normalized = (username == null || username.isBlank())
                ? "_anonymous"
                : username.trim().toLowerCase();
        String realmCode = realm.name().toLowerCase();
        return new NormalizedTarget(realmCode + ':' + normalized, realmCode, normalized);
    }

    private boolean isDbStoreAvailable() {
        JdbcTemplate jdbc = this.jdbcTemplate;
        if (jdbc == null) {
            return false;
        }

        Boolean ready = dbTableAvailable;
        if (ready != null) {
            return ready;
        }

        synchronized (this) {
            if (dbTableAvailable != null) {
                return dbTableAvailable;
            }
            try {
                jdbc.queryForObject("SELECT COUNT(*) FROM login_attempt_state WHERE 1 = 0", Integer.class);
                dbTableAvailable = true;
            } catch (DataAccessException ex) {
                logger.debug("login_attempt_state 尚未就緒，改用記憶體登入嘗試追蹤。", ex);
                dbTableAvailable = false;
            }
            return dbTableAvailable;
        }
    }

    private void markDbUnavailable(DataAccessException ex) {
        logger.debug("登入嘗試持久化查詢失敗，切回記憶體模式：{}", ex.getMessage());
        dbTableAvailable = false;
    }

    private record NormalizedTarget(String key, String realm, String username) {
    }

    @Scheduled(fixedDelay = 5 * 60 * 1000L)
    void cleanup() {
        if (isDbStoreAvailable()) {
            try {
                Instant staleCutoff = AppClock.nowInstant().minus(ENTRY_TTL);
                jdbcTemplate.update(
                        "DELETE FROM login_attempt_state WHERE last_seen < ? OR (locked_until IS NOT NULL AND locked_until < ?)",
                        toTimestamp(staleCutoff),
                        toTimestamp(staleCutoff));
                return;
            } catch (DataAccessException ex) {
                markDbUnavailable(ex);
            }
        }

        Instant now = AppClock.nowInstant();
        attempts.entrySet().removeIf(entry -> {
            AttemptState state = entry.getValue();
            if (state == null) {
                return true;
            }
            Instant lastSeen = (state.lastSeen == null) ? now : state.lastSeen;
            if (lastSeen.plus(ENTRY_TTL).isBefore(now)) {
                return true;
            }
            if (state.lockedUntil != null && state.lockedUntil.plus(ENTRY_TTL).isBefore(now)) {
                return true;
            }
            return false;
        });
    }
}

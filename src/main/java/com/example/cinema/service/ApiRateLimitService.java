package com.example.cinema.service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
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
import com.example.cinema.exception.RateLimitExceededException;

@Service
public class ApiRateLimitService {

    private static final Logger logger = LoggerFactory.getLogger(ApiRateLimitService.class);
    private static final Duration ENTRY_TTL = Duration.ofHours(1);

    private final Map<String, ArrayDeque<Instant>> buckets = new ConcurrentHashMap<>();
    private volatile JdbcTemplate jdbcTemplate;
    private volatile Boolean dbTableAvailable;

    @Autowired(required = false)
    void setJdbcTemplate(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void check(String action, String subject, int limitPerWindow, Duration window) {
        if (limitPerWindow <= 0 || window == null || window.isNegative() || window.isZero()) {
            return;
        }
        String safeAction = (action == null || action.isBlank()) ? "unknown-action" : action.trim();
        String safeSubject = (subject == null || subject.isBlank()) ? "unknown-subject" : subject.trim();
        String key = safeAction + "|" + safeSubject;
        Instant now = AppClock.nowInstant();
        Instant cutoff = now.minus(window);

        if (isDbStoreAvailable()) {
            try {
                checkWithDb(safeAction, safeSubject, limitPerWindow, now, cutoff);
                return;
            } catch (DataAccessException ex) {
                markDbUnavailable(ex);
            }
        }

        ArrayDeque<Instant> queue = buckets.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (queue) {
            while (!queue.isEmpty() && queue.peekFirst().isBefore(cutoff)) {
                queue.pollFirst();
            }
            if (queue.size() >= limitPerWindow) {
                throw new RateLimitExceededException("操作過於頻繁，請稍後再試。");
            }
            queue.addLast(now);
        }
    }

    private void checkWithDb(String action, String subject, int limitPerWindow, Instant now, Instant cutoff) {
        JdbcTemplate jdbc = this.jdbcTemplate;
        if (jdbc == null) {
            return;
        }
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM api_rate_limit_events WHERE action = ? AND subject = ? AND happened_at >= ?",
                Integer.class,
                action,
                subject,
                toTimestamp(cutoff));
        if (count != null && count >= limitPerWindow) {
            throw new RateLimitExceededException("操作過於頻繁，請稍後再試。");
        }
        jdbc.update(
                "INSERT INTO api_rate_limit_events (action, subject, happened_at) VALUES (?, ?, ?)",
                action,
                subject,
                toTimestamp(now));
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
                jdbc.queryForObject("SELECT COUNT(*) FROM api_rate_limit_events WHERE 1 = 0", Integer.class);
                dbTableAvailable = true;
            } catch (DataAccessException ex) {
                logger.debug("api_rate_limit_events 尚未就緒，改用記憶體限流。", ex);
                dbTableAvailable = false;
            }
            return dbTableAvailable;
        }
    }

    private void markDbUnavailable(DataAccessException ex) {
        logger.debug("API 限流持久化查詢失敗，切回記憶體模式：{}", ex.getMessage());
        dbTableAvailable = false;
    }

    private static Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    @Scheduled(fixedDelayString = "${app.rate-limit.cleanup-ms:300000}")
    void cleanup() {
        if (isDbStoreAvailable()) {
            try {
                jdbcTemplate.update(
                        "DELETE FROM api_rate_limit_events WHERE happened_at < ?",
                        toTimestamp(AppClock.nowInstant().minus(ENTRY_TTL)));
                return;
            } catch (DataAccessException ex) {
                markDbUnavailable(ex);
            }
        }

        Instant now = AppClock.nowInstant();
        Instant staleCutoff = now.minus(ENTRY_TTL);
        buckets.entrySet().removeIf(entry -> {
            ArrayDeque<Instant> queue = entry.getValue();
            if (queue == null) {
                return true;
            }
            synchronized (queue) {
                while (!queue.isEmpty() && queue.peekFirst().isBefore(staleCutoff)) {
                    queue.pollFirst();
                }
                return queue.isEmpty();
            }
        });
    }
}

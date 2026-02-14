package com.example.cinema.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.example.cinema.config.AppClock;
import com.example.cinema.exception.RateLimitExceededException;

@Service
public class ApiRateLimitService {

    private final Map<String, ArrayDeque<Instant>> buckets = new ConcurrentHashMap<>();

    public void check(String action, String subject, int limitPerWindow, Duration window) {
        if (limitPerWindow <= 0 || window == null || window.isNegative() || window.isZero()) {
            return;
        }
        String safeAction = (action == null || action.isBlank()) ? "unknown-action" : action.trim();
        String safeSubject = (subject == null || subject.isBlank()) ? "unknown-subject" : subject.trim();
        String key = safeAction + "|" + safeSubject;
        Instant now = AppClock.nowInstant();
        Instant cutoff = now.minus(window);

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

    @Scheduled(fixedDelayString = "${app.rate-limit.cleanup-ms:300000}")
    void cleanup() {
        Instant now = AppClock.nowInstant();
        Instant staleCutoff = now.minus(Duration.ofHours(1));
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

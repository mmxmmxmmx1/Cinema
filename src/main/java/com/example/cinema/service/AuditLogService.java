package com.example.cinema.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final JdbcTemplate jdbcTemplate;

    public AuditLogService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void log(String actorType, String actorId, String action, String targetType, String targetId, String result,
            String detail) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO audit_logs (actor_type, actor_id, action, target_type, target_id, result, detail) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    safe(actorType, 30),
                    safe(actorId, 100),
                    safe(action, 50),
                    safe(targetType, 30),
                    safe(targetId, 100),
                    safe(result, 20),
                    safe(detail, 1000));
        } catch (Exception ex) {
            // Audit failures should not break business flow.
            log.warn("Failed to write audit log: {}", ex.getMessage());
        }
    }

    private static String safe(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= max) {
            return trimmed;
        }
        return trimmed.substring(0, max);
    }
}


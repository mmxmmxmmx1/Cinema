package com.example.cinema.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.notification.provider", havingValue = "inapp", matchIfMissing = true)
public class InAppNotificationSender implements NotificationSender {

    private final JdbcTemplate jdbcTemplate;

    public InAppNotificationSender(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void send(NotificationCommand command) {
        if (command == null) {
            return;
        }
        jdbcTemplate.update(
                "INSERT INTO notifications (member_id, category, title, message) VALUES (?, ?, ?, ?)",
                command.memberId(),
                safe(command.category(), 30),
                safe(command.title(), 150),
                safe(command.message(), 500));
    }

    private static String safe(String raw, int max) {
        if (raw == null) {
            return "";
        }
        String value = raw.trim();
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}


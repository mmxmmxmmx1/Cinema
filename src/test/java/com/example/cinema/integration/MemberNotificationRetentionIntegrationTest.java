package com.example.cinema.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.example.cinema.config.AppClock;
import com.example.cinema.dto.MemberNotificationResponse;
import com.example.cinema.service.MemberNotificationService;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("會員通知保留機制整合測試")
class MemberNotificationRetentionIntegrationTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Taipei");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MemberNotificationService memberNotificationService;

    @BeforeEach
    void setUpSchemaAndClock() throws Exception {
        setClock(ZonedDateTime.of(2026, 2, 17, 10, 0, 0, 0, ZONE).toInstant());
        jdbcTemplate.execute("DROP TABLE IF EXISTS notifications");
        jdbcTemplate.execute("DROP TABLE IF EXISTS members");

        jdbcTemplate.execute(
                "CREATE TABLE members (" +
                        "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                        "nickname VARCHAR(100) NOT NULL UNIQUE, " +
                        "first_name VARCHAR(100) NOT NULL DEFAULT '', " +
                        "last_name VARCHAR(100) NOT NULL DEFAULT '', " +
                        "email VARCHAR(255), " +
                        "phone VARCHAR(50), " +
                        "password VARCHAR(255) NOT NULL, " +
                        "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");

        jdbcTemplate.execute(
                "CREATE TABLE notifications (" +
                        "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                        "member_id BIGINT NOT NULL, " +
                        "category VARCHAR(30) NOT NULL, " +
                        "title VARCHAR(150) NOT NULL, " +
                        "message VARCHAR(500) NOT NULL, " +
                        "read_at TIMESTAMP NULL, " +
                        "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");

        jdbcTemplate.update(
                "INSERT INTO members (nickname, password) VALUES (?, ?)",
                "test123",
                "{noop}test123");
    }

    @AfterEach
    void resetClock() throws Exception {
        Field field = AppClock.class.getDeclaredField("clock");
        field.setAccessible(true);
        field.set(null, Clock.system(ZONE));
    }

    @Test
    @DisplayName("通知應只保留 30 天內資料，清理後仍可正確查詢")
    void shouldKeepOnlyNotificationsWithinRetentionWindow() {
        Long memberId = jdbcTemplate.queryForObject(
                "SELECT id FROM members WHERE nickname = ?",
                Long.class,
                "test123");

        Instant now = AppClock.nowInstant();
        jdbcTemplate.update(
                "INSERT INTO notifications (member_id, category, title, message, created_at) VALUES (?, 'ORDER', ?, ?, ?)",
                memberId,
                "old-31d",
                "old-31d",
                Timestamp.from(now.minusSeconds(31L * 24 * 3600)));
        jdbcTemplate.update(
                "INSERT INTO notifications (member_id, category, title, message, created_at) VALUES (?, 'ORDER', ?, ?, ?)",
                memberId,
                "edge-30d",
                "edge-30d",
                Timestamp.from(now.minusSeconds(30L * 24 * 3600)));
        jdbcTemplate.update(
                "INSERT INTO notifications (member_id, category, title, message, created_at) VALUES (?, 'PAYMENT', ?, ?, ?)",
                memberId,
                "new-5d",
                "new-5d",
                Timestamp.from(now.minusSeconds(5L * 24 * 3600)));

        memberNotificationService.purgeExpiredNotifications();

        Integer dbCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notifications", Integer.class);
        assertEquals(2, dbCount == null ? 0 : dbCount.intValue());

        List<MemberNotificationResponse> rows = memberNotificationService.listForMember("test123", 10);
        assertEquals(2, rows.size());
        assertEquals(2, memberNotificationService.unreadCount("test123"));
    }

    private static void setClock(Instant instant) throws Exception {
        Field field = AppClock.class.getDeclaredField("clock");
        field.setAccessible(true);
        field.set(null, Clock.fixed(instant, ZONE));
    }
}

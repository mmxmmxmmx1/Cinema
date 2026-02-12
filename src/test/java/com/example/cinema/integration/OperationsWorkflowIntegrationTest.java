package com.example.cinema.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.example.cinema.dto.MaintenanceRequestSummary;
import com.example.cinema.dto.MemberPointLog;
import com.example.cinema.service.MaintenanceRequestService;
import com.example.cinema.service.MemberLoyaltyService;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("營運流程整合測試")
class OperationsWorkflowIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MaintenanceRequestService maintenanceRequestService;

    @Autowired
    private MemberLoyaltyService memberLoyaltyService;

    @BeforeEach
    void setUpSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS member_point_redemptions");
        jdbcTemplate.execute("DROP TABLE IF EXISTS member_orders");
        jdbcTemplate.execute("DROP TABLE IF EXISTS members");
        jdbcTemplate.execute("DROP TABLE IF EXISTS maintenance_requests");

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
                "CREATE TABLE member_orders (" +
                        "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                        "member_id BIGINT NOT NULL, " +
                        "movie_id VARCHAR(50) NOT NULL, " +
                        "showtime_id VARCHAR(50) NOT NULL, " +
                        "auditorium VARCHAR(100) NOT NULL, " +
                        "total_qty INT NOT NULL, " +
                        "unit_price INT NOT NULL DEFAULT 300, " +
                        "total_price INT NOT NULL DEFAULT 0, " +
                        "status VARCHAR(20) NOT NULL, " +
                        "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                        "paid_at TIMESTAMP NULL)");

        jdbcTemplate.execute(
                "CREATE TABLE member_point_redemptions (" +
                        "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                        "member_id BIGINT NOT NULL, " +
                        "reward_code VARCHAR(50) NOT NULL, " +
                        "reward_name VARCHAR(100) NOT NULL, " +
                        "points_spent INT NOT NULL, " +
                        "note VARCHAR(255), " +
                        "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");

        jdbcTemplate.execute(
                "CREATE TABLE maintenance_requests (" +
                        "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                        "tracking_no VARCHAR(40), " +
                        "requester VARCHAR(100) NOT NULL, " +
                        "assignee VARCHAR(100), " +
                        "auditorium VARCHAR(100), " +
                        "title VARCHAR(150) NOT NULL, " +
                        "description VARCHAR(1000) NOT NULL, " +
                        "priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM', " +
                        "status VARCHAR(20) NOT NULL DEFAULT 'OPEN', " +
                        "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                        "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                        "started_at TIMESTAMP NULL, " +
                        "resolved_at TIMESTAMP NULL, " +
                        "closed_at TIMESTAMP NULL, " +
                        "closed_by VARCHAR(100), " +
                        "resolution_note VARCHAR(500))");

        jdbcTemplate.update(
                "INSERT INTO members (nickname, password) VALUES (?, ?)",
                "test123",
                "{noop}test123");
    }

    @Test
    @DisplayName("點數應可依付款累積並可兌換扣點")
    void shouldEarnAndRedeemPoints() {
        Long memberId = jdbcTemplate.queryForObject(
                "SELECT id FROM members WHERE nickname = ?",
                Long.class,
                "test123");

        jdbcTemplate.update(
                "INSERT INTO member_orders (member_id, movie_id, showtime_id, auditorium, total_qty, unit_price, total_price, status, created_at, paid_at) " +
                        "VALUES (?, 'mv-01', 'mv-01-st1', '1號廳', 4, 300, 1200, 'PAID', ?, ?)",
                memberId,
                Timestamp.from(Instant.now().minusSeconds(3600)),
                Timestamp.from(Instant.now().minusSeconds(3500)));

        assertEquals(120, memberLoyaltyService.currentPoints("test123"));
        int remaining = memberLoyaltyService.redeem("test123", "POPCORN_S");
        assertEquals(70, remaining);
        assertEquals(70, memberLoyaltyService.currentPoints("test123"));

        List<MemberPointLog> logs = memberLoyaltyService.recentPointLogs("test123", 10);
        assertFalse(logs.isEmpty());
        assertTrue(logs.stream().anyMatch(log -> log.points() < 0));
        assertTrue(logs.stream().anyMatch(log -> log.points() > 0));
    }

    @Test
    @DisplayName("維修申請應可依序轉為處理中、已解決、已結案")
    void shouldAdvanceMaintenanceLifecycle() {
        String trackingNo = maintenanceRequestService.createRequest(
                "emp01",
                "1號廳",
                "投影亮度異常",
                "畫面過暗",
                "HIGH");
        assertTrue(trackingNo.startsWith("MR-"));

        long requestId = jdbcTemplate.queryForObject(
                "SELECT id FROM maintenance_requests WHERE tracking_no = ?",
                Long.class,
                trackingNo);

        maintenanceRequestService.updateStatus(requestId, "emp01", "IN_PROGRESS", "it01", "");
        maintenanceRequestService.updateStatus(requestId, "it01", "RESOLVED", "it01", "已更換燈泡");
        maintenanceRequestService.updateStatus(requestId, "it01", "CLOSED", "it01", "驗收完成");

        List<MaintenanceRequestSummary> rows = maintenanceRequestService.listRecent(5);
        assertFalse(rows.isEmpty());
        MaintenanceRequestSummary row = rows.get(0);
        assertEquals("CLOSED", row.status());
        assertEquals("it01", row.assignee());
        assertEquals("it01", row.closedBy());
    }
}

package com.example.cinema.integration;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.example.cinema.config.AppClock;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("會員訂票 UI 層 E2E")
class MemberOrderWebE2EIntegrationTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Taipei");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUpSchemaAndClock() throws Exception {
        setTestClock(2026, 2, 12, 7, 10);

        jdbcTemplate.execute("DROP TABLE IF EXISTS audit_logs");
        jdbcTemplate.execute("DROP TABLE IF EXISTS notifications");
        jdbcTemplate.execute("DROP TABLE IF EXISTS payment_transactions");
        jdbcTemplate.execute("DROP TABLE IF EXISTS payment_idempotency");
        jdbcTemplate.execute("DROP TABLE IF EXISTS member_tickets");
        jdbcTemplate.execute("DROP TABLE IF EXISTS member_order_items");
        jdbcTemplate.execute("DROP TABLE IF EXISTS member_orders");
        jdbcTemplate.execute("DROP TABLE IF EXISTS member_point_balance");
        jdbcTemplate.execute("DROP TABLE IF EXISTS member_point_ledger");
        jdbcTemplate.execute("DROP TABLE IF EXISTS member_point_redemptions");
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
                        "created_at TIMESTAMP NULL, " +
                        "paid_at TIMESTAMP NULL, " +
                        "cancelled_at TIMESTAMP NULL, " +
                        "failed_at TIMESTAMP NULL, " +
                        "expired_at TIMESTAMP NULL, " +
                        "failure_reason VARCHAR(255) NULL, " +
                        "payment_attempts INT NOT NULL DEFAULT 0, " +
                        "payment_reference VARCHAR(100) NULL, " +
                        "payment_mode VARCHAR(20) NULL, " +
                        "updated_at TIMESTAMP NULL)");
        jdbcTemplate.execute(
                "CREATE TABLE member_order_items (" +
                        "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                        "order_id BIGINT NOT NULL, " +
                        "seat_id VARCHAR(10) NOT NULL)");
        jdbcTemplate.execute(
                "CREATE TABLE member_tickets (" +
                        "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                        "order_id BIGINT NULL, " +
                        "member_id BIGINT NOT NULL, " +
                        "movie_id VARCHAR(50) NOT NULL, " +
                        "showtime_id VARCHAR(50) NOT NULL, " +
                        "show_date DATE NOT NULL, " +
                        "show_start_at TIMESTAMP NOT NULL, " +
                        "auditorium VARCHAR(100) NOT NULL, " +
                        "seat_id VARCHAR(10) NOT NULL, " +
                        "purchased_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                        "UNIQUE (show_start_at, showtime_id, seat_id))");
        jdbcTemplate.execute(
                "CREATE TABLE payment_transactions (" +
                        "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                        "order_id BIGINT NOT NULL, " +
                        "member_id BIGINT NOT NULL, " +
                        "amount INT NOT NULL, " +
                        "mode VARCHAR(20) NOT NULL, " +
                        "gateway VARCHAR(50) NOT NULL, " +
                        "status VARCHAR(20) NOT NULL, " +
                        "reference VARCHAR(100), " +
                        "error_message VARCHAR(255), " +
                        "requested_at TIMESTAMP NULL, " +
                        "completed_at TIMESTAMP NULL)");
        jdbcTemplate.execute(
                "CREATE TABLE payment_idempotency (" +
                        "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                        "member_id BIGINT NOT NULL, " +
                        "order_id BIGINT NOT NULL, " +
                        "idempotency_key VARCHAR(120) NOT NULL, " +
                        "status VARCHAR(20) NOT NULL, " +
                        "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                        "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                        "UNIQUE (member_id, order_id, idempotency_key))");
        jdbcTemplate.execute(
                "CREATE TABLE notifications (" +
                        "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                        "member_id BIGINT NOT NULL, " +
                        "category VARCHAR(30) NOT NULL, " +
                        "title VARCHAR(150) NOT NULL, " +
                        "message VARCHAR(500) NOT NULL, " +
                        "read_at TIMESTAMP NULL, " +
                        "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        jdbcTemplate.execute(
                "CREATE TABLE audit_logs (" +
                        "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                        "actor_type VARCHAR(30) NOT NULL, " +
                        "actor_id VARCHAR(100), " +
                        "action VARCHAR(50) NOT NULL, " +
                        "target_type VARCHAR(30), " +
                        "target_id VARCHAR(100), " +
                        "result VARCHAR(20) NOT NULL, " +
                        "detail VARCHAR(1000), " +
                        "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        jdbcTemplate.execute(
                "CREATE TABLE member_point_ledger (" +
                        "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                        "member_id BIGINT NOT NULL, " +
                        "event_key VARCHAR(100) NOT NULL, " +
                        "event_type VARCHAR(20) NOT NULL, " +
                        "ref_order_id BIGINT NULL, " +
                        "ref_redemption_id BIGINT NULL, " +
                        "amount INT NOT NULL DEFAULT 0, " +
                        "points_delta INT NOT NULL, " +
                        "description VARCHAR(255) NOT NULL, " +
                        "happened_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                        "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                        "UNIQUE (event_key))");
        jdbcTemplate.execute(
                "CREATE TABLE member_point_balance (" +
                        "member_id BIGINT PRIMARY KEY, " +
                        "points_balance INT NOT NULL DEFAULT 0, " +
                        "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbcTemplate.execute(
                "CREATE TABLE member_point_redemptions (" +
                        "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                        "member_id BIGINT NOT NULL, " +
                        "reward_code VARCHAR(50) NOT NULL, " +
                        "reward_name VARCHAR(100) NOT NULL, " +
                        "points_spent INT NOT NULL, " +
                        "note VARCHAR(255), " +
                        "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");

        jdbcTemplate.update(
                "INSERT INTO members (nickname, password) VALUES (?, ?)",
                "test123",
                "{noop}test123");
    }

    @AfterEach
    void tearDownClock() throws Exception {
        Field field = AppClock.class.getDeclaredField("clock");
        field.setAccessible(true);
        field.set(null, Clock.system(ZONE));
    }

    @Test
    @WithMockUser(username = "test123", roles = "MEMBER")
    @DisplayName("訂票→付款→取消→釋位，頁面應可看到訂單狀態")
    void shouldCompleteBookingFlowThroughWebLayer() throws Exception {
        String requestBody = """
                {"movieId":"mv-02","showtimeId":"mv-02-st1","seatIds":["A01","A02"]}
                """;

        MvcResult created = mockMvc.perform(post("/member/api/orders")
                        .with(csrf())
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();

        JsonNode createdJson = objectMapper.readTree(created.getResponse().getContentAsString());
        long orderId = createdJson.path("orderId").asLong();
        assertTrue(orderId > 0, "建立訂單後應取得 orderId");

        mockMvc.perform(post("/member/api/orders/{orderId}/pay", orderId)
                        .with(csrf())
                        .param("mode", "SUCCESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));

        mockMvc.perform(get("/member/orders"))
                .andExpect(status().isOk())
                .andExpect(view().name("member-orders"))
                .andExpect(content().string(containsString("#" + orderId)));

        mockMvc.perform(post("/member/orders/{orderId}/cancel", orderId)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:/member/orders"))
                .andExpect(flash().attributeExists("success"));

        mockMvc.perform(post("/member/api/orders")
                        .with(csrf())
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @WithMockUser(username = "test123", roles = "MEMBER")
    @DisplayName("開演前 30 分鐘內，頁面取消應顯示拒絕訊息")
    void shouldRejectCancelWithinThirtyMinutesFromWebPage() throws Exception {
        String requestBody = """
                {"movieId":"mv-02","showtimeId":"mv-02-st1","seatIds":["A03"]}
                """;

        MvcResult created = mockMvc.perform(post("/member/api/orders")
                        .with(csrf())
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andReturn();

        long orderId = objectMapper.readTree(created.getResponse().getContentAsString()).path("orderId").asLong();
        mockMvc.perform(post("/member/api/orders/{orderId}/pay", orderId)
                        .with(csrf())
                        .param("mode", "SUCCESS"))
                .andExpect(status().isOk());

        setTestClock(2026, 2, 12, 8, 30);

        mockMvc.perform(post("/member/orders/{orderId}/cancel", orderId)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:/member/orders"))
                .andExpect(flash().attribute("error", containsString("不可取消")));
    }

    private static void setTestClock(int year, int month, int day, int hour, int minute) throws Exception {
        Field field = AppClock.class.getDeclaredField("clock");
        field.setAccessible(true);
        field.set(null, Clock.fixed(
                ZonedDateTime.of(year, month, day, hour, minute, 0, 0, ZONE).toInstant(),
                ZONE));
    }
}

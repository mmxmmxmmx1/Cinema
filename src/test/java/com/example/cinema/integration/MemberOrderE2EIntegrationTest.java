package com.example.cinema.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
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
import com.example.cinema.dto.OrderDetailResponse;
import com.example.cinema.dto.OrderSummaryResponse;
import com.example.cinema.dto.UpcomingBookingResponse;
import com.example.cinema.exception.TicketPurchaseConflictException;
import com.example.cinema.exception.TicketPurchaseRuleViolationException;
import com.example.cinema.model.SeatStatus;
import com.example.cinema.service.MemberOrderService;
import com.example.cinema.service.MovieService;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("會員訂單 E2E 流程整合測試")
class MemberOrderE2EIntegrationTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Taipei");
    private static final String MEMBER = "test123";
    private static final String MOVIE_ID = "mv-02";
    private static final String SHOWTIME_ID = "mv-02-st1";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MemberOrderService memberOrderService;

    @Autowired
    private MovieService movieService;

    @BeforeEach
    void setUpSchemaAndClock() throws Exception {
        setTestClock(ZonedDateTime.of(2026, 2, 12, 7, 10, 0, 0, ZONE).toInstant());

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
                MEMBER,
                "{noop}test123");
    }

    @AfterEach
    void tearDownClock() throws Exception {
        resetClock();
    }

    @Test
    @DisplayName("訂票→付款→取消應釋放座位，且可再次購買同座位")
    void shouldPurchasePayCancelAndReleaseSeats() {
        List<String> seats = pickAvailableSeats(2);

        OrderDetailResponse created = memberOrderService.createOrder(MEMBER, MOVIE_ID, SHOWTIME_ID, seats);
        assertEquals("PENDING", created.status());

        OrderDetailResponse paid = memberOrderService.payOrder(MEMBER, created.orderId(), "SUCCESS");
        assertEquals("PAID", paid.status());
        assertEquals(2, countTicketsByOrder(created.orderId()));

        assertThrows(TicketPurchaseConflictException.class, () ->
                memberOrderService.createOrder(MEMBER, MOVIE_ID, SHOWTIME_ID, seats));

        OrderDetailResponse cancelled = memberOrderService.cancelOrder(MEMBER, created.orderId());
        assertEquals("CANCELLED", cancelled.status());
        assertEquals(0, countTicketsByOrder(created.orderId()));

        OrderDetailResponse retry = memberOrderService.createOrder(MEMBER, MOVIE_ID, SHOWTIME_ID, seats);
        OrderDetailResponse retryPaid = memberOrderService.payOrder(MEMBER, retry.orderId(), "SUCCESS");
        assertEquals("PAID", retryPaid.status());
        assertEquals(2, countTicketsByOrder(retry.orderId()));
    }

    @Test
    @DisplayName("同一 idempotency key 的重複付款請求不應重複扣款")
    void shouldHandlePaymentIdempotency() {
        List<String> seats = pickAvailableSeats(1);
        OrderDetailResponse created = memberOrderService.createOrder(MEMBER, MOVIE_ID, SHOWTIME_ID, seats);

        OrderDetailResponse first = memberOrderService.payOrder(MEMBER, created.orderId(), "SUCCESS", "idem-001");
        OrderDetailResponse second = memberOrderService.payOrder(MEMBER, created.orderId(), "SUCCESS", "idem-001");

        assertEquals("PAID", first.status());
        assertEquals("PAID", second.status());
        assertEquals(1, countPaymentTransactions(created.orderId()));
    }

    @Test
    @DisplayName("取消已付款訂單時應回滾紅利點數")
    void shouldRollbackPointsAfterPaidOrderCancelled() {
        List<String> seats = pickAvailableSeats(2);
        OrderDetailResponse created = memberOrderService.createOrder(MEMBER, MOVIE_ID, SHOWTIME_ID, seats);
        OrderDetailResponse paid = memberOrderService.payOrder(MEMBER, created.orderId(), "SUCCESS");
        assertEquals("PAID", paid.status());
        assertEquals(60, pointBalanceOfMember(MEMBER));

        OrderDetailResponse cancelled = memberOrderService.cancelOrder(MEMBER, created.orderId());
        assertEquals("CANCELLED", cancelled.status());
        assertEquals(0, pointBalanceOfMember(MEMBER));
    }

    @Test
    @DisplayName("開演前 30 分鐘內不可取消已付款訂單")
    void shouldRejectCancelWithinThirtyMinutesBeforeShowtime() throws Exception {
        List<String> seats = pickAvailableSeats(1);
        OrderDetailResponse created = memberOrderService.createOrder(MEMBER, MOVIE_ID, SHOWTIME_ID, seats);
        OrderDetailResponse paid = memberOrderService.payOrder(MEMBER, created.orderId(), "SUCCESS");
        assertEquals("PAID", paid.status());

        setTestClock(ZonedDateTime.of(2026, 2, 12, 8, 30, 0, 0, ZONE).toInstant());
        assertThrows(TicketPurchaseRuleViolationException.class,
                () -> memberOrderService.cancelOrder(MEMBER, created.orderId()));
    }

    @Test
    @DisplayName("開演後不可取消已付款訂單")
    void shouldRejectCancelAfterShowStarted() throws Exception {
        List<String> seats = pickAvailableSeats(1);
        OrderDetailResponse created = memberOrderService.createOrder(MEMBER, MOVIE_ID, SHOWTIME_ID, seats);
        OrderDetailResponse paid = memberOrderService.payOrder(MEMBER, created.orderId(), "SUCCESS");
        assertEquals("PAID", paid.status());

        setTestClock(ZonedDateTime.of(2026, 2, 12, 9, 10, 0, 0, ZONE).toInstant());
        assertThrows(TicketPurchaseRuleViolationException.class,
                () -> memberOrderService.cancelOrder(MEMBER, created.orderId()));
    }

    @Test
    @DisplayName("單筆訂單超過 4 張應被拒絕")
    void shouldRejectMoreThanFourTicketsPerOrder() {
        List<String> seats = pickAvailableSeats(5);
        assertThrows(TicketPurchaseRuleViolationException.class,
                () -> memberOrderService.createOrder(MEMBER, MOVIE_ID, SHOWTIME_ID, seats));
    }

    @Test
    @DisplayName("舊資料時間異常（00:00）時，訂單頁/會員頁仍應顯示一致的場次時間")
    void shouldKeepShowtimeDateTimeConsistentAcrossViews() {
        List<String> seats = pickAvailableSeats(1);
        OrderDetailResponse created = memberOrderService.createOrder(MEMBER, MOVIE_ID, SHOWTIME_ID, seats);
        OrderDetailResponse paid = memberOrderService.payOrder(MEMBER, created.orderId(), "SUCCESS");

        jdbcTemplate.update(
                "UPDATE member_tickets SET show_start_at = CAST(show_date AS TIMESTAMP) WHERE order_id = ?",
                paid.orderId());

        OrderDetailResponse detail = memberOrderService.getOrder(MEMBER, paid.orderId());
        List<OrderSummaryResponse> orders = memberOrderService.listOrders(MEMBER);
        List<UpcomingBookingResponse> upcoming = memberOrderService.listUpcomingBookings(MEMBER, 5);

        assertNotNull(detail.showStartAt(), "detail.showStartAt 不應為 null");
        assertFalse(orders.isEmpty(), "listOrders 不應為空");
        assertFalse(upcoming.isEmpty(), "listUpcomingBookings 不應為空");

        Instant fromDetail = detail.showStartAt();
        Instant fromOrders = orders.get(0).showStartAt();
        Instant fromUpcoming = upcoming.get(0).showStartAt();

        assertEquals(fromDetail, fromOrders, "訂單列表與詳情場次時間需一致");
        assertEquals(fromDetail, fromUpcoming, "會員頁 upcoming 與詳情場次時間需一致");

        LocalTime local = fromDetail.atZone(ZONE).toLocalTime().withSecond(0).withNano(0);
        assertEquals(LocalTime.of(8, 50), local, "場次時間應還原為 08:50，而非 00:00");
    }

    private List<String> pickAvailableSeats(int count) {
        List<String> seats = movieService.getShowtimeDetails(MOVIE_ID, SHOWTIME_ID)
                .getSeatLayout()
                .getSeats()
                .stream()
                .filter(seat -> seat != null && !seat.isReserved())
                .map(SeatStatus::getSeatId)
                .limit(count)
                .toList();
        assertEquals(count, seats.size(), "可用座位不足，無法執行測試");
        return seats;
    }

    private int countTicketsByOrder(long orderId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM member_tickets WHERE order_id = ?",
                Integer.class,
                orderId);
        return count == null ? 0 : count.intValue();
    }

    private int countPaymentTransactions(long orderId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payment_transactions WHERE order_id = ?",
                Integer.class,
                orderId);
        return count == null ? 0 : count.intValue();
    }

    private int pointBalanceOfMember(String nickname) {
        Integer points = jdbcTemplate.queryForObject(
                "SELECT COALESCE(pb.points_balance, 0) " +
                        "FROM members m LEFT JOIN member_point_balance pb ON pb.member_id = m.id " +
                        "WHERE m.nickname = ?",
                Integer.class,
                nickname);
        return points == null ? 0 : points.intValue();
    }

    private static void setTestClock(Instant instant) throws Exception {
        Field field = AppClock.class.getDeclaredField("clock");
        field.setAccessible(true);
        field.set(null, Clock.fixed(instant, ZONE));
    }

    private static void resetClock() throws Exception {
        Field field = AppClock.class.getDeclaredField("clock");
        field.setAccessible(true);
        field.set(null, Clock.system(ZONE));
    }
}

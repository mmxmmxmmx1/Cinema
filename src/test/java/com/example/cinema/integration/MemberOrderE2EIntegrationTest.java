package com.example.cinema.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
    private static final DateTimeFormatter SHOW_START_LABEL_FORMATTER = DateTimeFormatter.ofPattern("MM/dd HH:mm")
            .withZone(ZONE);
    private static final String MEMBER = "test123";
    private static final String MOVIE_ID = "mv-02";
    private static final String SHOWTIME_ID = "mv-02-st1";
    private static final String LATER_OTHER_AUDITORIUM_SHOWTIME_ID = "mv-02-st5";

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
    @DisplayName("付款失敗後可在逾時前重新付款成功")
    void shouldAllowRetryPaymentAfterFailure() {
        List<String> seats = pickAvailableSeats(1);
        OrderDetailResponse created = memberOrderService.createOrder(MEMBER, MOVIE_ID, SHOWTIME_ID, seats);

        OrderDetailResponse failed = memberOrderService.payOrder(MEMBER, created.orderId(), "FAILED");
        assertEquals("FAILED", failed.status());
        assertEquals(0, countTicketsByOrder(created.orderId()));

        OrderDetailResponse paid = memberOrderService.payOrder(MEMBER, created.orderId(), "SUCCESS");
        assertEquals("PAID", paid.status());
        assertEquals(1, countTicketsByOrder(created.orderId()));
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
    @DisplayName("我的訂單與即將欣賞場次的日期/時間顯示應一致")
    void shouldKeepShowStartDateTimeConsistentAcrossViews() {
        List<String> seats = pickAvailableSeats(1);
        OrderDetailResponse created = memberOrderService.createOrder(MEMBER, MOVIE_ID, SHOWTIME_ID, seats);
        OrderDetailResponse paid = memberOrderService.payOrder(MEMBER, created.orderId(), "SUCCESS");
        assertEquals("PAID", paid.status());

        OrderSummaryResponse activeOrder = memberOrderService.listActiveOrders(MEMBER, 10).stream()
                .filter(o -> o.orderId() == created.orderId())
                .findFirst()
                .orElseThrow();
        UpcomingBookingResponse upcomingBooking = memberOrderService.listUpcomingBookings(MEMBER, 10).stream()
                .filter(o -> o.orderId() == created.orderId())
                .findFirst()
                .orElseThrow();

        assertNotNull(activeOrder.showStartAt());
        assertNotNull(upcomingBooking.showStartAt());
        assertEquals(activeOrder.showStartAt(), upcomingBooking.showStartAt());
        assertEquals(SHOW_START_LABEL_FORMATTER.format(activeOrder.showStartAt()), upcomingBooking.showStartLabel());
    }

    @Test
    @DisplayName("單筆訂單超過 4 張應被拒絕")
    void shouldRejectMoreThanFourTicketsPerOrder() {
        List<String> seats = pickAvailableSeats(5);
        assertThrows(TicketPurchaseRuleViolationException.class,
                () -> memberOrderService.createOrder(MEMBER, MOVIE_ID, SHOWTIME_ID, seats));
    }

    @Test
    @DisplayName("逾時未付款訂單應自動失效並釋放座位")
    void shouldExpirePendingOrderAndReleaseSeats() throws Exception {
        List<String> seats = pickAvailableSeats(1);
        OrderDetailResponse created = memberOrderService.createOrder(MEMBER, MOVIE_ID, SHOWTIME_ID, seats);
        jdbcTemplate.update(
                "UPDATE member_orders SET created_at = ? WHERE id = ?",
                Timestamp.from(ZonedDateTime.of(2026, 2, 12, 7, 10, 0, 0, ZONE).toInstant()),
                created.orderId());

        setTestClock(ZonedDateTime.of(2026, 2, 12, 7, 30, 0, 0, ZONE).toInstant());
        memberOrderService.expireTimedOutPendingOrders();

        OrderDetailResponse expired = memberOrderService.getOrder(MEMBER, created.orderId());
        assertEquals("EXPIRED", expired.status());
        assertEquals(0, countTicketsByOrder(created.orderId()));

        OrderDetailResponse retry = memberOrderService.createOrder(MEMBER, MOVIE_ID, SHOWTIME_ID, seats);
        assertEquals("PENDING", retry.status());
    }

    @Test
    @DisplayName("原場次結束後，應可改買其他影廳的後續場次")
    void shouldAllowCrossAuditoriumPurchaseAfterCurrentShowEnded() throws Exception {
        List<String> firstShowSeats = pickAvailableSeats(SHOWTIME_ID, 4);
        OrderDetailResponse firstOrder = memberOrderService.createOrder(MEMBER, MOVIE_ID, SHOWTIME_ID, firstShowSeats);
        OrderDetailResponse firstPaid = memberOrderService.payOrder(MEMBER, firstOrder.orderId(), "SUCCESS");
        assertEquals("PAID", firstPaid.status());

        List<String> otherAuditoriumSeats = pickAvailableSeats(LATER_OTHER_AUDITORIUM_SHOWTIME_ID, 1);
        assertThrows(TicketPurchaseRuleViolationException.class,
                () -> memberOrderService.createOrder(MEMBER, MOVIE_ID, LATER_OTHER_AUDITORIUM_SHOWTIME_ID,
                        otherAuditoriumSeats));

        // mv-02-st1 starts at 08:50 and lasts 180 minutes, so it ends at 11:50.
        setTestClock(ZonedDateTime.of(2026, 2, 12, 12, 0, 0, 0, ZONE).toInstant());

        OrderDetailResponse secondOrder = memberOrderService.createOrder(
                MEMBER, MOVIE_ID, LATER_OTHER_AUDITORIUM_SHOWTIME_ID, otherAuditoriumSeats);
        assertEquals("PENDING", secondOrder.status());
    }

    @Test
    @DisplayName("同座位並發付款時只能成功一筆")
    void shouldAllowOnlyOneSuccessfulPaymentUnderConcurrentSeatRace() throws Exception {
        String rival = "rival001";
        jdbcTemplate.update(
                "INSERT INTO members (nickname, password) VALUES (?, ?)",
                rival,
                "{noop}test123");

        String seat = pickAvailableSeats(1).get(0);
        OrderDetailResponse firstOrder = memberOrderService.createOrder(MEMBER, MOVIE_ID, SHOWTIME_ID, List.of(seat));
        OrderDetailResponse secondOrder = memberOrderService.createOrder(rival, MOVIE_ID, SHOWTIME_ID, List.of(seat));

        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> firstFuture = executor.submit(payConcurrently(MEMBER, firstOrder.orderId(), startLatch));
            Future<String> secondFuture = executor.submit(payConcurrently(rival, secondOrder.orderId(), startLatch));
            startLatch.countDown();

            String firstResult = firstFuture.get(10, TimeUnit.SECONDS);
            String secondResult = secondFuture.get(10, TimeUnit.SECONDS);
            int paidCount = ("PAID".equals(firstResult) ? 1 : 0) + ("PAID".equals(secondResult) ? 1 : 0);

            assertEquals(1, paidCount);
            assertEquals(1, countTicketsByShowtimeSeat(SHOWTIME_ID, seat));
            assertTrue(firstResult.equals("PAID") || secondResult.equals("PAID"));
        } finally {
            executor.shutdownNow();
        }
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
        return pickAvailableSeats(SHOWTIME_ID, count);
    }

    private List<String> pickAvailableSeats(String showtimeId, int count) {
        List<String> seats = movieService.getShowtimeDetails(MOVIE_ID, showtimeId)
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

    private int countTicketsByShowtimeSeat(String showtimeId, String seatId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM member_tickets WHERE showtime_id = ? AND seat_id = ?",
                Integer.class,
                showtimeId,
                seatId);
        return count == null ? 0 : count.intValue();
    }

    private Callable<String> payConcurrently(String member, long orderId, CountDownLatch startLatch) {
        return () -> {
            startLatch.await(5, TimeUnit.SECONDS);
            try {
                return memberOrderService.payOrder(member, orderId, "SUCCESS").status();
            } catch (TicketPurchaseConflictException | TicketPurchaseRuleViolationException ex) {
                return "REJECTED";
            }
        };
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

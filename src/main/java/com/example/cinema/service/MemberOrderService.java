package com.example.cinema.service;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.cinema.config.AppClock;
import com.example.cinema.dao.UserDao;
import com.example.cinema.dto.OrderDetailResponse;
import com.example.cinema.dto.OrderSummaryResponse;
import com.example.cinema.dto.UpcomingBookingResponse;
import com.example.cinema.exception.TicketPurchaseConflictException;
import com.example.cinema.exception.TicketPurchaseRuleViolationException;
import com.example.cinema.model.ShowtimeDetails;
import com.example.cinema.model.User;

@Service
public class MemberOrderService {

    private static final Logger log = LoggerFactory.getLogger(MemberOrderService.class);

    private static final int MAX_TICKETS_PER_ORDER = 4;
    private static final int MAX_TICKETS_PER_SCREENING_PER_MEMBER = 4;
    private static final int UNIT_PRICE = 300;
    private static final DateTimeFormatter SHOW_START_FORMATTER = DateTimeFormatter.ofPattern("MM/dd HH:mm")
            .withZone(AppClock.zoneId());

    private final JdbcTemplate jdbcTemplate;
    private final UserDao userDao;
    private final MovieService movieService;
    private final PaymentGateway paymentGateway;
    private final MemberNotificationService memberNotificationService;
    private final AuditLogService auditLogService;
    private final MemberLoyaltyService memberLoyaltyService;
    private final int pendingTimeoutMinutes;
    private volatile Boolean paymentIdempotencyEnabled;

    public MemberOrderService(
            JdbcTemplate jdbcTemplate,
            UserDao userDao,
            MovieService movieService,
            PaymentGateway paymentGateway,
            MemberNotificationService memberNotificationService,
            AuditLogService auditLogService,
            MemberLoyaltyService memberLoyaltyService,
            @Value("${app.order.pending-timeout-minutes:15}") int pendingTimeoutMinutes) {
        this.jdbcTemplate = jdbcTemplate;
        this.userDao = userDao;
        this.movieService = movieService;
        this.paymentGateway = paymentGateway;
        this.memberNotificationService = memberNotificationService;
        this.auditLogService = auditLogService;
        this.memberLoyaltyService = memberLoyaltyService;
        this.pendingTimeoutMinutes = Math.max(1, pendingTimeoutMinutes);
    }

    @Transactional
    public OrderDetailResponse createOrder(String memberUsername, String movieId, String showtimeId,
            Collection<String> seatIds) {
        Set<String> requested = normalizeSeatIds(seatIds);
        if (requested.size() > MAX_TICKETS_PER_ORDER) {
            throw new TicketPurchaseRuleViolationException("一次最多只能買 4 張票。");
        }

        User member = loadMember(memberUsername);
        if (!movieService.isBookingWindowOpenNow()) {
            throw new TicketPurchaseRuleViolationException("目前非訂票時段（每日 07:00 - 22:45）。");
        }
        if (!movieService.isShowtimeOpenForPurchase(movieId, showtimeId)) {
            throw new TicketPurchaseRuleViolationException("此場次已開演或已結束，請選擇其他場次。");
        }
        ShowtimeDetails details = movieService.getShowtimeDetails(movieId, showtimeId);
        String auditorium = details.getShowtime().getAuditorium();

        assertSeatsAvailable(details, requested);
        enforcePerScreeningTicketCap(member.getId(), movieId, showtimeId, requested.size());

        int totalPrice = UNIT_PRICE * requested.size();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO member_orders (member_id, movie_id, showtime_id, auditorium, total_qty, unit_price, total_price, status) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, 'PENDING')",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, member.getId());
            ps.setString(2, movieId);
            ps.setString(3, showtimeId);
            ps.setString(4, auditorium);
            ps.setInt(5, requested.size());
            ps.setInt(6, UNIT_PRICE);
            ps.setInt(7, totalPrice);
            return ps;
        }, keyHolder);

        long orderId = extractGeneratedId(keyHolder);
        if (orderId <= 0) {
            throw new TicketPurchaseRuleViolationException("建立訂單失敗：無法取得訂單編號。");
        }

        for (String seatId : requested) {
            jdbcTemplate.update("INSERT INTO member_order_items (order_id, seat_id) VALUES (?, ?)", orderId, seatId);
        }
        Instant showStartInstant = movieService.resolveShowStartInstant(movieId, showtimeId);
        Timestamp showStartAt = Timestamp.from(showStartInstant);
        Date showDate = Date.valueOf(showStartInstant.atZone(AppClock.zoneId()).toLocalDate());
        reserveSeatsForOrder(orderId, member.getId(), movieId, showtimeId, auditorium, requested, showDate, showStartAt);

        memberNotificationService.notifyOrderCreated(member.getId(), orderId, totalPrice);
        auditLogService.log("MEMBER", String.valueOf(member.getId()), "ORDER_CREATE", "ORDER", String.valueOf(orderId),
                "SUCCESS",
                "movie=" + movieId + ",showtime=" + showtimeId + ",qty=" + requested.size() + ",amount=" + totalPrice);

        return getOrderDetail(member.getId(), orderId);
    }

    @Transactional
    public OrderDetailResponse payOrder(String memberUsername, long orderId) {
        return payOrder(memberUsername, orderId, PaymentMode.SUCCESS);
    }

    @Transactional
    public OrderDetailResponse payOrder(String memberUsername, long orderId, String mode) {
        return payOrder(memberUsername, orderId, PaymentMode.fromNullable(mode), null);
    }

    @Transactional
    public OrderDetailResponse payOrder(String memberUsername, long orderId, String mode, String idempotencyKey) {
        return payOrder(memberUsername, orderId, PaymentMode.fromNullable(mode), idempotencyKey);
    }

    @Transactional
    public OrderDetailResponse payOrder(String memberUsername, long orderId, PaymentMode mode) {
        return payOrder(memberUsername, orderId, mode, null);
    }

    @Transactional
    public OrderDetailResponse payOrder(String memberUsername, long orderId, PaymentMode mode, String idempotencyKey) {
        User member = loadMember(memberUsername);
        Map<String, Object> order = getOrderRow(orderId);
        String safeIdempotencyKey = normalizeIdempotencyKey(idempotencyKey);

        long ownerId = ((Number) order.get("member_id")).longValue();
        if (ownerId != member.getId()) {
            throw new TicketPurchaseRuleViolationException("無權限操作此訂單。");
        }

        OrderStatus status = OrderStatus.fromDb(String.valueOf(order.get("status")));
        if (status == OrderStatus.PAID) {
            if (!safeIdempotencyKey.isBlank()
                    && hasSucceededPaymentIdempotency(member.getId(), orderId, safeIdempotencyKey)) {
                return getOrderDetail(member.getId(), orderId);
            }
            throw new TicketPurchaseRuleViolationException("此訂單已付款。");
        }
        if (status == OrderStatus.CANCELLED) {
            throw new TicketPurchaseRuleViolationException("此訂單已取消，無法付款。");
        }
        if (status == OrderStatus.EXPIRED) {
            throw new TicketPurchaseRuleViolationException("此訂單已逾時失效，請重新下單。");
        }

        Instant createdAt = toInstant(order.get("created_at"));
        if (isPastPendingTimeout(createdAt)) {
            markOrderExpired(orderId, "PAYMENT_TIMEOUT");
            memberNotificationService.notifyOrderExpired(member.getId(), orderId, pendingTimeoutMinutes);
            auditLogService.log("MEMBER", String.valueOf(member.getId()), "ORDER_PAY", "ORDER", String.valueOf(orderId),
                    "FAILED", "timeout-before-payment");
            return getOrderDetail(member.getId(), orderId);
        }

        String movieId = String.valueOf(order.get("movie_id"));
        String showtimeId = String.valueOf(order.get("showtime_id"));
        if (!movieService.isBookingWindowOpenNow()) {
            throw new TicketPurchaseRuleViolationException("目前非訂票時段（每日 07:00 - 22:45）。");
        }
        if (!movieService.isShowtimeOpenForPurchase(movieId, showtimeId)) {
            throw new TicketPurchaseRuleViolationException("此場次已開演或已結束，請重新選擇場次。");
        }
        String auditorium = String.valueOf(order.get("auditorium"));
        int totalPrice = ((Number) order.get("total_price")).intValue();

        boolean idempotencyStarted = false;
        if (!safeIdempotencyKey.isBlank()) {
            IdempotencyStartResult startResult = beginPaymentIdempotency(member.getId(), orderId, safeIdempotencyKey);
            if (startResult == IdempotencyStartResult.SUCCEEDED) {
                return getOrderDetail(member.getId(), orderId);
            }
            if (startResult == IdempotencyStartResult.IN_PROGRESS) {
                throw new TicketPurchaseRuleViolationException("付款請求處理中，請稍候再刷新。");
            }
            idempotencyStarted = (startResult == IdempotencyStartResult.NEW);
        }

        try {
            List<String> seatIds = jdbcTemplate.queryForList(
                    "SELECT seat_id FROM member_order_items WHERE order_id = ? ORDER BY seat_id",
                    String.class,
                    orderId);

            if (seatIds.isEmpty()) {
                throw new TicketPurchaseRuleViolationException("訂單沒有任何座位，無法付款。");
            }
            if (seatIds.size() > MAX_TICKETS_PER_ORDER) {
                throw new TicketPurchaseRuleViolationException("訂單座位數超過 4，無法付款。");
            }

            ShowtimeDetails details = movieService.getShowtimeDetails(movieId, showtimeId);
            if (!auditorium.equals(details.getShowtime().getAuditorium())) {
                throw new TicketPurchaseRuleViolationException("訂單影廳資訊不一致，請重新下單。");
            }

            enforcePerScreeningTicketCap(member.getId(), movieId, showtimeId, seatIds.size(), orderId);
            Instant showStartInstant = movieService.resolveShowStartInstant(movieId, showtimeId);
            Timestamp showStartAt = Timestamp.from(showStartInstant);
            Date showDate = Date.valueOf(showStartInstant.atZone(AppClock.zoneId()).toLocalDate());
            Set<String> heldSeats = new LinkedHashSet<>(jdbcTemplate.queryForList(
                    "SELECT seat_id FROM member_tickets WHERE order_id = ? ORDER BY seat_id",
                    String.class,
                    orderId));
            Set<String> expectedSeats = new LinkedHashSet<>(seatIds);
            if (heldSeats.isEmpty()) {
                // Legacy orders created before seat-hold rollout reserve seats at payment.
                assertSeatsAvailable(details, seatIds);
                reserveSeatsForOrder(orderId, member.getId(), movieId, showtimeId, auditorium, expectedSeats, showDate,
                        showStartAt);
            } else if (!heldSeats.equals(expectedSeats)) {
                throw new TicketPurchaseRuleViolationException("訂單座位鎖定資料異常，請取消後重新下單。");
            }

            PaymentMode effectiveMode = mode == null ? PaymentMode.SUCCESS : mode;
            long paymentTxId = insertPaymentTransaction(orderId, member.getId(), totalPrice, effectiveMode);
            PaymentGatewayResult gatewayResult = paymentGateway
                    .charge(new PaymentGatewayRequest(orderId, member.getId(), totalPrice, effectiveMode));
            completePaymentTransaction(paymentTxId, gatewayResult);

            if (gatewayResult.outcome() != PaymentOutcome.SUCCESS) {
                String reason = gatewayResult.outcome() == PaymentOutcome.TIMEOUT ? "PAYMENT_TIMEOUT" : "PAYMENT_FAILED";
                markOrderFailed(orderId, effectiveMode, gatewayResult.reference(), reason);
                finishPaymentIdempotency(member.getId(), orderId, safeIdempotencyKey, "FAILED");
                memberNotificationService.notifyPaymentFailed(member.getId(), orderId, gatewayResult.message());
                auditLogService.log("MEMBER", String.valueOf(member.getId()), "ORDER_PAY", "ORDER", String.valueOf(orderId),
                        "FAILED", "mode=" + effectiveMode + ",reason=" + reason);
                return getOrderDetail(member.getId(), orderId);
            }

            if (!OrderStatus.fromDb(String.valueOf(order.get("status"))).canTransitionTo(OrderStatus.PAID)) {
                throw new TicketPurchaseRuleViolationException("此訂單狀態無法轉為已付款。");
            }

            jdbcTemplate.update(
                    "UPDATE member_orders SET status = 'PAID', paid_at = NOW(), failed_at = NULL, expired_at = NULL, " +
                            "failure_reason = NULL, payment_reference = ?, payment_mode = ?, payment_attempts = payment_attempts + 1 " +
                            "WHERE id = ?",
                    gatewayResult.reference(), effectiveMode.name(), orderId);
            finishPaymentIdempotency(member.getId(), orderId, safeIdempotencyKey, "SUCCEEDED");
            memberLoyaltyService.awardPaidOrder(member.getId(), orderId, totalPrice, AppClock.nowInstant());

            memberNotificationService.notifyPaymentSuccess(member.getId(), orderId, gatewayResult.reference());
            auditLogService.log("MEMBER", String.valueOf(member.getId()), "ORDER_PAY", "ORDER", String.valueOf(orderId),
                    "SUCCESS", "mode=" + effectiveMode + ",amount=" + totalPrice);

            return getOrderDetail(member.getId(), orderId);
        } catch (RuntimeException ex) {
            if (idempotencyStarted) {
                finishPaymentIdempotency(member.getId(), orderId, safeIdempotencyKey, "FAILED");
            }
            throw ex;
        }
    }

    @Transactional
    public OrderDetailResponse cancelOrder(String memberUsername, long orderId) {
        User member = loadMember(memberUsername);
        Map<String, Object> order = getOrderRow(orderId);

        long ownerId = ((Number) order.get("member_id")).longValue();
        if (ownerId != member.getId()) {
            throw new TicketPurchaseRuleViolationException("無權限操作此訂單。");
        }

        OrderStatus status = OrderStatus.fromDb(String.valueOf(order.get("status")));
        if (!(status == OrderStatus.PENDING || status == OrderStatus.FAILED || status == OrderStatus.PAID)) {
            throw new TicketPurchaseRuleViolationException("此訂單目前狀態為 " + status + "，無法取消。");
        }

        if (status == OrderStatus.PAID) {
            Instant showStartAt = getOrderShowStartAt(
                    orderId,
                    String.valueOf(order.get("movie_id")),
                    String.valueOf(order.get("showtime_id")));
            if (showStartAt == null) {
                throw new TicketPurchaseRuleViolationException("找不到場次開演時間，無法取消。");
            }
            Instant cancelDeadline = showStartAt.minus(30, ChronoUnit.MINUTES);
            if (!AppClock.nowInstant().isBefore(cancelDeadline)) {
                throw new TicketPurchaseRuleViolationException("開演前 30 分鐘內與開演後不可取消訂單。");
            }
        }

        if (!status.canTransitionTo(OrderStatus.CANCELLED)) {
            throw new TicketPurchaseRuleViolationException("此訂單狀態不允許取消。");
        }

        jdbcTemplate.update(
                "UPDATE member_orders SET status = 'CANCELLED', cancelled_at = NOW(), failure_reason = NULL WHERE id = ?",
                orderId);
        int released = jdbcTemplate.update("DELETE FROM member_tickets WHERE order_id = ?", orderId);
        if (status == OrderStatus.PAID) {
            int totalPrice = ((Number) order.get("total_price")).intValue();
            memberLoyaltyService.rollbackPaidOrder(member.getId(), orderId, totalPrice, AppClock.nowInstant());
        }

        memberNotificationService.notifyOrderCancelled(member.getId(), orderId);
        auditLogService.log("MEMBER", String.valueOf(member.getId()), "ORDER_CANCEL", "ORDER", String.valueOf(orderId),
                "SUCCESS", "fromStatus=" + status + ",releasedSeats=" + released);

        return getOrderDetail(member.getId(), orderId);
    }

    public List<OrderSummaryResponse> listOrders(String memberUsername) {
        User member = loadMember(memberUsername);
        return listPaidOrderRows(member.getId()).stream()
                .map(PaidOrderRow::summary)
                .toList();
    }

    public List<OrderSummaryResponse> listAllOrders(String memberUsername, int limit) {
        User member = loadMember(memberUsername);
        int safeLimit = Math.max(1, Math.min(500, limit));
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT mo.id, mo.movie_id, mo.showtime_id, mo.auditorium, mo.total_qty, mo.total_price, mo.status, " +
                        "mo.created_at, mo.paid_at, " +
                        "MIN(mt.show_start_at) AS show_start_at, MIN(mt.show_date) AS show_date " +
                        "FROM member_orders mo " +
                        "LEFT JOIN member_tickets mt ON mt.order_id = mo.id " +
                        "WHERE mo.member_id = ? " +
                        "GROUP BY mo.id, mo.movie_id, mo.showtime_id, mo.auditorium, mo.total_qty, mo.total_price, mo.status, mo.created_at, mo.paid_at " +
                        "ORDER BY mo.created_at DESC LIMIT " + safeLimit,
                member.getId());

        return rows.stream().map(row -> {
            String movieId = String.valueOf(row.get("movie_id"));
            String showtimeId = String.valueOf(row.get("showtime_id"));
            Instant showStartAt = resolveShowStartInstant(movieId, showtimeId, row.get("show_start_at"), row.get("show_date"));
            if (showStartAt == null) {
                try {
                    showStartAt = movieService.resolveShowStartInstant(movieId, showtimeId);
                } catch (Exception ex) {
                    showStartAt = null;
                }
            }
            return new OrderSummaryResponse(
                    ((Number) row.get("id")).longValue(),
                    movieId,
                    showtimeId,
                    String.valueOf(row.get("auditorium")),
                    ((Number) row.get("total_qty")).intValue(),
                    ((Number) row.get("total_price")).intValue(),
                    String.valueOf(row.get("status")),
                    toInstant(row.get("created_at")),
                    toInstant(row.get("paid_at")),
                    showStartAt);
        }).toList();
    }

    public List<OrderSummaryResponse> listActiveOrders(String memberUsername, int limit) {
        User member = loadMember(memberUsername);
        int safeLimit = Math.max(1, Math.min(20, limit));
        Instant now = AppClock.nowInstant();
        List<PaidOrderRow> rows = listPaidOrderRows(member.getId());
        Map<String, Integer> durationByShowtime = resolveShowtimeDurations(rows);
        return rows.stream()
                .filter(row -> row.showStartAt() != null)
                .filter(row -> isUpcomingOrInProgress(row, now, durationByShowtime))
                .sorted(Comparator.comparing(PaidOrderRow::showStartAt))
                .limit(safeLimit)
                .map(PaidOrderRow::summary)
                .toList();
    }

    public List<OrderSummaryResponse> listHistoryOrders(String memberUsername, int limit) {
        User member = loadMember(memberUsername);
        int safeLimit = Math.max(1, Math.min(20, limit));
        Instant now = AppClock.nowInstant();
        List<PaidOrderRow> rows = listPaidOrderRows(member.getId());
        Map<String, Integer> durationByShowtime = resolveShowtimeDurations(rows);
        return rows.stream()
                .filter(row -> row.showStartAt() != null)
                .filter(row -> !isUpcomingOrInProgress(row, now, durationByShowtime))
                .sorted(Comparator.comparing(PaidOrderRow::showStartAt).reversed())
                .limit(safeLimit)
                .map(PaidOrderRow::summary)
                .toList();
    }

    public List<UpcomingBookingResponse> listUpcomingBookings(String memberUsername, int limit) {
        User member = loadMember(memberUsername);
        int safeLimit = Math.max(1, Math.min(20, limit));

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT COALESCE(mt.order_id, 0) AS order_id, mt.movie_id, mt.showtime_id, mt.auditorium, " +
                        "MIN(mt.show_start_at) AS show_start_at, MIN(mt.show_date) AS show_date, COUNT(*) AS qty " +
                        "FROM member_tickets mt " +
                        "LEFT JOIN member_orders mo ON mo.id = mt.order_id " +
                        "WHERE mt.member_id = ? " +
                        "AND (mt.order_id IS NULL OR mo.status = 'PAID') " +
                        "GROUP BY COALESCE(mt.order_id, 0), mt.movie_id, mt.showtime_id, mt.auditorium " +
                        "ORDER BY MIN(mt.show_start_at) ASC LIMIT 200",
                member.getId());
        Instant now = AppClock.nowInstant();

        return rows.stream().map(row -> {
            String movieId = String.valueOf(row.get("movie_id"));
            String movieTitle = movieService.getMovieWithAvailability(movieId)
                    .map(m -> m.getTitle())
                    .orElse(movieId);
            String showtimeId = String.valueOf(row.get("showtime_id"));
            Instant showStartAt = resolveShowStartInstant(
                    movieId,
                    showtimeId,
                    row.get("show_start_at"),
                    row.get("show_date"));
            return new UpcomingBookingResponse(
                    ((Number) row.get("order_id")).longValue(),
                    movieId,
                    movieTitle,
                    showtimeId,
                    String.valueOf(row.get("auditorium")),
                    ((Number) row.get("qty")).intValue(),
                    showStartAt,
                    formatShowStart(showStartAt));
        }).filter(row -> row.showStartAt() != null && row.showStartAt().isAfter(now))
                .sorted(Comparator.comparing(UpcomingBookingResponse::showStartAt))
                .limit(safeLimit)
                .toList();
    }

    public OrderDetailResponse getOrder(String memberUsername, long orderId) {
        User member = loadMember(memberUsername);
        return getOrderDetail(member.getId(), orderId);
    }

    @Scheduled(fixedDelayString = "${app.order.expire-check-ms:60000}")
    @Transactional
    public void expireTimedOutPendingOrders() {
        Instant threshold = AppClock.nowInstant().minus(pendingTimeoutMinutes, ChronoUnit.MINUTES);
        List<Map<String, Object>> rows;
        try {
            rows = jdbcTemplate.queryForList(
                    "SELECT id, member_id FROM member_orders WHERE status = 'PENDING' AND created_at < ?",
                    Timestamp.from(threshold));
        } catch (DataAccessException ex) {
            // Some test profiles intentionally disable DB schema initialization.
            log.debug("Skip pending-order expiration sweep: {}", ex.getMessage());
            return;
        }

        if (rows.isEmpty()) {
            return;
        }

        for (Map<String, Object> row : rows) {
            long orderId = ((Number) row.get("id")).longValue();
            long memberId = ((Number) row.get("member_id")).longValue();
            int updated = jdbcTemplate.update(
                    "UPDATE member_orders SET status = 'EXPIRED', expired_at = NOW(), failure_reason = 'PAYMENT_TIMEOUT' " +
                            "WHERE id = ? AND status = 'PENDING'",
                    orderId);
            if (updated > 0) {
                int released = jdbcTemplate.update("DELETE FROM member_tickets WHERE order_id = ?", orderId);
                memberNotificationService.notifyOrderExpired(memberId, orderId, pendingTimeoutMinutes);
                auditLogService.log("SYSTEM", "scheduler", "ORDER_EXPIRE", "ORDER", String.valueOf(orderId), "SUCCESS",
                        "pending-timeout-minutes=" + pendingTimeoutMinutes + ",released-seats=" + released);
            }
        }
        log.info("Expired {} pending order(s).", rows.size());
    }

    @Transactional
    public OrderRepairSummary repairOrderStatuses() {
        int pendingToPaid = jdbcTemplate.update(
                "UPDATE member_orders SET status = 'PAID' " +
                        "WHERE status IN ('PENDING', 'FAILED') AND paid_at IS NOT NULL");

        int paidToCancelled = jdbcTemplate.update(
                "UPDATE member_orders SET status = 'CANCELLED' " +
                        "WHERE status = 'PAID' AND cancelled_at IS NOT NULL");

        Instant threshold = AppClock.nowInstant().minus(pendingTimeoutMinutes, ChronoUnit.MINUTES);
        int pendingToExpired = jdbcTemplate.update(
                "UPDATE member_orders SET status = 'EXPIRED', expired_at = COALESCE(expired_at, NOW()), " +
                        "failure_reason = COALESCE(failure_reason, 'PAYMENT_TIMEOUT') " +
                        "WHERE status IN ('PENDING', 'FAILED') AND created_at < ?",
                Timestamp.from(threshold));

        int releasedTickets = jdbcTemplate.update(
                "DELETE FROM member_tickets " +
                        "WHERE order_id IN (SELECT id FROM member_orders WHERE status <> 'PAID')");

        return new OrderRepairSummary(
                pendingToPaid,
                paidToCancelled,
                pendingToExpired,
                releasedTickets);
    }

    private OrderDetailResponse getOrderDetail(long memberId, long orderId) {
        Map<String, Object> row = getOrderRow(orderId);
        long ownerId = ((Number) row.get("member_id")).longValue();
        if (ownerId != memberId) {
            throw new TicketPurchaseRuleViolationException("無權限查看此訂單。");
        }

        List<String> seats = jdbcTemplate.queryForList(
                "SELECT seat_id FROM member_order_items WHERE order_id = ? ORDER BY seat_id",
                String.class,
                orderId);
        Instant showStartAt = getOrderShowStartAt(
                orderId,
                String.valueOf(row.get("movie_id")),
                String.valueOf(row.get("showtime_id")));

        return new OrderDetailResponse(
                ((Number) row.get("id")).longValue(),
                String.valueOf(row.get("movie_id")),
                String.valueOf(row.get("showtime_id")),
                String.valueOf(row.get("auditorium")),
                ((Number) row.get("total_qty")).intValue(),
                ((Number) row.get("unit_price")).intValue(),
                ((Number) row.get("total_price")).intValue(),
                String.valueOf(row.get("status")),
                toInstant(row.get("created_at")),
                toInstant(row.get("paid_at")),
                showStartAt,
                toInstant(row.get("cancelled_at")),
                toInstant(row.get("failed_at")),
                toInstant(row.get("expired_at")),
                (String) row.get("failure_reason"),
                ((Number) row.get("payment_attempts")).intValue(),
                seats);
    }

    private Map<String, Object> getOrderRow(long orderId) {
        try {
            return jdbcTemplate.queryForMap(
                    "SELECT id, member_id, movie_id, showtime_id, auditorium, total_qty, unit_price, total_price, status, " +
                            "created_at, paid_at, cancelled_at, failed_at, expired_at, failure_reason, payment_attempts " +
                            "FROM member_orders WHERE id = ?",
                    orderId);
        } catch (EmptyResultDataAccessException ex) {
            throw new TicketPurchaseRuleViolationException("找不到訂單。");
        }
    }

    private void markOrderFailed(long orderId, PaymentMode mode, String reference, String reason) {
        jdbcTemplate.update(
                "UPDATE member_orders SET status = 'FAILED', failed_at = NOW(), failure_reason = ?, " +
                        "payment_reference = ?, payment_mode = ?, payment_attempts = payment_attempts + 1 " +
                        "WHERE id = ?",
                reason, reference, mode.name(), orderId);
    }

    private void markOrderExpired(long orderId, String reason) {
        int updated = jdbcTemplate.update(
                "UPDATE member_orders SET status = 'EXPIRED', expired_at = NOW(), failure_reason = ? " +
                        "WHERE id = ? AND status IN ('PENDING', 'FAILED')",
                reason, orderId);
        if (updated > 0) {
            jdbcTemplate.update("DELETE FROM member_tickets WHERE order_id = ?", orderId);
        }
    }

    private long insertPaymentTransaction(long orderId, long memberId, int amount, PaymentMode mode) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO payment_transactions (order_id, member_id, amount, mode, gateway, status) VALUES (?, ?, ?, ?, 'MOCK', 'REQUESTED')",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, orderId);
            ps.setLong(2, memberId);
            ps.setInt(3, amount);
            ps.setString(4, mode.name());
            return ps;
        }, keyHolder);
        return extractGeneratedId(keyHolder);
    }

    private void completePaymentTransaction(long txId, PaymentGatewayResult result) {
        if (txId <= 0 || result == null) {
            return;
        }
        String status = switch (result.outcome()) {
            case SUCCESS -> "SUCCESS";
            case FAILED -> "FAILED";
            case TIMEOUT -> "TIMEOUT";
        };
        jdbcTemplate.update(
                "UPDATE payment_transactions SET status = ?, reference = ?, error_message = ?, completed_at = NOW() WHERE id = ?",
                status, result.reference(), result.message(), txId);
    }

    private void assertSeatsAvailable(ShowtimeDetails details, Collection<String> requestedSeatIds) {
        Set<String> requested = requestedSeatIds.stream().collect(Collectors.toSet());
        Set<String> reserved = details.getSeatLayout().getSeats().stream()
                .filter(seat -> seat != null && seat.isReserved())
                .map(seat -> seat.getSeatId())
                .collect(Collectors.toSet());
        List<String> blocked = requested.stream().filter(reserved::contains).sorted().toList();
        if (!blocked.isEmpty()) {
            throw new TicketPurchaseConflictException("座位已被預訂：" + String.join(", ", blocked));
        }
    }

    private String normalizeIdempotencyKey(String key) {
        if (key == null) {
            return "";
        }
        String safe = key.trim();
        if (safe.isEmpty()) {
            return "";
        }
        return safe.length() > 120 ? safe.substring(0, 120) : safe;
    }

    private IdempotencyStartResult beginPaymentIdempotency(long memberId, long orderId, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || !isPaymentIdempotencyEnabled()) {
            return IdempotencyStartResult.NEW;
        }
        try {
            IdempotencyState existing = loadPaymentIdempotency(memberId, orderId, idempotencyKey);
            if (existing != null) {
                if ("SUCCEEDED".equalsIgnoreCase(existing.status())) {
                    return IdempotencyStartResult.SUCCEEDED;
                }
                Instant staleBefore = AppClock.nowInstant().minus(2, ChronoUnit.MINUTES);
                if ("IN_PROGRESS".equalsIgnoreCase(existing.status())
                        && existing.updatedAt() != null
                        && existing.updatedAt().isAfter(staleBefore)) {
                    return IdempotencyStartResult.IN_PROGRESS;
                }
                jdbcTemplate.update(
                        "UPDATE payment_idempotency SET status = 'IN_PROGRESS', updated_at = NOW() " +
                                "WHERE member_id = ? AND order_id = ? AND idempotency_key = ?",
                        memberId,
                        orderId,
                        idempotencyKey);
                return IdempotencyStartResult.NEW;
            }
            jdbcTemplate.update(
                    "INSERT INTO payment_idempotency (member_id, order_id, idempotency_key, status) VALUES (?, ?, ?, 'IN_PROGRESS')",
                    memberId,
                    orderId,
                    idempotencyKey);
            return IdempotencyStartResult.NEW;
        } catch (DataAccessException ex) {
            // Duplicate writes can happen under race; reload current state once.
            try {
                IdempotencyState reloaded = loadPaymentIdempotency(memberId, orderId, idempotencyKey);
                if (reloaded != null && "SUCCEEDED".equalsIgnoreCase(reloaded.status())) {
                    return IdempotencyStartResult.SUCCEEDED;
                }
                if (reloaded != null && "IN_PROGRESS".equalsIgnoreCase(reloaded.status())) {
                    return IdempotencyStartResult.IN_PROGRESS;
                }
            } catch (DataAccessException ignored) {
                paymentIdempotencyEnabled = Boolean.FALSE;
            }
            return IdempotencyStartResult.NEW;
        }
    }

    private void finishPaymentIdempotency(long memberId, long orderId, String idempotencyKey, String status) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || !isPaymentIdempotencyEnabled()) {
            return;
        }
        try {
            jdbcTemplate.update(
                    "UPDATE payment_idempotency SET status = ?, updated_at = NOW() " +
                            "WHERE member_id = ? AND order_id = ? AND idempotency_key = ?",
                    status,
                    memberId,
                    orderId,
                    idempotencyKey);
        } catch (DataAccessException ex) {
            paymentIdempotencyEnabled = Boolean.FALSE;
        }
    }

    private boolean hasSucceededPaymentIdempotency(long memberId, long orderId, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || !isPaymentIdempotencyEnabled()) {
            return false;
        }
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM payment_idempotency " +
                            "WHERE member_id = ? AND order_id = ? AND idempotency_key = ? AND status = 'SUCCEEDED'",
                    Integer.class,
                    memberId,
                    orderId,
                    idempotencyKey);
            return count != null && count.intValue() > 0;
        } catch (DataAccessException ex) {
            paymentIdempotencyEnabled = Boolean.FALSE;
            return false;
        }
    }

    private IdempotencyState loadPaymentIdempotency(long memberId, long orderId, String idempotencyKey) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT status, updated_at FROM payment_idempotency " +
                        "WHERE member_id = ? AND order_id = ? AND idempotency_key = ?",
                memberId,
                orderId,
                idempotencyKey);
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> row = rows.get(0);
        return new IdempotencyState(
                String.valueOf(row.get("status")),
                toInstant(row.get("updated_at")));
    }

    private boolean isPaymentIdempotencyEnabled() {
        Boolean cached = paymentIdempotencyEnabled;
        if (Boolean.TRUE.equals(cached)) {
            return cached.booleanValue();
        }
        try {
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payment_idempotency WHERE 1 = 0", Integer.class);
            paymentIdempotencyEnabled = Boolean.TRUE;
        } catch (DataAccessException ex) {
            // Keep probing in later calls because some tests create schema lazily.
            paymentIdempotencyEnabled = Boolean.FALSE;
        }
        return paymentIdempotencyEnabled.booleanValue();
    }

    private static long extractGeneratedId(KeyHolder keyHolder) {
        if (keyHolder == null) {
            return 0L;
        }
        try {
            Number key = keyHolder.getKey();
            if (key != null) {
                return key.longValue();
            }
        } catch (Exception ignored) {
        }
        Map<String, Object> keys = keyHolder.getKeys();
        if (keys == null || keys.isEmpty()) {
            return 0L;
        }
        Object key = keys.getOrDefault("id", keys.get("ID"));
        if (key instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }

    private User loadMember(String memberUsername) {
        return userDao.findMemberByUsername(memberUsername)
                .orElseThrow(() -> new TicketPurchaseRuleViolationException("找不到會員帳號。"));
    }

    private boolean isPastPendingTimeout(Instant createdAt) {
        if (createdAt == null) {
            return false;
        }
        return createdAt.plus(pendingTimeoutMinutes, ChronoUnit.MINUTES).isBefore(AppClock.nowInstant());
    }

    private List<PaidOrderRow> listPaidOrderRows(long memberId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT mo.id, mo.movie_id, mo.showtime_id, mo.auditorium, mo.total_qty, mo.total_price, mo.status, " +
                        "mo.created_at, mo.paid_at, MIN(mt.show_start_at) AS show_start_at, MIN(mt.show_date) AS show_date " +
                        "FROM member_orders mo " +
                        "LEFT JOIN member_tickets mt ON mt.order_id = mo.id " +
                        "WHERE mo.member_id = ? AND mo.status = 'PAID' " +
                        "GROUP BY mo.id, mo.movie_id, mo.showtime_id, mo.auditorium, mo.total_qty, mo.total_price, mo.status, mo.created_at, mo.paid_at " +
                        "ORDER BY mo.created_at DESC LIMIT 200",
                memberId);
        return rows.stream()
                .map(this::mapPaidOrderRow)
                .toList();
    }

    private PaidOrderRow mapPaidOrderRow(Map<String, Object> row) {
        String movieId = String.valueOf(row.get("movie_id"));
        String showtimeId = String.valueOf(row.get("showtime_id"));
        Instant showStartAt = resolveShowStartInstant(movieId, showtimeId, row.get("show_start_at"), row.get("show_date"));
        return new PaidOrderRow(
                new OrderSummaryResponse(
                        ((Number) row.get("id")).longValue(),
                        movieId,
                        showtimeId,
                        String.valueOf(row.get("auditorium")),
                        ((Number) row.get("total_qty")).intValue(),
                        ((Number) row.get("total_price")).intValue(),
                        String.valueOf(row.get("status")),
                        toInstant(row.get("created_at")),
                        toInstant(row.get("paid_at")),
                        showStartAt),
                showStartAt);
    }

    private boolean isUpcomingOrInProgress(PaidOrderRow row, Instant now, Map<String, Integer> durationByShowtime) {
        Instant showStart = row.showStartAt();
        if (showStart == null) {
            return false;
        }
        int durationMinutes = durationByShowtime.getOrDefault(
                showtimeKey(row.summary().movieId(), row.summary().showtimeId()),
                0);
        Instant showEnd = showStart.plus(Math.max(0, durationMinutes), ChronoUnit.MINUTES);
        return showEnd.isAfter(now);
    }

    private Map<String, Integer> resolveShowtimeDurations(List<PaidOrderRow> rows) {
        Map<String, Integer> map = new HashMap<>();
        for (PaidOrderRow row : rows) {
            String movieId = row.summary().movieId();
            String showtimeId = row.summary().showtimeId();
            String key = showtimeKey(movieId, showtimeId);
            if (map.containsKey(key)) {
                continue;
            }
            int duration = movieService.getShowtime(movieId, showtimeId)
                    .map(showtime -> showtime.getDurationMinutes())
                    .orElse(0);
            map.put(key, duration);
        }
        return map;
    }

    private static String showtimeKey(String movieId, String showtimeId) {
        return movieId + "|" + showtimeId;
    }

    private String formatShowStart(Instant showStartAt) {
        if (showStartAt == null) {
            return "";
        }
        return SHOW_START_FORMATTER.format(showStartAt);
    }

    private static Instant toInstant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof Timestamp ts) {
            return ts.toInstant();
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.atZone(AppClock.zoneId()).toInstant();
        }
        if (value instanceof java.util.Date date) {
            return date.toInstant();
        }
        return null;
    }

    private static LocalDate toLocalDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof Date date) {
            return date.toLocalDate();
        }
        if (value instanceof Timestamp ts) {
            return ts.toInstant().atZone(AppClock.zoneId()).toLocalDate();
        }
        if (value instanceof java.util.Date date) {
            return date.toInstant().atZone(AppClock.zoneId()).toLocalDate();
        }
        try {
            return LocalDate.parse(String.valueOf(value));
        } catch (Exception ex) {
            return null;
        }
    }

    private Instant getOrderShowStartAt(long orderId, String movieId, String showtimeId) {
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT MIN(show_start_at) AS show_start_at, MIN(show_date) AS show_date FROM member_tickets WHERE order_id = ?",
                orderId);
        return resolveShowStartInstant(movieId, showtimeId, row.get("show_start_at"), row.get("show_date"));
    }

    private Instant resolveShowStartInstant(String movieId, String showtimeId, Object rawShowStartAt, Object rawShowDate) {
        Instant direct = toInstant(rawShowStartAt);
        LocalDate showDate = toLocalDate(rawShowDate);

        // Prefer show_date + configured showtime start.
        // This avoids DATETIME timezone conversion drift between DB/driver/app zones.
        if (showDate != null) {
            LocalTime start = movieService.getShowtime(movieId, showtimeId)
                    .map(showtime -> parseShowtimeStart(showtime.getStartTime()))
                    .orElse(null);
            if (start != null) {
                return ZonedDateTime.of(showDate, start, AppClock.zoneId()).toInstant();
            }
        }
        return direct;
    }

    private static LocalTime parseShowtimeStart(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(value.trim());
        } catch (Exception ex) {
            return null;
        }
    }

    private static Set<String> normalizeSeatIds(Collection<String> seatIds) {
        if (seatIds == null || seatIds.isEmpty()) {
            throw new TicketPurchaseRuleViolationException("請至少選擇 1 個座位。");
        }
        Set<String> requested = seatIds.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (requested.isEmpty()) {
            throw new TicketPurchaseRuleViolationException("請至少選擇 1 個座位。");
        }
        return requested;
    }

    private void enforcePerScreeningTicketCap(long memberId, String movieId, String showtimeId, int requestedCount) {
        enforcePerScreeningTicketCap(memberId, movieId, showtimeId, requestedCount, null);
    }

    private void enforcePerScreeningTicketCap(long memberId, String movieId, String showtimeId, int requestedCount,
            Long excludedOrderId) {
        Instant showStartAt = movieService.resolveShowStartInstant(movieId, showtimeId);
        Timestamp holdThreshold = Timestamp.from(AppClock.nowInstant().minus(pendingTimeoutMinutes, ChronoUnit.MINUTES));
        Integer existingCount;
        if (excludedOrderId == null) {
            existingCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) " +
                            "FROM member_tickets mt " +
                            "LEFT JOIN member_orders mo ON mo.id = mt.order_id " +
                            "WHERE mt.member_id = ? AND mt.movie_id = ? AND mt.showtime_id = ? AND mt.show_start_at = ? " +
                            "AND (mt.order_id IS NULL OR mo.status = 'PAID' OR (mo.status IN ('PENDING', 'FAILED') AND mo.created_at >= ?))",
                    Integer.class,
                    memberId,
                    movieId,
                    showtimeId,
                    Timestamp.from(showStartAt),
                    holdThreshold);
        } else {
            existingCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) " +
                            "FROM member_tickets mt " +
                            "LEFT JOIN member_orders mo ON mo.id = mt.order_id " +
                            "WHERE mt.member_id = ? AND mt.movie_id = ? AND mt.showtime_id = ? AND mt.show_start_at = ? " +
                            "AND (mt.order_id IS NULL OR mo.status = 'PAID' OR (mo.status IN ('PENDING', 'FAILED') AND mo.created_at >= ?)) " +
                            "AND (mt.order_id IS NULL OR mt.order_id <> ?)",
                    Integer.class,
                    memberId,
                    movieId,
                    showtimeId,
                    Timestamp.from(showStartAt),
                    holdThreshold,
                    excludedOrderId);
        }
        int existing = existingCount == null ? 0 : existingCount.intValue();
        if (existing + requestedCount > MAX_TICKETS_PER_SCREENING_PER_MEMBER) {
            int remaining = Math.max(0, MAX_TICKETS_PER_SCREENING_PER_MEMBER - existing);
            throw new TicketPurchaseRuleViolationException(
                    "同一場次同一會員最多只能持有 4 張票（你已持有 " + existing + " 張，剩餘可買 " + remaining + " 張）。");
        }
    }

    private void reserveSeatsForOrder(
            long orderId,
            long memberId,
            String movieId,
            String showtimeId,
            String auditorium,
            Collection<String> seatIds,
            Date showDate,
            Timestamp showStartAt) {
        for (String seatId : seatIds) {
            try {
                jdbcTemplate.update(
                        "INSERT INTO member_tickets (order_id, member_id, movie_id, showtime_id, show_date, show_start_at, auditorium, seat_id) " +
                                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                        orderId, memberId, movieId, showtimeId, showDate, showStartAt, auditorium, seatId);
            } catch (DuplicateKeyException ex) {
                throw new TicketPurchaseConflictException("座位已被其他人搶先購買：" + seatId, ex);
            }
        }
    }

    private record PaidOrderRow(OrderSummaryResponse summary, Instant showStartAt) {
    }

    private record IdempotencyState(String status, Instant updatedAt) {
    }

    private enum IdempotencyStartResult {
        NEW,
        IN_PROGRESS,
        SUCCEEDED
    }

    public record OrderRepairSummary(
            int pendingToPaid,
            int paidToCancelled,
            int pendingToExpired,
            int releasedTickets) {
    }
}

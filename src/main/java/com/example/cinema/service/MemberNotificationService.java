package com.example.cinema.service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.example.cinema.config.AppClock;
import com.example.cinema.dao.UserDao;
import com.example.cinema.dto.MemberNotificationResponse;
import com.example.cinema.exception.TicketPurchaseRuleViolationException;
import com.example.cinema.model.User;

@Service
public class MemberNotificationService {

    private static final Logger log = LoggerFactory.getLogger(MemberNotificationService.class);

    private final JdbcTemplate jdbcTemplate;
    private final UserDao userDao;
    private final NotificationSender notificationSender;
    private final int retentionDays;

    public MemberNotificationService(
            JdbcTemplate jdbcTemplate,
            UserDao userDao,
            NotificationSender notificationSender,
            @Value("${app.notification.retention-days:7}") int retentionDays) {
        this.jdbcTemplate = jdbcTemplate;
        this.userDao = userDao;
        this.notificationSender = notificationSender;
        this.retentionDays = Math.max(1, retentionDays);
    }

    public void notifyOrderCreated(long memberId, long orderId, int totalPrice) {
        notificationSender.send(new NotificationCommand(
                memberId,
                "ORDER",
                "訂單已建立",
                "你的訂單 #" + orderId + " 已建立，待付款金額 $" + totalPrice + "。"));
    }

    public void notifyPaymentSuccess(long memberId, long orderId, String reference) {
        notificationSender.send(new NotificationCommand(
                memberId,
                "PAYMENT",
                "付款成功",
                "訂單 #" + orderId + " 付款成功，交易編號：" + nonEmpty(reference, "N/A") + "。"));
    }

    public void notifyPaymentFailed(long memberId, long orderId, String reason) {
        notificationSender.send(new NotificationCommand(
                memberId,
                "PAYMENT",
                "付款失敗",
                "訂單 #" + orderId + " 付款失敗：" + nonEmpty(reason, "請稍後重試。")));
    }

    public void notifyOrderExpired(long memberId, long orderId, int timeoutMinutes) {
        notificationSender.send(new NotificationCommand(
                memberId,
                "ORDER",
                "訂單逾時",
                "訂單 #" + orderId + " 因超過 " + timeoutMinutes + " 分鐘未付款，已自動失效。"));
    }

    public void notifyOrderCancelled(long memberId, long orderId) {
        notificationSender.send(new NotificationCommand(
                memberId,
                "ORDER",
                "訂單已取消",
                "訂單 #" + orderId + " 已取消。"));
    }

    public List<MemberNotificationResponse> listForMember(String memberUsername, int limit) {
        User member = userDao.findMemberByUsername(memberUsername)
                .orElseThrow(() -> new TicketPurchaseRuleViolationException("找不到會員帳號。"));

        int safeLimit = Math.max(1, Math.min(100, limit));
        Instant cutoff = notificationCutoff();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, category, title, message, created_at, read_at FROM notifications " +
                        "WHERE member_id = ? AND created_at >= ? " +
                        "ORDER BY created_at DESC LIMIT " + safeLimit,
                member.getId(),
                Timestamp.from(cutoff));

        return rows.stream().map(this::toDto).toList();
    }

    public void markRead(String memberUsername, long notificationId) {
        User member = userDao.findMemberByUsername(memberUsername)
                .orElseThrow(() -> new TicketPurchaseRuleViolationException("找不到會員帳號。"));

        jdbcTemplate.update(
                "UPDATE notifications SET read_at = NOW() WHERE id = ? AND member_id = ? AND read_at IS NULL",
                notificationId, member.getId());
    }

    @Scheduled(fixedDelayString = "${app.notification.cleanup-ms:3600000}")
    public void purgeExpiredNotifications() {
        Instant cutoff = notificationCutoff();
        try {
            int deleted = jdbcTemplate.update(
                    "DELETE FROM notifications WHERE created_at < ?",
                    Timestamp.from(cutoff));
            if (deleted > 0) {
                log.info("Purged {} expired notifications older than {} day(s).", deleted, retentionDays);
            }
        } catch (DataAccessException ex) {
            // Some test profiles intentionally do not initialize DB schema.
            log.debug("Skip notification cleanup sweep: {}", ex.getMessage());
        }
    }

    private MemberNotificationResponse toDto(Map<String, Object> row) {
        Instant readAt = toInstant(row.get("read_at"));
        return new MemberNotificationResponse(
                ((Number) row.get("id")).longValue(),
                String.valueOf(row.get("category")),
                String.valueOf(row.get("title")),
                String.valueOf(row.get("message")),
                readAt != null,
                toInstant(row.get("created_at")),
                readAt);
    }

    private static Instant toInstant(Object value) {
        if (value instanceof Timestamp ts) {
            return ts.toInstant();
        }
        if (value instanceof java.util.Date date) {
            return date.toInstant();
        }
        return null;
    }

    private static String nonEmpty(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    private Instant notificationCutoff() {
        return AppClock.nowInstant().minus(retentionDays, ChronoUnit.DAYS);
    }
}

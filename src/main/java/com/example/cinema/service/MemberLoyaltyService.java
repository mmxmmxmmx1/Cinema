package com.example.cinema.service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.cinema.config.AppClock;
import com.example.cinema.dao.UserDao;
import com.example.cinema.dto.MemberPointLog;
import com.example.cinema.exception.TicketPurchaseRuleViolationException;
import com.example.cinema.model.User;

@Service
public class MemberLoyaltyService {

    private static final int POINTS_PER_NTD = 10;
    private static final List<RewardOption> REWARD_OPTIONS = List.of(
            new RewardOption("POPCORN_S", "小爆米花兌換券", 50),
            new RewardOption("COMBO_M", "中爆米花+飲料", 120),
            new RewardOption("TICKET_DISCOUNT", "電影票折抵 50 元", 200));

    private final JdbcTemplate jdbcTemplate;
    private final UserDao userDao;
    private volatile Boolean pointLedgerEnabled;

    public MemberLoyaltyService(JdbcTemplate jdbcTemplate, UserDao userDao) {
        this.jdbcTemplate = jdbcTemplate;
        this.userDao = userDao;
    }

    public int currentPoints(String memberUsername) {
        User member = loadMember(memberUsername);
        if (!isPointLedgerEnabled()) {
            return legacyCurrentPoints(member.getId());
        }
        Integer balance = jdbcTemplate.queryForObject(
                "SELECT points_balance FROM member_point_balance WHERE member_id = ?",
                Integer.class,
                member.getId());
        return Math.max(0, balance == null ? 0 : balance.intValue());
    }

    public List<MemberPointLog> recentPointLogs(String memberUsername, int limit) {
        User member = loadMember(memberUsername);
        int safeLimit = Math.max(1, Math.min(50, limit));
        List<Map<String, Object>> rows = List.of();
        if (isPointLedgerEnabled()) {
            rows = jdbcTemplate.queryForList(
                    "SELECT COALESCE(ref_order_id, ref_redemption_id, 0) AS ref_id, amount, points_delta AS points, happened_at, description " +
                            "FROM member_point_ledger " +
                            "WHERE member_id = ? " +
                            "ORDER BY happened_at DESC, id DESC LIMIT ?",
                    member.getId(),
                    safeLimit);
        }
        if (rows.isEmpty()) {
            rows = legacyPointLogs(member.getId(), safeLimit);
        }
        return rows.stream().map(this::mapPointLog).toList();
    }

    public List<RewardOption> listRewardOptions() {
        return REWARD_OPTIONS;
    }

    @Transactional
    public int redeem(String memberUsername, String rewardCode) {
        User member = loadMember(memberUsername);
        RewardOption reward = REWARD_OPTIONS.stream()
                .filter(item -> item.code().equalsIgnoreCase(rewardCode == null ? "" : rewardCode.trim()))
                .findFirst()
                .orElseThrow(() -> new TicketPurchaseRuleViolationException("找不到該兌換項目。"));

        int available = currentPoints(memberUsername);
        if (available < reward.pointsCost()) {
            throw new TicketPurchaseRuleViolationException("點數不足，無法兌換「" + reward.name() + "」。");
        }

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            var ps = con.prepareStatement(
                    "INSERT INTO member_point_redemptions (member_id, reward_code, reward_name, points_spent, note) VALUES (?, ?, ?, ?, ?)",
                    new String[] { "id" });
            ps.setLong(1, member.getId());
            ps.setString(2, reward.code());
            ps.setString(3, reward.name());
            ps.setInt(4, reward.pointsCost());
            ps.setString(5, "會員手動兌換");
            return ps;
        }, keyHolder);

        long redemptionId = extractGeneratedId(keyHolder);
        if (isPointLedgerEnabled() && redemptionId > 0) {
            applyRedeemLedger(member.getId(), redemptionId, reward);
            Integer updated = jdbcTemplate.queryForObject(
                    "SELECT points_balance FROM member_point_balance WHERE member_id = ?",
                    Integer.class,
                    member.getId());
            return Math.max(0, updated == null ? 0 : updated.intValue());
        }
        return Math.max(0, available - reward.pointsCost());
    }

    @Transactional
    public void awardPaidOrder(long memberId, long orderId, int totalAmount, Instant paidAt) {
        if (!isPointLedgerEnabled() || memberId <= 0 || orderId <= 0 || totalAmount <= 0) {
            return;
        }
        int points = Math.max(0, totalAmount / POINTS_PER_NTD);
        if (points <= 0) {
            return;
        }
        String eventKey = "ORDER_PAID:" + orderId;
        int inserted = jdbcTemplate.update(
                "INSERT IGNORE INTO member_point_ledger " +
                        "(member_id, event_key, event_type, ref_order_id, amount, points_delta, description, happened_at) " +
                        "VALUES (?, ?, 'ORDER_PAID', ?, ?, ?, ?, ?)",
                memberId,
                eventKey,
                orderId,
                totalAmount,
                points,
                "訂單 #" + orderId + " 付款回饋",
                toTimestamp(paidAt));
        if (inserted > 0) {
            incrementPointBalance(memberId, points);
        }
    }

    @Transactional
    public void rollbackPaidOrder(long memberId, long orderId, int totalAmount, Instant happenedAt) {
        if (!isPointLedgerEnabled() || memberId <= 0 || orderId <= 0 || totalAmount <= 0) {
            return;
        }
        int points = Math.max(0, totalAmount / POINTS_PER_NTD);
        if (points <= 0) {
            return;
        }

        Integer paidLogCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM member_point_ledger WHERE member_id = ? AND event_key = ?",
                Integer.class,
                memberId,
                "ORDER_PAID:" + orderId);
        if (paidLogCount == null || paidLogCount.intValue() <= 0) {
            return;
        }

        String eventKey = "ORDER_CANCELLED:" + orderId;
        int inserted = jdbcTemplate.update(
                "INSERT IGNORE INTO member_point_ledger " +
                        "(member_id, event_key, event_type, ref_order_id, amount, points_delta, description, happened_at) " +
                        "VALUES (?, ?, 'ORDER_CANCELLED', ?, ?, ?, ?, ?)",
                memberId,
                eventKey,
                orderId,
                -totalAmount,
                -points,
                "訂單 #" + orderId + " 取消，回滾點數",
                toTimestamp(happenedAt));
        if (inserted > 0) {
            incrementPointBalance(memberId, -points);
        }
    }

    private MemberPointLog mapPointLog(Map<String, Object> row) {
        long orderId = ((Number) row.get("ref_id")).longValue();
        int amount = ((Number) row.get("amount")).intValue();
        int points = ((Number) row.get("points")).intValue();
        return new MemberPointLog(
                orderId,
                amount,
                points,
                toInstant(row.get("happened_at")),
                String.valueOf(row.get("description")));
    }

    private User loadMember(String memberUsername) {
        return userDao.findMemberByUsername(memberUsername)
                .orElseThrow(() -> new TicketPurchaseRuleViolationException("找不到會員帳號。"));
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

    private int legacyCurrentPoints(long memberId) {
        Integer totalAmount = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(total_price), 0) FROM member_orders WHERE member_id = ? AND status = 'PAID'",
                Integer.class,
                memberId);
        Integer spent;
        try {
            spent = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(SUM(points_spent), 0) FROM member_point_redemptions WHERE member_id = ?",
                    Integer.class,
                    memberId);
        } catch (DataAccessException ex) {
            spent = 0;
        }
        int earnedPoints = (totalAmount == null ? 0 : totalAmount.intValue()) / POINTS_PER_NTD;
        int spentPoints = spent == null ? 0 : spent.intValue();
        return Math.max(0, earnedPoints - spentPoints);
    }

    private List<Map<String, Object>> legacyPointLogs(long memberId, int safeLimit) {
        return jdbcTemplate.queryForList(
                "SELECT ref_id, amount, points, happened_at, description FROM (" +
                        " SELECT mo.id AS ref_id, mo.total_price AS amount, FLOOR(mo.total_price / 10) AS points, " +
                        "        mo.paid_at AS happened_at, CONCAT('訂單 #', mo.id, ' 付款回饋') AS description " +
                        " FROM member_orders mo " +
                        " WHERE mo.member_id = ? AND mo.status = 'PAID' AND mo.paid_at IS NOT NULL " +
                        " UNION ALL " +
                        " SELECT pr.id AS ref_id, 0 AS amount, (0 - pr.points_spent) AS points, " +
                        "        pr.created_at AS happened_at, CONCAT('點數兌換：', pr.reward_name) AS description " +
                        " FROM member_point_redemptions pr WHERE pr.member_id = ? " +
                        ") logs ORDER BY happened_at DESC LIMIT ?",
                memberId,
                memberId,
                safeLimit);
    }

    private void applyRedeemLedger(long memberId, long redemptionId, RewardOption reward) {
        String eventKey = "REDEEM:" + redemptionId;
        int inserted = jdbcTemplate.update(
                "INSERT IGNORE INTO member_point_ledger " +
                        "(member_id, event_key, event_type, ref_redemption_id, amount, points_delta, description, happened_at) " +
                        "VALUES (?, ?, 'REDEEM', ?, 0, ?, ?, NOW())",
                memberId,
                eventKey,
                redemptionId,
                -reward.pointsCost(),
                "點數兌換：" + reward.name());
        if (inserted > 0) {
            incrementPointBalance(memberId, -reward.pointsCost());
        }
    }

    private void incrementPointBalance(long memberId, int delta) {
        jdbcTemplate.update(
                "INSERT INTO member_point_balance (member_id, points_balance) VALUES (?, ?) " +
                        "ON DUPLICATE KEY UPDATE points_balance = points_balance + VALUES(points_balance)",
                memberId,
                delta);
    }

    private boolean isPointLedgerEnabled() {
        Boolean cached = pointLedgerEnabled;
        if (Boolean.TRUE.equals(cached)) {
            return cached.booleanValue();
        }
        try {
            jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM member_point_balance WHERE 1 = 0",
                    Integer.class);
            jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM member_point_ledger WHERE 1 = 0",
                    Integer.class);
            pointLedgerEnabled = Boolean.TRUE;
        } catch (DataAccessException ex) {
            // Keep probing in later calls because some tests create schema lazily.
            pointLedgerEnabled = Boolean.FALSE;
        }
        return pointLedgerEnabled.booleanValue();
    }

    private static Timestamp toTimestamp(Instant instant) {
        if (instant == null) {
            return Timestamp.from(AppClock.nowInstant());
        }
        return Timestamp.from(instant);
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

    public record RewardOption(String code, String name, int pointsCost) {
    }
}

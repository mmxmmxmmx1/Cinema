package com.example.cinema.service;

import java.time.Instant;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.cinema.config.AppClock;
import com.example.cinema.dao.UserDao;
import com.example.cinema.exception.TicketPurchaseConflictException;
import com.example.cinema.exception.TicketPurchaseRuleViolationException;
import com.example.cinema.model.Showtime;
import com.example.cinema.model.ShowtimeDetails;
import com.example.cinema.model.User;

@Service
public class TicketPurchaseService {

    private static final int MAX_TICKETS_PER_ORDER = 4;
    private static final int MAX_TICKETS_PER_ACTIVE_WINDOW = 4;
    private static final int DEFAULT_SHOW_DURATION_MINUTES = 120;

    private final JdbcTemplate jdbcTemplate;
    private final UserDao userDao;
    private final MovieService movieService;

    public TicketPurchaseService(JdbcTemplate jdbcTemplate, UserDao userDao, MovieService movieService) {
        this.jdbcTemplate = jdbcTemplate;
        this.userDao = userDao;
        this.movieService = movieService;
    }

    @Transactional
    public PurchaseResult purchase(String memberUsername, String movieId, String showtimeId, Collection<String> seatIds) {
        if (seatIds == null || seatIds.isEmpty()) {
            throw new TicketPurchaseRuleViolationException("請至少選擇 1 個座位。");
        }

        // De-dup while keeping order stable for UX.
        Set<String> requested = seatIds.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (requested.isEmpty()) {
            throw new TicketPurchaseRuleViolationException("請至少選擇 1 個座位。");
        }
        if (requested.size() > MAX_TICKETS_PER_ORDER) {
            throw new TicketPurchaseRuleViolationException("一次最多只能買 4 張票。");
        }

        User member = userDao.findMemberByUsername(memberUsername)
                .orElseThrow(() -> new TicketPurchaseRuleViolationException("找不到會員帳號。"));
        if (!movieService.isBookingWindowOpenNow()) {
            throw new TicketPurchaseRuleViolationException("目前非訂票時段（每日 07:00 - 22:45）。");
        }
        if (!movieService.isShowtimeOpenForPurchase(movieId, showtimeId)) {
            throw new TicketPurchaseRuleViolationException("此場次已開演或已結束，請選擇其他場次。");
        }

        ShowtimeDetails details = movieService.getShowtimeDetails(movieId, showtimeId);
        String auditorium = details.getShowtime().getAuditorium();

        // Block seats that are already marked reserved by the showtime details (includes simulated occupancy).
        Set<String> reservedSeats = details.getSeatLayout().getSeats().stream()
                .filter(seat -> seat != null && seat.isReserved())
                .map(seat -> seat.getSeatId())
                .collect(Collectors.toSet());
        List<String> blocked = requested.stream().filter(reservedSeats::contains).toList();
        if (!blocked.isEmpty()) {
            throw new TicketPurchaseConflictException("座位已被預訂：" + String.join(", ", blocked));
        }

        enforceActiveShowtimeSingleAuditoriumRule(member.getId(), auditorium, requested.size());

        Instant purchasedAt = AppClock.nowInstant();
        Date operationalDate = Date.valueOf(movieService.currentOperationalDate());
        Timestamp showStartAt = Timestamp.from(movieService.resolveShowStartInstant(movieId, showtimeId));
        for (String seatId : requested) {
            try {
                jdbcTemplate.update(
                        "INSERT INTO member_tickets (member_id, movie_id, showtime_id, show_date, show_start_at, auditorium, seat_id) VALUES (?, ?, ?, ?, ?, ?, ?)",
                        member.getId(), movieId, showtimeId, operationalDate, showStartAt, auditorium, seatId);
            } catch (DuplicateKeyException ex) {
                // Unique constraint: (show_start_at, showtime_id, seat_id)
                throw new TicketPurchaseConflictException("座位已被其他人搶先購買：" + seatId, ex);
            }
        }

        return new PurchaseResult(movieId, showtimeId, auditorium, List.copyOf(requested), purchasedAt);
    }

    private void enforceActiveShowtimeSingleAuditoriumRule(long memberId, String requestedAuditorium, int requestedCount) {
        List<Map<String, Object>> grouped = jdbcTemplate.queryForList(
                "SELECT movie_id, showtime_id, auditorium, COUNT(*) AS cnt, MIN(show_start_at) AS show_start_at " +
                        "FROM member_tickets " +
                        "WHERE member_id = ? " +
                        "GROUP BY movie_id, showtime_id, auditorium",
                memberId);

        Instant now = AppClock.nowInstant();
        Map<String, Integer> activeByAuditorium = new HashMap<>();
        int activeCount = 0;

        for (Map<String, Object> row : grouped) {
            Instant showStartAt = toInstant(row.get("show_start_at"));
            if (showStartAt == null) {
                continue;
            }
            String movieId = String.valueOf(row.get("movie_id"));
            String showtimeId = String.valueOf(row.get("showtime_id"));
            int durationMinutes = movieService.getShowtime(movieId, showtimeId)
                    .map(Showtime::getDurationMinutes)
                    .map(duration -> Math.max(1, duration))
                    .orElse(DEFAULT_SHOW_DURATION_MINUTES);
            Instant showEndAt = showStartAt.plus(durationMinutes, ChronoUnit.MINUTES);
            if (!showEndAt.isAfter(now)) {
                continue;
            }
            int count = ((Number) row.get("cnt")).intValue();
            String auditorium = String.valueOf(row.get("auditorium"));
            activeByAuditorium.merge(auditorium, count, Integer::sum);
            activeCount += count;
        }

        if (activeByAuditorium.size() > 1) {
            throw new TicketPurchaseRuleViolationException("你目前有未結束場次且分布於不同影廳，請待場次結束後再購買。");
        }

        if (activeByAuditorium.size() == 1) {
            String existingAuditorium = activeByAuditorium.keySet().iterator().next();
            if (!existingAuditorium.equals(requestedAuditorium)) {
                throw new TicketPurchaseRuleViolationException(
                        "你目前仍有未結束場次，僅能在同一影廳購買（目前影廳：" + existingAuditorium + "）。");
            }
        }

        if (activeCount + requestedCount > MAX_TICKETS_PER_ACTIVE_WINDOW) {
            int remaining = Math.max(0, MAX_TICKETS_PER_ACTIVE_WINDOW - activeCount);
            throw new TicketPurchaseRuleViolationException(
                    "目前未結束場次最多只能持有 4 張票（你已持有 " + activeCount + " 張，剩餘可買 " + remaining + " 張）。");
        }
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
        if (value instanceof LocalDate localDate) {
            return localDate.atStartOfDay(AppClock.zoneId()).toInstant();
        }
        if (value instanceof java.util.Date date) {
            return date.toInstant();
        }
        return null;
    }

    public record PurchaseResult(
            String movieId,
            String showtimeId,
            String auditorium,
            List<String> seatIds,
            Instant purchasedAt) {
    }
}

package com.example.cinema.service;

import java.time.Instant;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.Collection;
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
import com.example.cinema.model.ShowtimeDetails;
import com.example.cinema.model.User;

@Service
public class TicketPurchaseService {

    private static final int MAX_TICKETS_PER_ORDER = 4;
    private static final int MAX_TICKETS_PER_6H_WINDOW = 4;

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

        enforceSixHourSingleAuditoriumRule(member.getId(), auditorium, requested.size());

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

    private void enforceSixHourSingleAuditoriumRule(long memberId, String requestedAuditorium, int requestedCount) {
        // 6 小時內只能在同一個影廳購買，且累積最多 4 張。
        List<Map<String, Object>> grouped = jdbcTemplate.queryForList(
                "SELECT auditorium, COUNT(*) AS cnt " +
                        "FROM member_tickets " +
                        "WHERE member_id = ? AND purchased_at >= (NOW() - INTERVAL 6 HOUR) " +
                        "GROUP BY auditorium",
                memberId);

        if (grouped.size() > 1) {
            throw new TicketPurchaseRuleViolationException("你在最近 6 小時內已跨影廳購買過票，請等滿 6 小時後再購買。");
        }

        if (grouped.size() == 1) {
            String existingAuditorium = String.valueOf(grouped.get(0).get("auditorium"));
            if (!existingAuditorium.equals(requestedAuditorium)) {
                throw new TicketPurchaseRuleViolationException(
                        "最近 6 小時內只能在同一個影廳購買（你目前已在 " + existingAuditorium + " 買過票）。");
            }
        }

        Integer existingCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM member_tickets " +
                        "WHERE member_id = ? AND purchased_at >= (NOW() - INTERVAL 6 HOUR)",
                Integer.class,
                memberId);
        int already = existingCount == null ? 0 : existingCount.intValue();
        if (already + requestedCount > MAX_TICKETS_PER_6H_WINDOW) {
            int remaining = Math.max(0, MAX_TICKETS_PER_6H_WINDOW - already);
            throw new TicketPurchaseRuleViolationException(
                    "最近 6 小時內最多只能買 4 張票（你已買 " + already + " 張，剩餘可買 " + remaining + " 張）。");
        }
    }

    public record PurchaseResult(
            String movieId,
            String showtimeId,
            String auditorium,
            List<String> seatIds,
            Instant purchasedAt) {
    }
}

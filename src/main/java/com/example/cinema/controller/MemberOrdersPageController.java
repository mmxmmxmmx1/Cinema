package com.example.cinema.controller;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.cinema.config.AppClock;
import com.example.cinema.dto.OrderDetailResponse;
import com.example.cinema.dto.OrderSummaryResponse;
import com.example.cinema.exception.TicketPurchaseRuleViolationException;
import com.example.cinema.model.Movie;
import com.example.cinema.model.Showtime;
import com.example.cinema.service.MemberOrderService;
import com.example.cinema.service.MovieService;

@Controller
public class MemberOrdersPageController {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(AppClock.zoneId());
    private static final DateTimeFormatter SHOW_TS = DateTimeFormatter.ofPattern("MM/dd HH:mm")
            .withZone(AppClock.zoneId());

    private final MemberOrderService memberOrderService;
    private final MovieService movieService;

    public MemberOrdersPageController(MemberOrderService memberOrderService, MovieService movieService) {
        this.memberOrderService = memberOrderService;
        this.movieService = movieService;
    }

    @GetMapping("/member/orders")
    public String list(Authentication authentication, Model model) {
        String username = authentication == null ? null : authentication.getName();
        List<MemberOrderSummaryVm> orders;
        try {
            List<OrderSummaryResponse> raw = memberOrderService.listOrders(username);
            orders = raw.stream().map(this::toVm).toList();
        } catch (Exception ex) {
            orders = Collections.emptyList();
            model.addAttribute("error", "無法載入訂單（資料庫可能尚未啟動）。");
        }
        model.addAttribute("orders", orders);
        return "member-orders";
    }

    @GetMapping("/member/orders/{orderId}")
    public String detail(Authentication authentication, @PathVariable long orderId, Model model,
            RedirectAttributes redirectAttributes) {
        String username = authentication == null ? null : authentication.getName();
        try {
            OrderDetailResponse raw = memberOrderService.getOrder(username, orderId);
            model.addAttribute("order", toDetailVm(raw));
            return "member-order-detail";
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", "找不到此訂單或無權限查看。");
            return "redirect:/member/orders";
        }
    }

    @PostMapping("/member/orders/{orderId}/cancel")
    public String cancel(Authentication authentication, @PathVariable long orderId,
            RedirectAttributes redirectAttributes) {
        String username = authentication == null ? null : authentication.getName();
        try {
            memberOrderService.cancelOrder(username, orderId);
            redirectAttributes.addFlashAttribute("success", "已取消訂單 #" + orderId);
        } catch (TicketPurchaseRuleViolationException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", "取消失敗（系統忙碌或訂單不存在）。");
        }
        return "redirect:/member/orders";
    }

    private MemberOrderSummaryVm toVm(OrderSummaryResponse o) {
        String movieTitle = movieTitle(o.movieId());
        String showtime = showtimeStart(o.movieId(), o.showtimeId(), o.showStartAt());
        CancelPolicy cancelPolicy = evaluateCancelPolicy(o.status(), o.showStartAt());
        return new MemberOrderSummaryVm(
                o.orderId(),
                movieTitle,
                showtime,
                o.auditorium(),
                o.totalQty(),
                o.totalPrice(),
                o.status(),
                fmt(o.createdAt()),
                fmt(o.paidAt()),
                cancelPolicy.cancellable(),
                cancelPolicy.reason());
    }

    private MemberOrderDetailVm toDetailVm(OrderDetailResponse o) {
        String movieTitle = movieTitle(o.movieId());
        String showtime = showtimeStart(o.movieId(), o.showtimeId(), o.showStartAt());
        CancelPolicy cancelPolicy = evaluateCancelPolicy(o.status(), o.showStartAt());
        return new MemberOrderDetailVm(
                o.orderId(),
                movieTitle,
                showtime,
                o.auditorium(),
                o.totalQty(),
                o.unitPrice(),
                o.totalPrice(),
                o.status(),
                fmt(o.createdAt()),
                fmt(o.paidAt()),
                fmt(o.cancelledAt()),
                fmt(o.failedAt()),
                fmt(o.expiredAt()),
                o.failureReason(),
                o.paymentAttempts(),
                o.seatIds(),
                cancelPolicy.cancellable(),
                cancelPolicy.reason());
    }

    private String movieTitle(String movieId) {
        if (movieId == null) {
            return "";
        }
        Optional<Movie> movie = movieService.getMovieWithAvailability(movieId);
        return movie.map(Movie::getTitle).orElse(movieId);
    }

    private String showtimeStart(String movieId, String showtimeId, Instant showStartAt) {
        if (showStartAt != null) {
            return SHOW_TS.format(showStartAt);
        }
        if (movieId == null || showtimeId == null) {
            return "";
        }
        try {
            Instant resolved = movieService.resolveShowStartInstant(movieId, showtimeId);
            return SHOW_TS.format(resolved);
        } catch (Exception ex) {
            Optional<Showtime> showtime = movieService.getShowtime(movieId, showtimeId);
            return showtime.map(Showtime::getStartTime).orElse(showtimeId);
        }
    }

    private static String fmt(Instant value) {
        return value == null ? "" : TS.format(value);
    }

    private CancelPolicy evaluateCancelPolicy(String status, Instant showStartAt) {
        if ("PENDING".equalsIgnoreCase(status) || "FAILED".equalsIgnoreCase(status)) {
            return new CancelPolicy(true, null);
        }
        if (!"PAID".equalsIgnoreCase(status)) {
            return new CancelPolicy(false, "此訂單狀態不可取消。");
        }
        if (showStartAt == null) {
            return new CancelPolicy(false, "無法判定開演時間，暫時不可取消。");
        }
        Instant deadline = showStartAt.minus(30, ChronoUnit.MINUTES);
        if (!AppClock.nowInstant().isBefore(deadline)) {
            return new CancelPolicy(false, "開演前 30 分鐘內不可取消。");
        }
        return new CancelPolicy(true, null);
    }

    public record MemberOrderSummaryVm(
            long orderId,
            String movieTitle,
            String showtimeStartTime,
            String auditorium,
            int totalQty,
            int totalPrice,
            String status,
            String createdAt,
            String paidAt,
            boolean cancellable,
            String cancelHint) {
    }

    public record MemberOrderDetailVm(
            long orderId,
            String movieTitle,
            String showtimeStartTime,
            String auditorium,
            int totalQty,
            int unitPrice,
            int totalPrice,
            String status,
            String createdAt,
            String paidAt,
            String cancelledAt,
            String failedAt,
            String expiredAt,
            String failureReason,
            int paymentAttempts,
            List<String> seatIds,
            boolean cancellable,
            String cancelHint) {
    }

    private record CancelPolicy(boolean cancellable, String reason) {
    }
}

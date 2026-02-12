package com.example.cinema.controller;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.example.cinema.dto.ShowtimeSummary;
import com.example.cinema.config.AppClock;
import com.example.cinema.model.Movie;
import com.example.cinema.model.SeatLayout;
import com.example.cinema.model.SeatStatus;
import com.example.cinema.model.Showtime;
import com.example.cinema.service.MovieService;
import com.example.cinema.service.EmployeeTodoService;
import com.example.cinema.service.MemberLoyaltyService;
import com.example.cinema.service.MemberNotificationService;
import com.example.cinema.service.MemberOrderService;
import com.example.cinema.service.SessionService;
import com.example.cinema.service.SessionService.Realm;

import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class PageController {

    private final MovieService movieService;
    private final ObjectProvider<EmployeeTodoService> employeeTodoServiceProvider;
    private final ObjectProvider<MemberLoyaltyService> memberLoyaltyServiceProvider;
    private final ObjectProvider<MemberOrderService> memberOrderServiceProvider;
    private final ObjectProvider<MemberNotificationService> memberNotificationServiceProvider;
    private final SessionService sessionService;

    public PageController(
            MovieService movieService,
            ObjectProvider<EmployeeTodoService> employeeTodoServiceProvider,
            ObjectProvider<MemberLoyaltyService> memberLoyaltyServiceProvider,
            ObjectProvider<MemberOrderService> memberOrderServiceProvider,
            ObjectProvider<MemberNotificationService> memberNotificationServiceProvider,
            SessionService sessionService) {
        this.movieService = movieService;
        this.employeeTodoServiceProvider = employeeTodoServiceProvider;
        this.memberLoyaltyServiceProvider = memberLoyaltyServiceProvider;
        this.memberOrderServiceProvider = memberOrderServiceProvider;
        this.memberNotificationServiceProvider = memberNotificationServiceProvider;
        this.sessionService = sessionService;
    }

    @GetMapping({ "/login", "/login/" })
    public String unifiedLoginPage(
            @RequestParam(value = "target", required = false) String target,
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "returnTo", required = false) String returnTo,
            HttpSession session,
            Model model) {

        String normalized = target == null ? "" : target.trim().toLowerCase();
        boolean memberTarget = normalized.isEmpty() || "member".equals(normalized);
        boolean employeeTarget = "employee".equals(normalized) || "it".equals(normalized) || "manager".equals(normalized)
                || "admin".equals(normalized);

        if (memberTarget && hasActiveSession(session, Realm.MEMBER)) {
            return "redirect:/member";
        }
        if (employeeTarget && hasActiveSession(session, Realm.EMPLOYEE)) {
            return "redirect:/employee";
        }

        // This page is a wrapper: it posts to /member/login or /employee/login so that the correct
        // SecurityFilterChain handles authentication.
        model.addAttribute("formAction", employeeTarget ? "/employee/login" : "/member/login");
        model.addAttribute("targetLabel", employeeTarget ? "員工" : "會員");
        model.addAttribute("returnTo", returnTo);
        if (error != null) {
            model.addAttribute("error", "帳號或密碼不正確,請再試一次。");
        }
        return "login";
    }

    @GetMapping({ "/member/login", "/member/login/" })
    public String memberLoginPage(@RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "returnTo", required = false) String returnTo,
            HttpSession session,
            Model model) {
        if (hasActiveSession(session, Realm.MEMBER)) {
            return "redirect:/member";
        }
        return renderLoginView("member-login", Realm.MEMBER, error, returnTo, session, model);
    }

    @GetMapping("/member")
    public String memberPage(Model model, HttpSession session, RedirectAttributes redirectAttributes, Authentication authentication) {
        if (!hasActiveSession(session, Realm.MEMBER)) {
            redirectAttributes.addFlashAttribute("error", "請先登入會員帳號。");
            return "redirect:/member/login";
        }

        sessionService.updateLastActivity(session, Realm.MEMBER);
        populateMemberModel(model);

        // Recent orders shown in the member area (best-effort; don't break the page if DB is unavailable).
        try {
            String username = authentication == null ? null : authentication.getName();
            if (username != null && !username.isBlank()) {
                MemberLoyaltyService loyaltyService = memberLoyaltyServiceProvider.getIfAvailable();
                if (loyaltyService != null) {
                    model.addAttribute("memberPoints", loyaltyService.currentPoints(username));
                }

                MemberOrderService memberOrderService = memberOrderServiceProvider.getIfAvailable();
                if (memberOrderService != null) {
                    model.addAttribute("activeOrders",
                            memberOrderService.listActiveOrders(username, 5));
                    model.addAttribute("historyOrders",
                            memberOrderService.listHistoryOrders(username, 5));
                    model.addAttribute("upcomingBookings",
                            memberOrderService.listUpcomingBookings(username, 1));
                }
            }
        } catch (Exception ex) {
            model.addAttribute("orderLoadError", "無法載入訂單（資料庫可能尚未啟動）。");
        }

        try {
            String username = authentication == null ? null : authentication.getName();
            if (username != null && !username.isBlank()) {
                MemberNotificationService notificationService = memberNotificationServiceProvider.getIfAvailable();
                if (notificationService != null) {
                    model.addAttribute("recentNotifications", notificationService.listForMember(username, 5));
                }
            }
        } catch (Exception ex) {
            model.addAttribute("notificationLoadError", "無法載入通知（資料庫可能尚未啟動）。");
        }
        return "member";
    }

    @GetMapping({ "/employee/login", "/employee/login/" })
    public String employeeLoginPage(@RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "returnTo", required = false) String returnTo,
            HttpSession session, Model model) {
        if (hasActiveSession(session, Realm.EMPLOYEE)) {
            return "redirect:/employee";
        }
        return renderLoginView("employee-login", Realm.EMPLOYEE, error, returnTo, session, model);
    }

    @GetMapping("/employee")
    public String employeePage(Model model, HttpSession session, RedirectAttributes redirectAttributes) {
        if (!hasActiveSession(session, Realm.EMPLOYEE)) {
            redirectAttributes.addFlashAttribute("error", "請先登入員工帳號。");
            return "redirect:/employee/login";
        }

        sessionService.updateLastActivity(session, Realm.EMPLOYEE);

        model.addAttribute("title", "很好睡電影院員工後台");
        model.addAttribute("message", "掌握每日待辦與座位銷售狀況,協助影城營運。");

        try {
            EmployeeTodoService employeeTodoService = employeeTodoServiceProvider.getIfAvailable();
            if (employeeTodoService != null) {
                model.addAttribute("todoItems", employeeTodoService.listTodayTodos());
            } else {
                model.addAttribute("todoItems", List.of(
                        "08:30 — 影廳巡檢與音響測試",
                        "12:30 — 售票櫃台交接",
                        "16:45 — 小賣部盤點"));
            }
        } catch (Exception ex) {
            model.addAttribute("todoItems", List.of(
                    "08:30 — 影廳巡檢與音響測試",
                    "12:30 — 售票櫃台交接",
                    "16:45 — 小賣部盤點"));
        }

        List<ShowtimeSummary> summaries = new ArrayList<>();
        List<Movie> movies = movieService.getMovies();

        for (Movie movie : movies) {
            for (Showtime showtime : movie.getShowtimes()) {
                Instant showStartAt = movieService.resolveShowStartInstant(movie.getId(), showtime.getId());
                if (!showStartAt.isAfter(AppClock.nowInstant())) {
                    // Employee dashboard only shows showtimes that have not started yet.
                    continue;
                }

                SeatLayout seatLayout = movieService.getShowtimeDetails(movie.getId(), showtime.getId())
                        .getSeatLayout();
                long soldSeats = seatLayout.getSeats().stream().filter(SeatStatus::isReserved).count();
                int totalSeats = seatLayout.getRows() * seatLayout.getColumns();
                int percentage = totalSeats > 0 ? (int) (soldSeats * 100 / totalSeats) : 0;

                summaries.add(new ShowtimeSummary(
                        movie.getTitle(),
                        showtime.getStartTime(),
                        showtime.getAuditorium(),
                        (int) soldSeats,
                        totalSeats,
                        percentage));
            }
        }
        model.addAttribute("showtimeSummaries", summaries);

        return "employee";
    }

    @GetMapping("/employee/it/dashboard")
    public String employeeItDashboard(Model model, HttpSession session, RedirectAttributes redirectAttributes) {
        if (!hasActiveSession(session, Realm.EMPLOYEE)) {
            redirectAttributes.addFlashAttribute("error", "請先登入員工帳號。");
            return "redirect:/employee/login";
        }
        sessionService.updateLastActivity(session, Realm.EMPLOYEE);
        model.addAttribute("title", "很好睡電影院 IT 控制台");
        model.addAttribute("message", "監控系統健康狀態與維護排程。");
        return "it";
    }

    @GetMapping("/employee/manager/dashboard")
    public String employeeManagerDashboard(Model model, HttpSession session, RedirectAttributes redirectAttributes) {
        if (!hasActiveSession(session, Realm.EMPLOYEE)) {
            redirectAttributes.addFlashAttribute("error", "請先登入員工帳號。");
            return "redirect:/employee/login";
        }
        sessionService.updateLastActivity(session, Realm.EMPLOYEE);
        model.addAttribute("title", "很好睡電影院 主管儀表板");
        model.addAttribute("message", "查看營運概況、票務熱區與員工排班摘要。");
        return "manager";
    }

    @GetMapping("/employee/admin/dashboard")
    public String employeeAdminDashboard(Model model, HttpSession session, RedirectAttributes redirectAttributes) {
        if (!hasActiveSession(session, Realm.EMPLOYEE)) {
            redirectAttributes.addFlashAttribute("error", "請先登入員工帳號。");
            return "redirect:/employee/login";
        }
        sessionService.updateLastActivity(session, Realm.EMPLOYEE);
        model.addAttribute("title", "很好睡電影院管理中心");
        model.addAttribute("message", "檢視營運指標、角色權限與系統公告。");
        return "admin";
    }

    @PostMapping("/member/activity")
    public ResponseEntity<Void> memberActivity(HttpSession session) {
        if (!hasActiveSession(session, Realm.MEMBER)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        sessionService.updateLastActivity(session, Realm.MEMBER);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/employee/activity")
    public ResponseEntity<Void> employeeActivity(HttpSession session) {
        if (!hasActiveSession(session, Realm.EMPLOYEE)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        sessionService.updateLastActivity(session, Realm.EMPLOYEE);
        return ResponseEntity.noContent().build();
    }

    // Heartbeats for employee sub-areas. These are used by templates and are CSRF-ignored in SecurityConfig.
    @PostMapping("/employee/it/activity")
    public ResponseEntity<Void> employeeItActivity(HttpSession session) {
        return employeeActivity(session);
    }

    @PostMapping("/employee/manager/activity")
    public ResponseEntity<Void> employeeManagerActivity(HttpSession session) {
        return employeeActivity(session);
    }

    @PostMapping("/employee/admin/activity")
    public ResponseEntity<Void> employeeAdminActivity(HttpSession session) {
        return employeeActivity(session);
    }

    private boolean hasActiveSession(HttpSession session, Realm realm) {
        if (!sessionService.isAuthenticated(session, realm)) {
            return false;
        }
        return true;
    }

    private String renderLoginView(String viewName, Realm realm, String error, String returnTo, HttpSession session, Model model) {
        model.addAttribute("returnTo", returnTo);
        Duration lockRemaining = sessionService.remainingLockDuration(session, realm);
        if (lockRemaining.compareTo(Duration.ZERO) > 0) {
            applyLockMessage(model, lockRemaining);
        } else if (error != null) {
            String message = sessionService.consumeErrorMessage(session, realm);
            model.addAttribute("error", message != null ? message : "帳號或密碼不正確,請再試一次。");
        }

        return viewName;
    }

    private void populateMemberModel(Model model) {
        model.addAttribute("title", "很好睡電影院會員專區");
        model.addAttribute("message", "歡迎回來！立即查看個人優惠與專屬影城體驗。");
    }

    private void applyLockMessage(Model model, Duration duration) {
        Duration positive = duration.isNegative() ? Duration.ZERO : duration;
        long seconds = Math.max(1, positive.getSeconds());
        long minutes = Math.max(1, (seconds + 59) / 60);
        model.addAttribute("error", "帳號嘗試次數過多,已被暫時鎖定。請於 " + minutes + " 分鐘後再試。");
        model.addAttribute("lockSeconds", seconds);
    }
}

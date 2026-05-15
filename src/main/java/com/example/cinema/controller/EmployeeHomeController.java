package com.example.cinema.controller;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.example.cinema.config.AppClock;
import com.example.cinema.dto.ShowtimeSummary;
import com.example.cinema.model.Movie;
import com.example.cinema.model.SeatLayout;
import com.example.cinema.model.SeatStatus;
import com.example.cinema.model.Showtime;
import com.example.cinema.service.EmployeeTodoService;
import com.example.cinema.service.MovieService;
import com.example.cinema.service.OperationsDashboardService;
import com.example.cinema.service.SessionService.Realm;

import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class EmployeeHomeController {

    private static final List<String> FALLBACK_TODOS = List.of(
            "08:30 — 影廳巡檢與音響測試",
            "12:30 — 售票櫃台交接",
            "16:45 — 小賣部盤點");

    private final MovieService movieService;
    private final ObjectProvider<EmployeeTodoService> employeeTodoServiceProvider;
    private final ObjectProvider<OperationsDashboardService> operationsDashboardServiceProvider;
    private final PageSessionSupport pageSessionSupport;

    public EmployeeHomeController(
            MovieService movieService,
            ObjectProvider<EmployeeTodoService> employeeTodoServiceProvider,
            ObjectProvider<OperationsDashboardService> operationsDashboardServiceProvider,
            PageSessionSupport pageSessionSupport) {
        this.movieService = movieService;
        this.employeeTodoServiceProvider = employeeTodoServiceProvider;
        this.operationsDashboardServiceProvider = operationsDashboardServiceProvider;
        this.pageSessionSupport = pageSessionSupport;
    }

    @GetMapping("/employee")
    public String employeePage(Model model, HttpSession session, RedirectAttributes redirectAttributes) {
        if (!ensureEmployeeSession(session, redirectAttributes)) {
            return "redirect:/employee/login";
        }

        pageSessionSupport.updateLastActivity(session, Realm.EMPLOYEE);
        model.addAttribute("title", "很好睡電影院員工後台");
        model.addAttribute("message", "掌握每日待辦與座位銷售狀況,協助影城營運。");
        model.addAttribute("todoItems", loadTodoItems());
        model.addAttribute("showtimeSummaries", buildShowtimeSummaries());

        return "employee";
    }

    @GetMapping("/employee/it/dashboard")
    public String employeeItDashboard(Model model, HttpSession session, RedirectAttributes redirectAttributes) {
        if (!ensureEmployeeSession(session, redirectAttributes)) {
            return "redirect:/employee/login";
        }
        pageSessionSupport.updateLastActivity(session, Realm.EMPLOYEE);
        model.addAttribute("title", "很好睡電影院 IT 控制台");
        model.addAttribute("message", "監控系統健康狀態與維護排程。");
        OperationsDashboardService service = operationsDashboardServiceProvider.getIfAvailable();
        if (service != null) {
            model.addAttribute("itMetrics", service.itMetrics());
        }
        return "it";
    }

    @GetMapping("/employee/manager/dashboard")
    public String employeeManagerDashboard(Model model, HttpSession session, RedirectAttributes redirectAttributes) {
        if (!ensureEmployeeSession(session, redirectAttributes)) {
            return "redirect:/employee/login";
        }
        pageSessionSupport.updateLastActivity(session, Realm.EMPLOYEE);
        model.addAttribute("title", "很好睡電影院 主管儀表板");
        model.addAttribute("message", "查看營運概況、票務熱區與員工排班摘要。");
        OperationsDashboardService service = operationsDashboardServiceProvider.getIfAvailable();
        if (service != null) {
            model.addAttribute("managerMetrics", service.managerMetrics());
        }
        return "manager";
    }

    @GetMapping("/employee/admin/dashboard")
    public String employeeAdminDashboard(Model model, HttpSession session, RedirectAttributes redirectAttributes) {
        if (!ensureEmployeeSession(session, redirectAttributes)) {
            return "redirect:/employee/login";
        }
        pageSessionSupport.updateLastActivity(session, Realm.EMPLOYEE);
        model.addAttribute("title", "很好睡電影院管理中心");
        model.addAttribute("message", "檢視營運指標、角色權限與系統公告。");
        OperationsDashboardService service = operationsDashboardServiceProvider.getIfAvailable();
        if (service != null) {
            model.addAttribute("adminMetrics", service.adminMetrics());
            model.addAttribute("cleanupSnapshot", service.cleanupSnapshot());
        }
        return "admin";
    }

    @PostMapping("/employee/activity")
    public ResponseEntity<Void> employeeActivity(HttpSession session) {
        if (!pageSessionSupport.hasActiveSession(session, Realm.EMPLOYEE)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        pageSessionSupport.updateLastActivity(session, Realm.EMPLOYEE);
        return ResponseEntity.noContent().build();
    }

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

    private boolean ensureEmployeeSession(HttpSession session, RedirectAttributes redirectAttributes) {
        if (pageSessionSupport.hasActiveSession(session, Realm.EMPLOYEE)) {
            return true;
        }
        redirectAttributes.addFlashAttribute("error", "請先登入員工帳號。");
        return false;
    }

    private List<String> loadTodoItems() {
        try {
            EmployeeTodoService employeeTodoService = employeeTodoServiceProvider.getIfAvailable();
            return employeeTodoService == null ? FALLBACK_TODOS : employeeTodoService.listTodayTodos();
        } catch (Exception ex) {
            return FALLBACK_TODOS;
        }
    }

    private List<ShowtimeSummary> buildShowtimeSummaries() {
        List<ShowtimeSummary> summaries = new ArrayList<>();
        for (Movie movie : movieService.getMovies()) {
            for (Showtime showtime : movie.getShowtimes()) {
                Instant showStartAt = movieService.resolveShowStartInstant(movie.getId(), showtime.getId());
                if (!showStartAt.isAfter(AppClock.nowInstant())) {
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
        return summaries;
    }
}

package com.example.cinema.controller;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import com.example.cinema.dto.ShowtimeSummary;
import com.example.cinema.model.Movie;
import com.example.cinema.model.SeatLayout;
import com.example.cinema.model.SeatStatus;
import com.example.cinema.model.Showtime;
import com.example.cinema.service.MovieService;
import com.example.cinema.service.SessionService;
import com.example.cinema.service.SessionService.Realm;

import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class PageController {

    private static final Duration SESSION_TIMEOUT = Duration.ofMinutes(10);

    private final MovieService movieService;
    private final SessionService sessionService;

    public PageController(MovieService movieService, SessionService sessionService) {
        this.movieService = movieService;
        this.sessionService = sessionService;
    }

    @GetMapping({"/member/login", "/member/login/"})
    public String memberLoginPage(@RequestParam(value = "error", required = false) String error,
                                  HttpSession session,
                                  Model model) {
        if (hasActiveSession(session, Realm.MEMBER)) {
            sessionService.updateLastActivity(session, Realm.MEMBER);
            return "redirect:/member";
        }

        Duration lockRemaining = sessionService.remainingLockDuration(session, Realm.MEMBER);
        if (lockRemaining.compareTo(Duration.ZERO) > 0) {
            applyLockMessage(model, lockRemaining);
        } else if (error != null) {
            String message = sessionService.consumeErrorMessage(session, Realm.MEMBER);
            model.addAttribute("error", message != null ? message : "帳號或密碼不正確，請再試一次。");
        }

        return "member-login";
    }

    @GetMapping("/member")
    public String memberPage(Model model, HttpSession session, RedirectAttributes redirectAttributes) {
        if (!hasActiveSession(session, Realm.MEMBER)) {
            redirectAttributes.addFlashAttribute("error", "請先登入會員帳號。");
            return "redirect:/member/login";
        }

        sessionService.updateLastActivity(session, Realm.MEMBER);
        populateMemberModel(model);
        return "member";
    }

    @PostMapping("/member/logout")
    public String handleMemberLogout(HttpSession session) {
        sessionService.clearAuthentication(session, Realm.MEMBER);
        sessionService.resetAttempts(session, Realm.MEMBER);
        return "redirect:/";
    }

    @GetMapping({"/employee/login", "/employee/login/"})
    public String employeeLoginPage(@RequestParam(value = "error", required = false) String error,
                                    HttpSession session, Model model) {
        if (hasActiveSession(session, Realm.EMPLOYEE)) {
            sessionService.updateLastActivity(session, Realm.EMPLOYEE);
            return "redirect:/employee";
        }

        Duration lockRemaining = sessionService.remainingLockDuration(session, Realm.EMPLOYEE);
        if (lockRemaining.compareTo(Duration.ZERO) > 0) {
            applyLockMessage(model, lockRemaining);
        } else if (error != null) {
            String message = sessionService.consumeErrorMessage(session, Realm.EMPLOYEE);
            model.addAttribute("error", message != null ? message : "帳號或密碼不正確，請再試一次。");
        }
        return "employee-login";
    }

    @GetMapping("/employee")
    public String employeePage(Model model, HttpSession session, RedirectAttributes redirectAttributes) {
        if (!hasActiveSession(session, Realm.EMPLOYEE)) {
            redirectAttributes.addFlashAttribute("error", "請先登入員工帳號。");
            return "redirect:/employee/login";
        }

        sessionService.updateLastActivity(session, Realm.EMPLOYEE);

        model.addAttribute("title", "很好睡電影院員工後台");
        model.addAttribute("message", "掌握每日待辦與營運狀況，協助團隊順利進行。");

        List<ShowtimeSummary> summaries = new ArrayList<>();
        List<Movie> movies = movieService.getMovies();

        for (Movie movie : movies) {
            for (Showtime showtime : movie.getShowtimes()) {
                SeatLayout seatLayout = movieService.getShowtimeDetails(movie.getId(), showtime.getId()).getSeatLayout();
                long soldSeats = seatLayout.getSeats().stream().filter(SeatStatus::isReserved).count();
                int totalSeats = seatLayout.getRows() * seatLayout.getColumns();
                int percentage = totalSeats > 0 ? (int) (soldSeats * 100 / totalSeats) : 0;

                summaries.add(new ShowtimeSummary(
                        movie.getTitle(),
                        showtime.getStartTime(),
                        showtime.getAuditorium(),
                        (int) soldSeats,
                        totalSeats,
                        percentage
                ));
            }
        }
        model.addAttribute("showtimeSummaries", summaries);

        return "employee";
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

    private boolean hasActiveSession(HttpSession session, Realm realm) {
        if (!sessionService.isAuthenticated(session, realm)) {
            return false;
        }
        if (sessionService.isSessionExpired(session, realm, SESSION_TIMEOUT)) {
            sessionService.clearAuthentication(session, realm);
            return false;
        }
        return true;
    }

    private void populateMemberModel(Model model) {
        model.addAttribute("title", "很好睡電影院會員專區");
        model.addAttribute("message", "歡迎回來！立即查看個人優惠與專屬影城體驗。");
    }

    private void applyLockMessage(Model model, Duration duration) {
        Duration positive = duration.isNegative() ? Duration.ZERO : duration;
        long seconds = Math.max(1, positive.getSeconds());
        long minutes = Math.max(1, (seconds + 59) / 60);
        model.addAttribute("error", "帳號嘗試次數過多，已被暫時鎖定。請於 " + minutes + " 分鐘後再試。");
        model.addAttribute("lockSeconds", seconds);
    }
}

package com.example.cinema.controller;

import com.example.cinema.config.SessionConstants;
import com.example.cinema.dto.ShowtimeSummary;
import com.example.cinema.model.Movie;
import com.example.cinema.model.SeatLayout;
import com.example.cinema.model.Showtime;
import com.example.cinema.service.LoginAttemptService;
import com.example.cinema.service.LoginAttemptService.LoginAttemptStatus;
import com.example.cinema.service.MovieService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.WebAttributes;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Controller
public class PageController {

    private static final String MEMBER_USERNAME = "abc123";
    private static final String MEMBER_PASSWORD = "abc123";
    private static final String EMPLOYEE_USERNAME = "abc123";
    private static final String EMPLOYEE_PASSWORD = "abc123";
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(10);
    private static final Duration SESSION_TIMEOUT = Duration.ofMinutes(10);

    private final LoginAttemptService loginAttemptService;
    private final MovieService movieService;

    public PageController(LoginAttemptService loginAttemptService, MovieService movieService) {
        this.loginAttemptService = loginAttemptService;
        this.movieService = movieService;
    }


    // 會員登入頁面使用靜態 HTML（獨立頁面）
    @GetMapping({"/member/login", "/member/login/"})
    public String memberLoginPage(@RequestParam(value = "error", required = false) String error,
                                  HttpSession session,
                                  Model model) {
        if (hasValidMemberSession(session)) {
            updateLastActivity(session, SessionConstants.MEMBER_LAST_ACTIVITY);
            return "redirect:/member";
        }
        Duration lockRemaining = remainingLockDuration(session, SessionConstants.MEMBER_LOCK_UNTIL,
                SessionConstants.MEMBER_ATTEMPT_COUNT);
        if (lockRemaining != null) {
            applyLockMessage(model, lockRemaining);
        }
        if (error != null && model.getAttribute("error") == null) {
            if (session != null) {
                session.removeAttribute(WebAttributes.AUTHENTICATION_EXCEPTION);
                String txt = (String) session.getAttribute("memberLoginErrorText");
                if (txt != null && !txt.isBlank()) {
                    model.addAttribute("error", txt);
                    session.removeAttribute("memberLoginErrorText");
                } else {
                    model.addAttribute("error", "帳號或密碼錯誤，請再試一次。");
                }
            } else {
                model.addAttribute("error", "帳號或密碼錯誤，請再試一次。");
            }
        }
        return "member-login";
    }

    // 會員專區頁面使用靜態 HTML（獨立頁面）
    @GetMapping("/member")
    public String memberPage(Model model, HttpSession session, RedirectAttributes redirectAttributes) {
        if (!hasValidMemberSession(session)) {
            redirectAttributes.addFlashAttribute("error", "請先登入會員帳號。");
            return "redirect:/member/login";
        }
        updateLastActivity(session, SessionConstants.MEMBER_LAST_ACTIVITY);
        populateMemberModel(model);
        return "member";
    }

    @PostMapping("/member/login")
    public String handleMemberLogin(@RequestParam String username,
                                    @RequestParam String password,
                                    HttpSession session,
                                    Model model) {
        String attemptKey = buildAttemptKey("member", username);
        Duration sessionLock = remainingLockDuration(session, SessionConstants.MEMBER_LOCK_UNTIL,
                SessionConstants.MEMBER_ATTEMPT_COUNT);
        if (sessionLock != null) {
            applyLockMessage(model, sessionLock);
            return "member-login";
        }

        LoginAttemptStatus status = loginAttemptService.getStatus(attemptKey);
        if (status.locked()) {
            enforceGlobalLock(session, SessionConstants.MEMBER_LOCK_UNTIL,
                    SessionConstants.MEMBER_ATTEMPT_COUNT, status.lockDuration());
            applyLockMessage(model, status.lockDuration());
            return "member-login";
        }

        if (MEMBER_USERNAME.equals(username) && MEMBER_PASSWORD.equals(password)) {
            loginAttemptService.registerSuccess(attemptKey);
            establishMemberSession(session);
            resetAttempts(session, SessionConstants.MEMBER_ATTEMPT_COUNT, SessionConstants.MEMBER_LOCK_UNTIL);
            return "redirect:/member";
        }
        LoginAttemptStatus afterFailure = loginAttemptService.registerFailure(attemptKey);
        int attempts = incrementAttempts(session, SessionConstants.MEMBER_ATTEMPT_COUNT);
        if (attempts >= MAX_ATTEMPTS) {
            Duration lockDuration = afterFailure.locked()
                    ? afterFailure.lockDuration()
                    : LOCK_DURATION;
            Instant lockUntil = enforceLock(session, SessionConstants.MEMBER_LOCK_UNTIL, lockDuration);
            session.setAttribute(SessionConstants.MEMBER_ATTEMPT_COUNT, MAX_ATTEMPTS);
            applyLockMessage(model, Duration.between(Instant.now(), lockUntil));
        } else if (afterFailure.locked()) {
            Instant lockUntil = enforceLock(session, SessionConstants.MEMBER_LOCK_UNTIL, afterFailure.lockDuration());
            session.setAttribute(SessionConstants.MEMBER_ATTEMPT_COUNT, MAX_ATTEMPTS);
            applyLockMessage(model, Duration.between(Instant.now(), lockUntil));
        } else {
            int remaining = Math.max(0, MAX_ATTEMPTS - attempts);
            model.addAttribute("error", formatRemainingAttemptsMessage(remaining));
        }
        return "member-login";
    }

    @PostMapping("/member/logout")
    public String handleMemberLogout(HttpSession session) {
        session.removeAttribute(SessionConstants.MEMBER_SESSION_KEY);
        session.removeAttribute(SessionConstants.MEMBER_ACCESS_TOKEN);
        resetAttempts(session, SessionConstants.MEMBER_ATTEMPT_COUNT, SessionConstants.MEMBER_LOCK_UNTIL);
        session.removeAttribute(SessionConstants.MEMBER_LAST_ACTIVITY);
        return "redirect:/";
    }

    @GetMapping({"/employee/login", "/employee/login/"})
    public String employeeLoginPage(@RequestParam(value = "error", required = false) String error,
                                    HttpSession session, Model model) {
        if (hasValidEmployeeSession(session)) {
            updateLastActivity(session, SessionConstants.EMPLOYEE_LAST_ACTIVITY);
            return "redirect:/employee";
        }
        Duration lockRemaining = remainingLockDuration(session, SessionConstants.EMPLOYEE_LOCK_UNTIL,
                SessionConstants.EMPLOYEE_ATTEMPT_COUNT);
        if (lockRemaining != null) {
            applyLockMessage(model, lockRemaining);
        } else if (error != null && model.getAttribute("error") == null) {
            String txt = (String) session.getAttribute("employeeLoginErrorText");
            if (txt != null && !txt.isBlank()) {
                model.addAttribute("error", txt);
                session.removeAttribute("employeeLoginErrorText");
            } else {
                model.addAttribute("error", "帳號或密碼錯誤，請再試一次。");
            }
        }
        return "employee-login";
    }

    @GetMapping("/employee")
    public String employeePage(Model model, HttpSession session, RedirectAttributes redirectAttributes) {
        if (!hasValidEmployeeSession(session)) {
            redirectAttributes.addFlashAttribute("error", "請先登入員工帳號。");
            return "redirect:/employee/login";
        }
        updateLastActivity(session, SessionConstants.EMPLOYEE_LAST_ACTIVITY);

        model.addAttribute("title", "很好睡電影院員工後台");
        model.addAttribute("message", "即時場次狀態，協助團隊順利營運。");

        List<ShowtimeSummary> summaries = new ArrayList<>();
        List<Movie> movies = movieService.getMovies();

        for (Movie movie : movies) {
            for (Showtime showtime : movie.getShowtimes()) {
                SeatLayout seatLayout = movieService.getShowtimeDetails(movie.getId(), showtime.getId()).getSeatLayout();
                long soldSeats = seatLayout.getSeats().stream().filter(s -> s.isReserved()).count();
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

    @PostMapping("/employee/login")
    public String handleEmployeeLogin(@RequestParam String username,
                                      @RequestParam String password,
                                      HttpSession session,
                                      Model model) {
        String attemptKey = buildAttemptKey("employee", username);
        Duration sessionLock = remainingLockDuration(session, SessionConstants.EMPLOYEE_LOCK_UNTIL,
                SessionConstants.EMPLOYEE_ATTEMPT_COUNT);
        if (sessionLock != null) {
            applyLockMessage(model, sessionLock);
            return "employee-login";
        }

        LoginAttemptStatus status = loginAttemptService.getStatus(attemptKey);
        if (status.locked()) {
            enforceGlobalLock(session, SessionConstants.EMPLOYEE_LOCK_UNTIL,
                    SessionConstants.EMPLOYEE_ATTEMPT_COUNT, status.lockDuration());
            applyLockMessage(model, status.lockDuration());
            return "employee-login";
        }

        if (EMPLOYEE_USERNAME.equals(username) && EMPLOYEE_PASSWORD.equals(password)) {
            loginAttemptService.registerSuccess(attemptKey);
            establishEmployeeSession(session);
            resetAttempts(session, SessionConstants.EMPLOYEE_ATTEMPT_COUNT, SessionConstants.EMPLOYEE_LOCK_UNTIL);
            return "redirect:/employee";
        }
        LoginAttemptStatus afterFailure = loginAttemptService.registerFailure(attemptKey);
        int attempts = incrementAttempts(session, SessionConstants.EMPLOYEE_ATTEMPT_COUNT);
        if (attempts >= MAX_ATTEMPTS) {
            Duration lockDuration = afterFailure.locked()
                    ? afterFailure.lockDuration()
                    : LOCK_DURATION;
            Instant lockUntil = enforceLock(session, SessionConstants.EMPLOYEE_LOCK_UNTIL, lockDuration);
            session.setAttribute(SessionConstants.EMPLOYEE_ATTEMPT_COUNT, MAX_ATTEMPTS);
            applyLockMessage(model, Duration.between(Instant.now(), lockUntil));
        } else if (afterFailure.locked()) {
            Instant lockUntil = enforceLock(session, SessionConstants.EMPLOYEE_LOCK_UNTIL, afterFailure.lockDuration());
            session.setAttribute(SessionConstants.EMPLOYEE_ATTEMPT_COUNT, MAX_ATTEMPTS);
            applyLockMessage(model, Duration.between(Instant.now(), lockUntil));
        } else {
            int remaining = Math.max(0, MAX_ATTEMPTS - attempts);
            model.addAttribute("error", formatRemainingAttemptsMessage(remaining));
        }
        return "employee-login";
    }

    @PostMapping("/employee/logout")
    public String handleEmployeeLogout(HttpSession session) {
        session.removeAttribute(SessionConstants.EMPLOYEE_SESSION_KEY);
        session.removeAttribute(SessionConstants.EMPLOYEE_ACCESS_TOKEN);
        resetAttempts(session, SessionConstants.EMPLOYEE_ATTEMPT_COUNT, SessionConstants.EMPLOYEE_LOCK_UNTIL);
        session.removeAttribute(SessionConstants.EMPLOYEE_LAST_ACTIVITY);
        return "redirect:/";
    }

    @PostMapping("/member/activity")
    public ResponseEntity<Void> memberActivity(HttpSession session) {
        if (!hasValidMemberSession(session)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        updateLastActivity(session, SessionConstants.MEMBER_LAST_ACTIVITY);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/employee/activity")
    public ResponseEntity<Void> employeeActivity(HttpSession session) {
        if (!hasValidEmployeeSession(session)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        updateLastActivity(session, SessionConstants.EMPLOYEE_LAST_ACTIVITY);
        return ResponseEntity.noContent().build();
    }

    private void populateMemberModel(Model model) {
        model.addAttribute("title", "很好睡電影院會員專區");
        model.addAttribute("message", "歡迎回來！查看您的專屬優惠與即將到來的電影體驗。");
    }



    private boolean hasValidMemberSession(HttpSession session) {
        if (!Boolean.TRUE.equals(session.getAttribute(SessionConstants.MEMBER_SESSION_KEY))) {
            return false;
        }
        if (session.getAttribute(SessionConstants.MEMBER_ACCESS_TOKEN) == null) {
            session.removeAttribute(SessionConstants.MEMBER_SESSION_KEY);
            return false;
        }
        if (isSessionExpired(session, SessionConstants.MEMBER_LAST_ACTIVITY)) {
            session.removeAttribute(SessionConstants.MEMBER_SESSION_KEY);
            session.removeAttribute(SessionConstants.MEMBER_ACCESS_TOKEN);
            session.removeAttribute(SessionConstants.MEMBER_LAST_ACTIVITY);
            return false;
        }
        return true;
    }

    private boolean hasValidEmployeeSession(HttpSession session) {
        if (!Boolean.TRUE.equals(session.getAttribute(SessionConstants.EMPLOYEE_SESSION_KEY))) {
            return false;
        }
        if (session.getAttribute(SessionConstants.EMPLOYEE_ACCESS_TOKEN) == null) {
            session.removeAttribute(SessionConstants.EMPLOYEE_SESSION_KEY);
            return false;
        }
        if (isSessionExpired(session, SessionConstants.EMPLOYEE_LAST_ACTIVITY)) {
            session.removeAttribute(SessionConstants.EMPLOYEE_SESSION_KEY);
            session.removeAttribute(SessionConstants.EMPLOYEE_ACCESS_TOKEN);
            session.removeAttribute(SessionConstants.EMPLOYEE_LAST_ACTIVITY);
            return false;
        }
        return true;
    }

    private void establishMemberSession(HttpSession session) {
        session.setAttribute(SessionConstants.MEMBER_SESSION_KEY, true);
        session.setAttribute(SessionConstants.MEMBER_ACCESS_TOKEN, UUID.randomUUID().toString());
        updateLastActivity(session, SessionConstants.MEMBER_LAST_ACTIVITY);
    }

    private void establishEmployeeSession(HttpSession session) {
        session.setAttribute(SessionConstants.EMPLOYEE_SESSION_KEY, true);
        session.setAttribute(SessionConstants.EMPLOYEE_ACCESS_TOKEN, UUID.randomUUID().toString());
        updateLastActivity(session, SessionConstants.EMPLOYEE_LAST_ACTIVITY);
    }

    private String buildAttemptKey(String prefix, String username) {
        String normalized = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            normalized = "_anonymous";
        }
        return prefix + ':' + normalized;
    }

    private Duration remainingLockDuration(HttpSession session, String lockKey, String attemptsKey) {
        Instant lockUntil = (Instant) session.getAttribute(lockKey);
        if (lockUntil == null) {
            return null;
        }
        if (lockUntil.isAfter(Instant.now())) {
            return Duration.between(Instant.now(), lockUntil);
        }
        session.removeAttribute(lockKey);
        session.removeAttribute(attemptsKey);
        return null;
    }

    private boolean isSessionExpired(HttpSession session, String lastActivityKey) {
        Instant lastActivity = (Instant) session.getAttribute(lastActivityKey);
        if (lastActivity == null) {
            return false;
        }
        if (lastActivity.plus(SESSION_TIMEOUT).isBefore(Instant.now())) {
            return true;
        }
        return false;
    }

    private void updateLastActivity(HttpSession session, String lastActivityKey) {
        session.setAttribute(lastActivityKey, Instant.now());
    }

    private Instant enforceLock(HttpSession session, String lockKey, Duration duration) {
        Duration effectiveDuration = duration == null || duration.isZero() || duration.isNegative()
                ? LOCK_DURATION : duration;
        Instant lockUntil = Instant.now().plus(effectiveDuration);
        session.setAttribute(lockKey, lockUntil);
        return lockUntil;
    }

    private void enforceGlobalLock(HttpSession session, String lockKey, String attemptsKey, Duration duration) {
        enforceLock(session, lockKey, duration);
        session.setAttribute(attemptsKey, MAX_ATTEMPTS);
    }

    private int incrementAttempts(HttpSession session, String attemptsKey) {
        Integer current = (Integer) session.getAttribute(attemptsKey);
        int next = current == null ? 1 : current + 1;
        session.setAttribute(attemptsKey, next);
        return next;
    }

    private void resetAttempts(HttpSession session, String attemptsKey, String lockKey) {
        session.removeAttribute(attemptsKey);
        session.removeAttribute(lockKey);
    }

    private void applyLockMessage(Model model, Duration duration) {
        if (duration == null) {
            return;
        }
        Duration positive = duration.isNegative() ? Duration.ZERO : duration;
        long seconds = positive.getSeconds();
        if (seconds <= 0) {
            seconds = 1;
        }
        model.addAttribute("error", formatLockMessage(positive));
        model.addAttribute("lockSeconds", seconds);
    }

    private String formatLockMessage(Duration duration) {
        long seconds = duration.getSeconds();
        long minutes = (seconds + 59) / 60;
        if (minutes <= 0) {
            minutes = 1;
        }
        return "帳號因多次登入失敗已被鎖定，請於 " + minutes + " 分鐘後再試。";
    }

    private String formatRemainingAttemptsMessage(int remainingAttempts) {
        if (remainingAttempts <= 0) {
            return "帳號因多次登入失敗已被鎖定，請稍後再試。";
        }
        return "帳號或密碼不正確，還可以再嘗試 " + remainingAttempts + " 次。";
    }
}

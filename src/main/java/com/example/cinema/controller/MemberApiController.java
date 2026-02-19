package com.example.cinema.controller;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.example.cinema.dto.AuthStatusResponse;
import com.example.cinema.dto.MemberBooking;
import com.example.cinema.dto.MemberRegisterRequest;
import com.example.cinema.dto.MemberRegisterResponse;
import com.example.cinema.dto.MemberSummary;
import com.example.cinema.model.User.UserType;
import com.example.cinema.service.MemberLoyaltyService;
import com.example.cinema.service.MemberOrderService;
import com.example.cinema.service.SessionService;
import com.example.cinema.service.SessionService.Realm;
import com.example.cinema.service.UserService;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({ "/api", "/api/v1" })
public class MemberApiController {
	
    private final SessionService sessionService;
    private final MemberLoyaltyService memberLoyaltyService;
    private final MemberOrderService memberOrderService;
    private final UserService userService;
	
    public MemberApiController(
            SessionService sessionService,
            MemberLoyaltyService memberLoyaltyService,
            MemberOrderService memberOrderService,
            UserService userService) {
        this.sessionService = sessionService;
        this.memberLoyaltyService = memberLoyaltyService;
        this.memberOrderService = memberOrderService;
        this.userService = userService;
    }

    @GetMapping("/auth/member")
    public ResponseEntity<AuthStatusResponse> memberAuth(Authentication authentication) {
        boolean loggedIn = authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);

        Set<String> authorities = loggedIn
                ? authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(java.util.stream.Collectors.toSet())
                : Set.of();

        boolean isMember = authorities.contains("ROLE_MEMBER");
        boolean isEmployee = authorities.contains("ROLE_EMPLOYEE")
                || authorities.contains("ROLE_IT")
                || authorities.contains("ROLE_MANAGER")
                || authorities.contains("ROLE_ADMIN");

        String username = loggedIn ? authentication.getName() : null;
        List<String> roles = authorities.stream().sorted().toList();

        // Backward compatibility: `authenticated` means "logged in as MEMBER".
        return ResponseEntity.ok(new AuthStatusResponse(isMember, username, isMember, isEmployee, roles));
    }

    @PostMapping("/auth/member/register")
    public ResponseEntity<MemberRegisterResponse> registerMember(
            @Valid @RequestBody MemberRegisterRequest request) {
        String nickname = request.nickname() == null ? "" : request.nickname().trim();
        long userId = userService.registerUser(nickname, request.password(), UserType.MEMBER, false);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new MemberRegisterResponse(userId, nickname, "會員註冊成功"));
    }

    @PostMapping("/guest/watchlist/{movieId}")
    public ResponseEntity<Void> addGuestWatchlist(@PathVariable Long movieId, HttpSession session) {
        @SuppressWarnings("unchecked")
        Set<Long> set = (Set<Long>) session.getAttribute(sessionService.guestWatchlistKey());
        if (set == null) {
            set = new HashSet<>();
            session.setAttribute(sessionService.guestWatchlistKey(), set);
        }
        set.add(movieId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/guest/watchlist")
    public ResponseEntity<Set<Long>> getGuestWatchlist(HttpSession session) {
        @SuppressWarnings("unchecked")
        Set<Long> set = (Set<Long>) session.getAttribute(sessionService.guestWatchlistKey());
        if (set == null) {
            set = new HashSet<>();
        }
        return ResponseEntity.ok(set);
    }

    @GetMapping("/member/summary")
    public ResponseEntity<MemberSummary> summary(HttpSession session, Authentication authentication) {
        if (!sessionService.isAuthenticated(session, Realm.MEMBER)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String username = authentication == null ? null : authentication.getName();
        int points = memberLoyaltyService.currentPoints(username);
        List<MemberBooking> bookings = memberOrderService.listUpcomingBookings(username, 5).stream()
                .map(b -> new MemberBooking(b.movieTitle(), b.showStartLabel(), b.auditorium()))
                .toList();
        MemberSummary summary = new MemberSummary(points, bookings);
        return ResponseEntity.ok(summary);
    }
}

package com.example.cinema.controller;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.example.cinema.config.SecurityConfig;
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
import org.springframework.security.core.context.SecurityContext;
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
    public ResponseEntity<AuthStatusResponse> memberAuth(HttpSession session) {
        Authentication memberAuthentication = resolveRealmAuthentication(session, SecurityConfig.MEMBER_SECURITY_CONTEXT_KEY);
        Authentication employeeAuthentication = resolveRealmAuthentication(session, SecurityConfig.EMPLOYEE_SECURITY_CONTEXT_KEY);

        boolean isMember = memberAuthentication != null;
        boolean isEmployee = employeeAuthentication != null;

        Set<String> authorities = new HashSet<>();
        if (isMember) {
            authorities.addAll(toAuthorityCodes(memberAuthentication));
        }
        if (isEmployee) {
            authorities.addAll(toAuthorityCodes(employeeAuthentication));
        }

        String username = isMember ? memberAuthentication.getName()
                : (isEmployee ? employeeAuthentication.getName() : null);
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
    public ResponseEntity<Void> addGuestWatchlist(@PathVariable String movieId, HttpSession session) {
        String safeMovieId = movieId == null ? "" : movieId.trim();
        if (safeMovieId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        @SuppressWarnings("unchecked")
        Set<String> set = (Set<String>) session.getAttribute(sessionService.guestWatchlistKey());
        if (set == null) {
            set = new HashSet<>();
            session.setAttribute(sessionService.guestWatchlistKey(), set);
        }
        set.add(safeMovieId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/guest/watchlist")
    public ResponseEntity<Set<String>> getGuestWatchlist(HttpSession session) {
        @SuppressWarnings("unchecked")
        Set<String> set = (Set<String>) session.getAttribute(sessionService.guestWatchlistKey());
        if (set == null) {
            set = new HashSet<>();
        }
        return ResponseEntity.ok(set);
    }

    @GetMapping("/member/summary")
    public ResponseEntity<MemberSummary> summary(HttpSession session) {
        Authentication memberAuthentication = resolveRealmAuthentication(session, SecurityConfig.MEMBER_SECURITY_CONTEXT_KEY);
        if (!sessionService.isAuthenticated(session, Realm.MEMBER) || memberAuthentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String username = memberAuthentication.getName();
        int points = memberLoyaltyService.currentPoints(username);
        List<MemberBooking> bookings = memberOrderService.listUpcomingBookings(username, 5).stream()
                .map(b -> new MemberBooking(b.movieTitle(), b.showStartLabel(), b.auditorium()))
                .toList();
        MemberSummary summary = new MemberSummary(points, bookings);
        return ResponseEntity.ok(summary);
    }

    private Authentication resolveRealmAuthentication(HttpSession session, String contextKey) {
        if (session == null || contextKey == null || contextKey.isBlank()) {
            return null;
        }
        Object securityContext = session.getAttribute(contextKey);
        if (!(securityContext instanceof SecurityContext context)) {
            return null;
        }
        Authentication authentication = context.getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return authentication;
    }

    private Set<String> toAuthorityCodes(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return Set.of();
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(java.util.stream.Collectors.toSet());
    }
}

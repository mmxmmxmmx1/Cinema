package com.example.cinema.controller;

import com.example.cinema.config.SessionConstants;
import com.example.cinema.dto.MemberBooking;
import com.example.cinema.dto.MemberSummary;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api")
public class MemberApiController {

    @PostMapping("/guest/watchlist/{movieId}")
    public ResponseEntity<Void> addGuestWatchlist(@PathVariable Long movieId, HttpSession session) {
        @SuppressWarnings("unchecked")
        Set<Long> set = (Set<Long>) session.getAttribute(SessionConstants.GUEST_WATCHLIST);
        if (set == null) {
            set = new HashSet<>();
            session.setAttribute(SessionConstants.GUEST_WATCHLIST, set);
        }
        set.add(movieId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/guest/watchlist")
    public ResponseEntity<Set<Long>> getGuestWatchlist(HttpSession session) {
        @SuppressWarnings("unchecked")
        Set<Long> set = (Set<Long>) session.getAttribute(SessionConstants.GUEST_WATCHLIST);
        if (set == null) set = new HashSet<>();
        return ResponseEntity.ok(set);
    }

    @GetMapping("/member/summary")
    public ResponseEntity<MemberSummary> summary(HttpSession session) {
        if (!Boolean.TRUE.equals(session.getAttribute(SessionConstants.MEMBER_SESSION_KEY))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        MemberSummary summary = new MemberSummary(
                12450,
                List.of(
                        new MemberBooking("Dune: Part Two", "12/08 18:40", "1 號廳"),
                        new MemberBooking("Oppenheimer", "12/12 20:10", "2 號廳")
                )
        );
        return ResponseEntity.ok(summary);
    }
}

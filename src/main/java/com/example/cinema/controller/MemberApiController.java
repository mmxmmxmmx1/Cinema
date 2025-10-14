package com.example.cinema.controller;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.example.cinema.dto.MemberBooking;
import com.example.cinema.dto.MemberSummary;
import com.example.cinema.service.SessionService;
import com.example.cinema.service.SessionService.Realm;

import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class MemberApiController {

    private final SessionService sessionService;

    public MemberApiController(SessionService sessionService) {
        this.sessionService = sessionService;
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
    public ResponseEntity<MemberSummary> summary(HttpSession session) {
        if (!sessionService.isAuthenticated(session, Realm.MEMBER)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        MemberSummary summary = new MemberSummary(
                12450,
                List.of(
                        new MemberBooking("Dune: Part Two", "12/08 18:40", "1 號廳"),
                        new MemberBooking("Oppenheimer", "12/12 20:10", "2 號廳")));
        return ResponseEntity.ok(summary);
    }
}

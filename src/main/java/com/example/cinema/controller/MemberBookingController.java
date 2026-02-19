package com.example.cinema.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.cinema.config.AppClock;
import com.example.cinema.dto.TicketPurchaseRequest;
import com.example.cinema.dto.TicketPurchaseResponse;
import com.example.cinema.service.TicketPurchaseService;

import jakarta.validation.Valid;

@RestController
@RequestMapping({ "/member/api", "/member/api/v1" })
@Validated
public class MemberBookingController {

    private final TicketPurchaseService ticketPurchaseService;

    public MemberBookingController(TicketPurchaseService ticketPurchaseService) {
        this.ticketPurchaseService = ticketPurchaseService;
    }

    @PostMapping("/bookings")
    public ResponseEntity<TicketPurchaseResponse> purchase(
            Authentication authentication,
            @Valid @RequestBody TicketPurchaseRequest request) {

        String username = authentication == null ? null : authentication.getName();
        TicketPurchaseService.PurchaseResult result = ticketPurchaseService.purchase(
                username,
                request.getMovieId(),
                request.getShowtimeId(),
                request.getSeatIds());

        TicketPurchaseResponse payload = new TicketPurchaseResponse(
                result.movieId(),
                result.showtimeId(),
                result.auditorium(),
                result.seatIds(),
                result.purchasedAt() == null ? AppClock.nowInstant() : result.purchasedAt());
        return ResponseEntity.status(HttpStatus.CREATED).body(payload);
    }
}

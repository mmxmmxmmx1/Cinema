package com.example.cinema.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.cinema.dto.OrderCreateRequest;
import com.example.cinema.dto.OrderDetailResponse;
import com.example.cinema.dto.OrderSummaryResponse;
import com.example.cinema.service.MemberOrderService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/member/api/orders")
@Validated
public class MemberOrderController {

    private final MemberOrderService memberOrderService;

    public MemberOrderController(MemberOrderService memberOrderService) {
        this.memberOrderService = memberOrderService;
    }

    @PostMapping
    public ResponseEntity<OrderDetailResponse> create(Authentication authentication,
            @Valid @RequestBody OrderCreateRequest request) {
        String username = authentication == null ? null : authentication.getName();
        OrderDetailResponse created = memberOrderService.createOrder(
                username, request.getMovieId(), request.getShowtimeId(), request.getSeatIds());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/{orderId}/pay")
    public ResponseEntity<OrderDetailResponse> pay(Authentication authentication, @PathVariable long orderId,
            @RequestParam(name = "mode", required = false) String mode,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        String username = authentication == null ? null : authentication.getName();
        OrderDetailResponse paid = memberOrderService.payOrder(username, orderId, mode, idempotencyKey);
        return ResponseEntity.ok(paid);
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<OrderDetailResponse> cancel(Authentication authentication, @PathVariable long orderId) {
        String username = authentication == null ? null : authentication.getName();
        OrderDetailResponse cancelled = memberOrderService.cancelOrder(username, orderId);
        return ResponseEntity.ok(cancelled);
    }

    @GetMapping
    public ResponseEntity<List<OrderSummaryResponse>> list(Authentication authentication) {
        String username = authentication == null ? null : authentication.getName();
        return ResponseEntity.ok(memberOrderService.listOrders(username));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderDetailResponse> detail(Authentication authentication, @PathVariable long orderId) {
        String username = authentication == null ? null : authentication.getName();
        return ResponseEntity.ok(memberOrderService.getOrder(username, orderId));
    }
}

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
import com.example.cinema.exception.TicketPurchaseRuleViolationException;
import com.example.cinema.service.ApiRateLimitService;
import com.example.cinema.service.MemberOrderService;
import com.example.cinema.service.PaymentMode;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;

@RestController
@RequestMapping("/member/api/orders")
@Validated
public class MemberOrderController {

    private final MemberOrderService memberOrderService;
    private final ApiRateLimitService apiRateLimitService;
    private final int createLimitPerMinute;
    private final int payLimitPerMinute;
    private final int cancelLimitPerMinute;
    private final boolean allowPaymentModeOverride;

    public MemberOrderController(
            MemberOrderService memberOrderService,
            ApiRateLimitService apiRateLimitService,
            @Value("${app.payment.allow-mode-override:false}") boolean allowPaymentModeOverride,
            @Value("${app.rate-limit.order-create-per-minute:20}") int createLimitPerMinute,
            @Value("${app.rate-limit.order-pay-per-minute:20}") int payLimitPerMinute,
            @Value("${app.rate-limit.order-cancel-per-minute:20}") int cancelLimitPerMinute) {
        this.memberOrderService = memberOrderService;
        this.apiRateLimitService = apiRateLimitService;
        this.allowPaymentModeOverride = allowPaymentModeOverride;
        this.createLimitPerMinute = Math.max(1, createLimitPerMinute);
        this.payLimitPerMinute = Math.max(1, payLimitPerMinute);
        this.cancelLimitPerMinute = Math.max(1, cancelLimitPerMinute);
    }

    @PostMapping
    public ResponseEntity<OrderDetailResponse> create(Authentication authentication, HttpServletRequest httpRequest,
            @Valid @RequestBody OrderCreateRequest request) {
        String username = authentication == null ? null : authentication.getName();
        apiRateLimitService.check(
                "member-order-create",
                requestKey(username, httpRequest),
                createLimitPerMinute,
                Duration.ofMinutes(1));
        OrderDetailResponse created = memberOrderService.createOrder(
                username, request.getMovieId(), request.getShowtimeId(), request.getSeatIds());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/{orderId}/pay")
    public ResponseEntity<OrderDetailResponse> pay(Authentication authentication, HttpServletRequest httpRequest,
            @PathVariable long orderId,
            @RequestParam(name = "mode", required = false) String mode,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        String username = authentication == null ? null : authentication.getName();
        PaymentMode paymentMode = PaymentMode.fromNullable(mode);
        if (!allowPaymentModeOverride && paymentMode != PaymentMode.SUCCESS) {
            throw new TicketPurchaseRuleViolationException("正式環境不允許指定測試付款模式。");
        }
        apiRateLimitService.check(
                "member-order-pay",
                requestKey(username, httpRequest),
                payLimitPerMinute,
                Duration.ofMinutes(1));
        OrderDetailResponse paid = memberOrderService.payOrder(username, orderId, paymentMode, idempotencyKey);
        return ResponseEntity.ok(paid);
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<OrderDetailResponse> cancel(Authentication authentication, HttpServletRequest httpRequest,
            @PathVariable long orderId) {
        String username = authentication == null ? null : authentication.getName();
        apiRateLimitService.check(
                "member-order-cancel",
                requestKey(username, httpRequest),
                cancelLimitPerMinute,
                Duration.ofMinutes(1));
        OrderDetailResponse cancelled = memberOrderService.cancelOrder(username, orderId);
        return ResponseEntity.ok(cancelled);
    }

    @GetMapping
    public ResponseEntity<List<OrderSummaryResponse>> list(Authentication authentication) {
        String username = authentication == null ? null : authentication.getName();
        return ResponseEntity.ok(memberOrderService.listAllOrders(username, 300));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderDetailResponse> detail(Authentication authentication, @PathVariable long orderId) {
        String username = authentication == null ? null : authentication.getName();
        return ResponseEntity.ok(memberOrderService.getOrder(username, orderId));
    }

    private String requestKey(String username, HttpServletRequest request) {
        String userKey = (username == null || username.isBlank()) ? "anonymous" : username.trim();
        String ip = request == null ? null : request.getRemoteAddr();
        String ipKey = (ip == null || ip.isBlank()) ? "unknown-ip" : ip.trim();
        return userKey + "|" + ipKey;
    }
}

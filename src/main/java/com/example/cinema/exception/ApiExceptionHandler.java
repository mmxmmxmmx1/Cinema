package com.example.cinema.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.example.cinema.config.AppClock;
import com.example.cinema.controller.MemberApiController;
import com.example.cinema.controller.MemberBookingController;
import com.example.cinema.controller.MemberNotificationController;
import com.example.cinema.controller.MemberOrderController;
import com.example.cinema.controller.MovieController;
import com.example.cinema.filter.TraceIdFilter;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice(assignableTypes = { MovieController.class, MemberApiController.class, MemberBookingController.class,
        MemberOrderController.class, MemberNotificationController.class })
public class ApiExceptionHandler {

    @ExceptionHandler(TicketPurchaseRuleViolationException.class)
    public ResponseEntity<ApiError> purchaseRule(TicketPurchaseRuleViolationException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request, null);
    }

    @ExceptionHandler(TicketPurchaseConflictException.class)
    public ResponseEntity<ApiError> purchaseConflict(TicketPurchaseConflictException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request, null);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> illegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request, null);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiError> rateLimit(RateLimitExceededException ex, HttpServletRequest request) {
        return build(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), request, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, Object> details = new LinkedHashMap<>();
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError err : ex.getBindingResult().getFieldErrors()) {
            fields.put(err.getField(), err.getDefaultMessage());
        }
        details.put("fields", fields);
        return build(HttpStatus.BAD_REQUEST, "Validation failed", request, details);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unknown(Exception ex, HttpServletRequest request) {
        // Don't leak stack traces/implementation details to clients.
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", request, null);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message, HttpServletRequest request,
            Map<String, Object> details) {
        ApiError payload = new ApiError(
                AppClock.nowInstant(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request == null ? null : request.getRequestURI(),
                details,
                request == null ? null : String.valueOf(request.getAttribute(TraceIdFilter.TRACE_ID_ATTR)));
        return ResponseEntity.status(status).body(payload);
    }
}

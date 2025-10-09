package com.example.cinema.controller;

import com.example.cinema.config.SessionConstants;
import com.example.cinema.dto.AuthResponse;
import com.example.cinema.dto.LoginRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String DEMO_CREDENTIAL = "abc123";

    @GetMapping("/status")
    public ResponseEntity<AuthResponse> status(HttpSession session) {
        boolean authenticated = Boolean.TRUE.equals(session.getAttribute(SessionConstants.MEMBER_SESSION_KEY));
        return ResponseEntity.ok(new AuthResponse(authenticated, authenticated ? "已登入" : "尚未登入"));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request, HttpSession session) {
        if (DEMO_CREDENTIAL.equals(request.username()) && DEMO_CREDENTIAL.equals(request.password())) {
            session.setAttribute(SessionConstants.MEMBER_SESSION_KEY, true);
            return ResponseEntity.ok(new AuthResponse(true, "登入成功"));
        }
        session.removeAttribute(SessionConstants.MEMBER_SESSION_KEY);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new AuthResponse(false, "帳號或密碼不正確"));
    }

    @PostMapping("/logout")
    public ResponseEntity<AuthResponse> logout(HttpSession session) {
        session.removeAttribute(SessionConstants.MEMBER_SESSION_KEY);
        return ResponseEntity.ok(new AuthResponse(false, "已登出"));
    }
}

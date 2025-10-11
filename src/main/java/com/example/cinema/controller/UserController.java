package com.example.cinema.controller;

import com.example.cinema.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public static record RegisterRequest(String username, String password, Boolean admin) {}

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest req) {
        if (req == null || req.username() == null || req.username().isBlank()
                || req.password() == null || req.password().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "username / password 不可為空"));
        }
        Long id = userService.registerUser(
                req.username().trim(),
                req.password(),
                Boolean.TRUE.equals(req.admin())
        );
        return ResponseEntity.ok(Map.of("id", id, "username", req.username(), "admin", Boolean.TRUE.equals(req.admin())));
    }
}

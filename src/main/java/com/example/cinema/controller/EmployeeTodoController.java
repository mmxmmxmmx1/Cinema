package com.example.cinema.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.cinema.service.EmployeeTodoService;

@RestController
@RequestMapping("/employee")
public class EmployeeTodoController {

    private final EmployeeTodoService employeeTodoService;

    public EmployeeTodoController(EmployeeTodoService employeeTodoService) {
        this.employeeTodoService = employeeTodoService;
    }

    @PostMapping("/todos")
    public ResponseEntity<Void> replaceTodayTodos(
            Authentication authentication,
            @RequestBody TodoRequest request) {
        String operator = authentication == null ? "unknown" : authentication.getName();
        List<String> items = request == null ? List.of() : request.items();
        employeeTodoService.replaceTodayTodos(items, operator);
        return ResponseEntity.noContent().build();
    }

    public record TodoRequest(List<String> items) {
    }
}

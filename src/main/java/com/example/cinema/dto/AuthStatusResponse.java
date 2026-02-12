package com.example.cinema.dto;

import java.util.List;

public record AuthStatusResponse(
        boolean authenticated,
        String username,
        boolean member,
        boolean employee,
        List<String> roles) {
}

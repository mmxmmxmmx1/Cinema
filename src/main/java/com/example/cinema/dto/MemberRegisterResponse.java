package com.example.cinema.dto;

public record MemberRegisterResponse(
        long userId,
        String nickname,
        String message) {
}


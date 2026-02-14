package com.example.cinema.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MemberRegisterRequest(
        @NotBlank(message = "nickname 不可為空")
        @Pattern(regexp = "^[A-Za-z0-9]+$", message = "nickname 只能使用英文與數字")
        @Size(max = 100, message = "nickname 長度不可超過 100")
        String nickname,
        @NotBlank(message = "password 不可為空")
        @Size(max = 100, message = "password 長度不可超過 100")
        String password) {
}

package com.example.cinema.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import com.example.cinema.service.SessionService;
import com.example.cinema.service.SessionService.Realm;

import jakarta.servlet.http.HttpSession;

@Controller
public class SpaRouteGuardController {

    private final SessionService sessionService;

    public SpaRouteGuardController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @GetMapping({
            "/movies/{movieId}",
            "/movies/{movieId}/showtimes/{showtimeId}",
            "/checkout/{movieId}/showtimes/{showtimeId}",
            "/orders",
            "/orders/{orderId}" })
    public String memberSpaEntry(HttpSession session) {
        // 這三段（movies/checkout/orders）都採會員專用深連結策略。
        // 安全鏈為了支援深連結維持 permitAll，但在控制器層補上會員 session 檢查：
        // 未登入立刻導回首頁，已登入才放行 SPA 入口。
        if (!sessionService.isAuthenticated(session, Realm.MEMBER)) {
            return "redirect:/";
        }
        return "forward:/index.html";
    }
}

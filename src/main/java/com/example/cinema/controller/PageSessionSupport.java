package com.example.cinema.controller;

import java.time.Duration;

import com.example.cinema.service.SessionService;
import com.example.cinema.service.SessionService.Realm;

import jakarta.servlet.http.HttpSession;

import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

@Component
class PageSessionSupport {

    private final SessionService sessionService;

    PageSessionSupport(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    boolean hasActiveSession(HttpSession session, Realm realm) {
        return sessionService.isAuthenticated(session, realm);
    }

    void updateLastActivity(HttpSession session, Realm realm) {
        sessionService.updateLastActivity(session, realm);
    }

    String renderLoginView(String viewName, Realm realm, String error, String returnTo, HttpSession session, Model model) {
        model.addAttribute("returnTo", returnTo);
        Duration lockRemaining = sessionService.remainingLockDuration(session, realm);
        if (lockRemaining.compareTo(Duration.ZERO) > 0) {
            applyLockMessage(model, lockRemaining);
        } else if (error != null) {
            String message = sessionService.consumeErrorMessage(session, realm);
            model.addAttribute("error", message != null ? message : "帳號或密碼不正確,請再試一次。");
        }

        return viewName;
    }

    private void applyLockMessage(Model model, Duration duration) {
        Duration positive = duration.isNegative() ? Duration.ZERO : duration;
        long seconds = Math.max(1, positive.getSeconds());
        long minutes = Math.max(1, (seconds + 59) / 60);
        model.addAttribute("error", "帳號嘗試次數過多,已被暫時鎖定。請於 " + minutes + " 分鐘後再試。");
        model.addAttribute("lockSeconds", seconds);
    }
}

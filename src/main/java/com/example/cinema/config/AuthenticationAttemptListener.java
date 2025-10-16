package com.example.cinema.config;

import com.example.cinema.service.LoginAttemptService;
import com.example.cinema.service.SessionService;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

// @Component  // ← 注释掉此行：禁用此监听器
public class AuthenticationAttemptListener {

    private final LoginAttemptService loginAttemptService;

    public AuthenticationAttemptListener(LoginAttemptService loginAttemptService) {
        this.loginAttemptService = loginAttemptService;
    }

    // @EventListener // ← 注释掉此行
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        HttpServletRequest request = currentRequest();
        SessionService.Realm realm = resolveRealm(request);
        if (realm != null) {
            loginAttemptService.registerSuccess(realm, event.getAuthentication().getName());
        }
    }

    // @EventListener // ← 注释掉此行
    public void onAuthenticationFailure(AbstractAuthenticationFailureEvent event) {
        HttpServletRequest request = currentRequest();
        SessionService.Realm realm = resolveRealm(request);
        if (realm != null) {
            loginAttemptService.registerFailure(realm, event.getAuthentication().getName());
        }
    }

    private HttpServletRequest currentRequest() {
        return ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
    }

    private SessionService.Realm resolveRealm(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri.startsWith("/member")) {
            return SessionService.Realm.MEMBER;
        }
        if (uri.startsWith("/employee")) {
            return SessionService.Realm.EMPLOYEE;
        }
        return null;
    }
}
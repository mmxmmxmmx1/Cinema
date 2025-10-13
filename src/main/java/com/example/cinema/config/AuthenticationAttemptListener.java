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

@Component
public class AuthenticationAttemptListener {

    private final LoginAttemptService loginAttemptService;

    public AuthenticationAttemptListener(LoginAttemptService loginAttemptService) {
        this.loginAttemptService = loginAttemptService;
    }

    @EventListener
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        HttpServletRequest request = currentRequest();
        SessionService.Realm realm = resolveRealm(request);
        if (realm != null) {
            loginAttemptService.registerSuccess(realm, event.getAuthentication().getName());
        }
    }

    @EventListener
    public void onAuthenticationFailure(AbstractAuthenticationFailureEvent event) {
        HttpServletRequest request = currentRequest();
        SessionService.Realm realm = resolveRealm(request);
        if (realm != null) {
            loginAttemptService.registerFailure(realm, event.getAuthentication().getName());
        }
    }

    private SessionService.Realm resolveRealm(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String path = request.getRequestURI();
        if (path.startsWith("/employee")) {
            return SessionService.Realm.EMPLOYEE;
        }
        if (path.startsWith("/member")) {
            return SessionService.Realm.MEMBER;
        }
        return null;
    }

    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes == null ? null : attributes.getRequest();
    }
}

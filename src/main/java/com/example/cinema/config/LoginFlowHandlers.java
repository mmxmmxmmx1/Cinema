package com.example.cinema.config;

import com.example.cinema.service.LoginAttemptService;
import com.example.cinema.service.SessionService;

import jakarta.servlet.http.HttpSession;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Function;

@Component
public class LoginFlowHandlers {

    private final LoginAttemptService loginAttemptService;
    private final SessionService sessionService;

    public LoginFlowHandlers(LoginAttemptService loginAttemptService, SessionService sessionService) {
        this.loginAttemptService = loginAttemptService;
        this.sessionService = sessionService;
    }

    public AuthenticationFailureHandler failureHandler(SessionService.Realm realm, String redirectTarget) {
        return (request, response, exception) -> {
            String username = request.getParameter("username");
            var status = loginAttemptService.getStatus(realm, username);
            HttpSession session = request.getSession(true);
            sessionService.applyFailureFeedback(session, realm, status);
            response.sendRedirect(redirectTarget);
        };
    }

    public AuthenticationSuccessHandler successHandler(SessionService.Realm realm,
            String redirectTarget,
            SuccessCallback callback) {
        return (request, response, authentication) -> {
            String username = authentication.getName();
            loginAttemptService.registerSuccess(realm, username);
            HttpSession session = request.getSession(true);
            sessionService.establishSession(session, realm);
            sessionService.resetAttempts(session, realm);
            if (callback != null) {
                callback.handle(session, authentication);
            }
            response.sendRedirect(redirectTarget);
        };
    }

    public AuthenticationSuccessHandler roleAwareSuccessHandler(
            SessionService.Realm baseRealm,
            Function<Authentication, RoleResolution> resolver,
            SuccessCallback callback) {
        return (request, response, authentication) -> {
            String username = authentication.getName();
            loginAttemptService.registerSuccess(baseRealm, username);

            HttpSession session = request.getSession(true);

            RoleResolution resolution = resolver.apply(authentication);

            for (SessionService.Realm realm : resolution.realms()) {
                sessionService.establishSession(session, realm);
                sessionService.resetAttempts(session, realm);
            }

            if (callback != null) {
                callback.handle(session, authentication);
            }

            response.sendRedirect(resolution.redirectUrl());
        };
    }

    public SessionService sessionService() {
        return sessionService;
    }

    @FunctionalInterface
    public interface SuccessCallback {
        void handle(HttpSession session, Authentication authentication);
    }

    public record RoleResolution(String redirectUrl, List<SessionService.Realm> realms) {
    }
}

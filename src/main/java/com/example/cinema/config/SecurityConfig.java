package com.example.cinema.config;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.InvalidCsrfTokenException;
import org.springframework.security.web.csrf.MissingCsrfTokenException;

import com.example.cinema.dao.UserDao;
import com.example.cinema.model.User;
import com.example.cinema.service.LoginAttemptService;
import com.example.cinema.service.SessionService;
import com.example.cinema.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.csrf.CsrfException;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String STANDARD_CSP = String.join("; ",
            "default-src 'self'",
            "img-src 'self' https: data:",
            "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com",
            "font-src 'self' https://fonts.gstatic.com data:",
            "script-src 'self' 'unsafe-inline' https://unpkg.com",
            "connect-src 'self'",
            "object-src 'none'",
            "base-uri 'self'",
            "frame-ancestors 'self'",
            "form-action 'self'");

    // The SPA uses Vue templates defined as strings in JS, which requires runtime compilation.
    // Vue's runtime compiler relies on `new Function(...)` (blocked by CSP unless 'unsafe-eval' is allowed).
    // Keep this relaxed CSP scoped to the public SPA pages only.
    private static final String SPA_CSP = String.join("; ",
            "default-src 'self'",
            "img-src 'self' https: data:",
            "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com",
            "font-src 'self' https://fonts.gstatic.com data:",
            "script-src 'self' 'unsafe-inline' 'unsafe-eval' https://unpkg.com",
            "connect-src 'self'",
            "object-src 'none'",
            "base-uri 'self'",
            "frame-ancestors 'self'",
            "form-action 'self'");

    private final UserDao userDao;
    private final SessionService sessionService;
    private final LoginAttemptService loginAttemptService;
    private final ObjectProvider<UserService> userServiceProvider;
    private final boolean csrfCookieSecure;
    private final String csrfCookieSameSite;

    public SecurityConfig(UserDao userDao,
            SessionService sessionService,
            LoginAttemptService loginAttemptService,
            ObjectProvider<UserService> userServiceProvider,
            @Value("${app.security.csrf-cookie-secure:false}") boolean csrfCookieSecure,
            @Value("${app.security.csrf-cookie-same-site:Lax}") String csrfCookieSameSite) {
        this.userDao = userDao;
        this.sessionService = sessionService;
        this.loginAttemptService = loginAttemptService;
        this.userServiceProvider = userServiceProvider;
        this.csrfCookieSecure = csrfCookieSecure;
        this.csrfCookieSameSite = csrfCookieSameSite;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public CookieCsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repo = CookieCsrfTokenRepository.withHttpOnlyFalse();
        // Force a site-wide cookie path so /api/csrf and /member/api/** share the same token cookie.
        repo.setCookiePath("/");
        repo.setCookieName("XSRF-TOKEN");
        repo.setHeaderName("X-XSRF-TOKEN");
        repo.setCookieCustomizer(builder -> builder
                .secure(csrfCookieSecure)
                .sameSite(normalizeSameSite(csrfCookieSameSite)));
        return repo;
    }

    private static String normalizeSameSite(String raw) {
        if (raw == null) {
            return "Lax";
        }
        String value = raw.trim();
        if (value.equalsIgnoreCase("Strict")) {
            return "Strict";
        }
        if (value.equalsIgnoreCase("None")) {
            return "None";
        }
        return "Lax";
    }

    @Bean
    @Order(1)
    public SecurityFilterChain employeeSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/employee/**")
                .userDetailsService(username -> {
                    // ⚠️ 重要：在驗證密碼之前先檢查帳號是否被鎖定
                    var employeeStatus = loginAttemptService.getStatus(SessionService.Realm.EMPLOYEE, username);
                    if (employeeStatus.locked()) {
                        long minutes = Math.max(1, (employeeStatus.lockDuration().getSeconds() + 59) / 60);
                        throw new LockedException("帳號已被鎖定 " + minutes + " 分鐘，請稍後再試。");
                    }
                    
                    // 只從 employee 表查詢
                    User user = userDao.findEmployeeByUsername(username)
                            .orElseThrow(() -> new UsernameNotFoundException("Employee not found: " + username));
                    return new org.springframework.security.core.userdetails.User(
                            user.getUsername(),
                            user.getPassword(),
                            user.getRoles().stream()
                                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getCode()))
                                    .collect(Collectors.toList()));
                })
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/employee/login", "/employee/login/").permitAll()
                        .requestMatchers("/employee", "/employee/", "/employee/dashboard")
                        .hasAnyRole("EMPLOYEE", "IT", "MANAGER", "ADMIN")
                        .requestMatchers("/employee/it/**").access((authentication, context) -> {
                            return hasMinimumLevel(authentication.get(), 20);
                        })
                        .requestMatchers("/employee/manager/**").access((authentication, context) -> {
                            return hasMinimumLevel(authentication.get(), 30);
                        })
                        .requestMatchers("/employee/admin/**").hasRole("ADMIN")
                        .requestMatchers("/employee/**").hasAnyRole("EMPLOYEE", "IT", "MANAGER", "ADMIN")
                        .anyRequest().authenticated())

                // Keep CSRF enabled for employee pages.
                // Only ignore non-sensitive heartbeat endpoints.
                .csrf(csrf -> csrf.ignoringRequestMatchers(
                        "/employee/activity",
                        "/employee/it/activity",
                        "/employee/manager/activity",
                        "/employee/admin/activity"))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(STANDARD_CSP))
                        .frameOptions(frame -> frame.sameOrigin())
                        .referrerPolicy(ref -> ref.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .permissionsPolicy(policy -> policy.policy("camera=(), microphone=(), geolocation=()")))
                .formLogin(login -> login
                        .loginPage("/employee/login").permitAll()
                        .loginProcessingUrl("/employee/login")
                        .failureHandler((request, response, exception) -> {
                            String username = request.getParameter("username");
                            String clientIp = getClientIp(request);

                            boolean exists = false;
                            if (username != null && !username.isBlank()) {
                                // Employee realm should only check the employee table to avoid mixing member accounts.
                                exists = userDao.findEmployeeByUsername(username.trim()).isPresent();
                            }

                            String attemptKey = exists ? username : clientIp;
                            var status = loginAttemptService.registerFailure(SessionService.Realm.EMPLOYEE, attemptKey);
                            HttpSession session = request.getSession(true);
                            sessionService.applyFailureFeedback(session, SessionService.Realm.EMPLOYEE, status);
                            response.sendRedirect("/employee/login?error");
                        })
                        .successHandler((request, response, authentication) -> {
                            String username = authentication.getName();
                            String clientIp = getClientIp(request);

                            // ✅ 帳號鎖定檢查已移至 userDetailsService()，這裡只檢查 IP 鎖定
                            // 檢查 IP 是否被鎖定
                            var ipStatus = loginAttemptService.getStatus(SessionService.Realm.EMPLOYEE, clientIp);
                            if (ipStatus != null && ipStatus.locked()) {
                                HttpSession session = request.getSession(true);
                                sessionService.applyFailureFeedback(session, SessionService.Realm.EMPLOYEE, ipStatus);
                                SecurityContextHolder.clearContext();
                                response.sendRedirect("/employee/login?error");
                                return;
                            }

                            HttpSession session = request.getSession(true);
                            loginAttemptService.registerSuccess(SessionService.Realm.EMPLOYEE, username);
                            loginAttemptService.registerSuccess(SessionService.Realm.EMPLOYEE, clientIp);
                            sessionService.establishSession(session, SessionService.Realm.EMPLOYEE);
                            sessionService.resetAttempts(session, SessionService.Realm.EMPLOYEE);

                            String redirectUrl = determineEmployeeRedirectUrl(authentication);
                            response.sendRedirect(redirectUrl);
                        }))
                .logout(logout -> logout
                        .logoutUrl("/employee/logout")
                        .logoutSuccessHandler((request, response, authentication) -> {
                            var session = request.getSession(false);
                            if (session != null) {
                                sessionService.clearAuthentication(session, SessionService.Realm.EMPLOYEE);
                                sessionService.resetAttempts(session, SessionService.Realm.EMPLOYEE);
                            }
                            response.sendRedirect("/employee/login");
                        })
                        .permitAll());
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain memberSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/member/**")
                .userDetailsService(username -> {
                    // ⚠️ 重要：在驗證密碼之前先檢查帳號是否被鎖定
                    var memberStatus = loginAttemptService.getStatus(SessionService.Realm.MEMBER, username);
                    if (memberStatus.locked()) {
                        long minutes = Math.max(1, (memberStatus.lockDuration().getSeconds() + 59) / 60);
                        throw new LockedException("帳號已被鎖定 " + minutes + " 分鐘，請稍後再試。");
                    }
                    
                    // 只從 members 表查詢
                    User user = userDao.findMemberByUsername(username)
                            .orElseThrow(() -> new UsernameNotFoundException("Member not found: " + username));
                    return new org.springframework.security.core.userdetails.User(
                            user.getUsername(),
                            user.getPassword(),
                            user.getRoles().stream()
                                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getCode()))
                                    .collect(Collectors.toList()));
                })
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/member/login",
                                "/member/login/",
                                "/member/password/forgot",
                                "/member/password/reset")
                        .permitAll()
                        .requestMatchers("/member/**").hasRole("MEMBER")
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex.accessDeniedHandler((request, response, accessDeniedException) -> {
                    if (request.getRequestURI() != null && request.getRequestURI().startsWith("/member/api/")) {
                        String message;
                        String code;
                        if (accessDeniedException instanceof MissingCsrfTokenException) {
                            message = "缺少 CSRF token，請重新整理頁面後再試。";
                            code = "CSRF_MISSING";
                        } else if (accessDeniedException instanceof InvalidCsrfTokenException) {
                            message = "CSRF token 不一致或已過期，請重新整理頁面後再試。";
                            code = "CSRF_INVALID";
                        } else if (accessDeniedException instanceof CsrfException) {
                            message = "CSRF 驗證失敗，請重新整理頁面後再試。";
                            code = "CSRF_FAILED";
                        } else {
                            message = "沒有會員權限，請確認已使用會員帳號登入。";
                            code = "MEMBER_ACCESS_DENIED";
                        }
                        writeJsonError(response, HttpStatus.FORBIDDEN, code, message);
                        return;
                    }
                    response.sendError(HttpStatus.FORBIDDEN.value(), "Forbidden");
                }))
                // Keep CSRF enabled for member pages.
                // Only ignore non-sensitive heartbeat endpoint.
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository())
                        .ignoringRequestMatchers(
                                "/member/activity"))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(STANDARD_CSP))
                        .frameOptions(frame -> frame.sameOrigin())
                        .referrerPolicy(ref -> ref.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .permissionsPolicy(policy -> policy.policy("camera=(), microphone=(), geolocation=()")))
                .formLogin(login -> login
                        .loginPage("/member/login").permitAll()
                        .loginProcessingUrl("/member/login")
                        .failureHandler((request, response, exception) -> {
                            String username = request.getParameter("username");
                            String clientIp = getClientIp(request);
                            String returnTo = request.getParameter("returnTo");

                            boolean exists = false;
                            if (username != null && !username.isBlank()) {
                                // Member realm should only check the member table to avoid mixing employee accounts.
                                exists = userDao.findMemberByUsername(username.trim()).isPresent();
                            }

                            String attemptKey = exists ? username : clientIp;
                            var status = loginAttemptService.registerFailure(SessionService.Realm.MEMBER, attemptKey);
                            HttpSession session = request.getSession(true);
                            sessionService.applyFailureFeedback(session, SessionService.Realm.MEMBER, status);
                            response.sendRedirect(withReturnTo("/member/login?error", returnTo));
                        })
                        .successHandler((request, response, authentication) -> {
                            String username = authentication.getName();
                            String clientIp = getClientIp(request);
                            String returnTo = request.getParameter("returnTo");

                            // ✅ 帳號鎖定檢查已移至 userDetailsService()，這裡只檢查 IP 鎖定
                            // 檢查 IP 是否被鎖定
                            var ipStatus = loginAttemptService.getStatus(SessionService.Realm.MEMBER, clientIp);
                            if (ipStatus != null && ipStatus.locked()) {
                                HttpSession session = request.getSession(true);
                                sessionService.applyFailureFeedback(session, SessionService.Realm.MEMBER, ipStatus);
                                SecurityContextHolder.clearContext();
                                response.sendRedirect(withReturnTo("/member/login?error", returnTo));
                                return;
                            }

                            HttpSession session = request.getSession(true);
                            loginAttemptService.registerSuccess(SessionService.Realm.MEMBER, username);
                            loginAttemptService.registerSuccess(SessionService.Realm.MEMBER, clientIp);
                            sessionService.establishSession(session, SessionService.Realm.MEMBER);
                            sessionService.resetAttempts(session, SessionService.Realm.MEMBER);
                            handleMemberPostLogin(session, authentication);
                            String safeReturnTo = sanitizeReturnTo(returnTo);
                            response.sendRedirect(safeReturnTo != null ? safeReturnTo : "/member");
                        }))
                .logout(logout -> logout
                        .logoutUrl("/member/logout")
                        .logoutSuccessHandler((request, response, authentication) -> {
                            var session = request.getSession(false);
                            if (session != null) {
                                sessionService.clearAuthentication(session, SessionService.Realm.MEMBER);
                                sessionService.resetAttempts(session, SessionService.Realm.MEMBER);
                            }
                            response.sendRedirect("/member/login");
                        })
                        .permitAll());
        return http.build();
    }

    @Bean
    @Order(3)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/index.html", "/login", "/error",
                        "/css/**", "/js/**", "/images/**", "/favicon.ico",
                        "/webjars/**", "/assets/**")
                .permitAll()
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/csrf").permitAll()
                .requestMatchers("/api/health").permitAll()
                .requestMatchers("/api/guest/**", "/api/movies/**").permitAll()
                .requestMatchers("/movies/**").permitAll() // 加這行
                .anyRequest().authenticated())
                // Only ignore CSRF for guest APIs used by the SPA without a token.
                // Keep CSRF enabled elsewhere to avoid widening the attack surface.
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository())
                        .ignoringRequestMatchers("/api/guest/**"));

        http.headers(headers -> headers
                .contentSecurityPolicy(csp -> csp.policyDirectives(SPA_CSP))
                .frameOptions(frame -> frame.sameOrigin())
                .referrerPolicy(ref -> ref.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .permissionsPolicy(policy -> policy.policy("camera=(), microphone=(), geolocation=()")));
        return http.build();
    }

    private String getClientIp(HttpServletRequest request) {
        // Prefer remoteAddr to avoid trusting spoofable headers. If you deploy behind a reverse proxy,
        // configure Spring's forwarded header support so remoteAddr is the client IP.
        String ip = request.getRemoteAddr();
        return (ip == null || ip.isBlank()) ? "unknown" : ip;
    }

    private static String sanitizeReturnTo(String returnTo) {
        if (returnTo == null) {
            return null;
        }
        String value = returnTo.trim();
        if (value.isBlank()) {
            return null;
        }
        // Allow only same-site absolute paths.
        if (!value.startsWith("/")) {
            return null;
        }
        if (value.startsWith("//")) {
            return null;
        }
        if (value.contains("://")) {
            return null;
        }
        if (value.contains("\r") || value.contains("\n")) {
            return null;
        }
        return value;
    }

    private static String withReturnTo(String baseUrl, String returnTo) {
        String safe = sanitizeReturnTo(returnTo);
        if (safe == null) {
            return baseUrl;
        }
        String glue = baseUrl.contains("?") ? "&" : "?";
        return baseUrl + glue + "returnTo=" + URLEncoder.encode(safe, StandardCharsets.UTF_8);
    }

    private static void writeJsonError(HttpServletResponse response, HttpStatus status, String code, String message)
            throws java.io.IOException {
        response.setStatus(status.value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"status\":" + status.value()
                + ",\"code\":\"" + jsonEscape(code) + "\""
                + ",\"message\":\"" + jsonEscape(message) + "\"}");
    }

    private static String jsonEscape(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private org.springframework.security.authorization.AuthorizationDecision hasMinimumLevel(
            Authentication authentication, int requiredLevel) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return new org.springframework.security.authorization.AuthorizationDecision(false);
        }

        try {
            User user = userDao.findEmployeeByUsername(authentication.getName()).orElse(null);
            if (user == null || user.getRoles().isEmpty()) {
                return new org.springframework.security.authorization.AuthorizationDecision(false);
            }

            int userLevel = user.getRoles().stream()
                    .mapToInt(role -> role.getLevel())
                    .max()
                    .orElse(0);

            return new org.springframework.security.authorization.AuthorizationDecision(userLevel >= requiredLevel);
        } catch (Exception e) {
            return new org.springframework.security.authorization.AuthorizationDecision(false);
        }
    }

    private String determineEmployeeRedirectUrl(Authentication authentication) {
        try {
            User user = userDao.findEmployeeByUsername(authentication.getName()).orElse(null);
            if (user == null || user.getRoles().isEmpty()) {
                return "/employee";
            }

            int level = user.getRoles().stream()
                    .mapToInt(role -> role.getLevel())
                    .max()
                    .orElse(0);

            if (level >= 99) {
                return "/employee/admin/dashboard";
            } else if (level >= 30) {
                return "/employee/manager/dashboard";
            } else if (level >= 20) {
                return "/employee/it/dashboard";
            } else {
                return "/employee";  // 基本員工導向 /employee
            }
        } catch (Exception e) {
            return "/employee";
        }
    }

    private void handleMemberPostLogin(HttpSession session, Authentication authentication) {
        UserService userService = userServiceProvider.getIfAvailable();
        if (userService == null) {
            return;
        }

        if (!hasAuthority(authentication.getAuthorities(), "ROLE_MEMBER")) {
            return;
        }

        @SuppressWarnings("unchecked")
        Set<Long> watchlist = (Set<Long>) session.getAttribute(sessionService.guestWatchlistKey());
        if (watchlist != null && !watchlist.isEmpty()) {
            userService.mergeGuestWatchlistIntoUser(authentication.getName(), watchlist);
            session.removeAttribute(sessionService.guestWatchlistKey());
        }
    }

    private boolean hasAuthority(java.util.Collection<? extends GrantedAuthority> authorities, String code) {
        if (authorities == null) {
            return false;
        }
        return authorities.stream()
                .anyMatch(granted -> code.equalsIgnoreCase(granted.getAuthority()));
    }
}

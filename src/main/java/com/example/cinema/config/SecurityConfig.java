package com.example.cinema.config;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import com.example.cinema.dao.UserDao;
import com.example.cinema.model.User;
import com.example.cinema.service.LoginAttemptService;
import com.example.cinema.service.SessionService;
import com.example.cinema.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final UserDao userDao;
    private final SessionService sessionService;
    private final LoginAttemptService loginAttemptService;
    private final ObjectProvider<UserService> userServiceProvider;

    public SecurityConfig(UserDao userDao,
            SessionService sessionService,
            LoginAttemptService loginAttemptService,
            ObjectProvider<UserService> userServiceProvider) {
        this.userDao = userDao;
        this.sessionService = sessionService;
        this.loginAttemptService = loginAttemptService;
        this.userServiceProvider = userServiceProvider;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
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

                .csrf(csrf -> csrf.ignoringRequestMatchers("/employee/**"))
                .formLogin(login -> login
                        .loginPage("/employee/login").permitAll()
                        .loginProcessingUrl("/employee/login")
                        .failureHandler((request, response, exception) -> {
                            String username = request.getParameter("username");
                            String clientIp = getClientIp(request);

                            boolean exists = false;
                            if (username != null && !username.isBlank()) {
                                exists = userDao.findByUsername(username.trim()).isPresent();
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
                        .requestMatchers("/member/login", "/member/login/").permitAll()
                        .requestMatchers("/member/**").hasRole("MEMBER")
                        .anyRequest().authenticated())
                .csrf(csrf -> csrf.ignoringRequestMatchers("/member/**"))
                .formLogin(login -> login
                        .loginPage("/member/login").permitAll()
                        .loginProcessingUrl("/member/login")
                        .failureHandler((request, response, exception) -> {
                            String username = request.getParameter("username");
                            String clientIp = getClientIp(request);

                            boolean exists = false;
                            if (username != null && !username.isBlank()) {
                                exists = userDao.findByUsername(username.trim()).isPresent();
                            }

                            String attemptKey = exists ? username : clientIp;
                            var status = loginAttemptService.registerFailure(SessionService.Realm.MEMBER, attemptKey);
                            HttpSession session = request.getSession(true);
                            sessionService.applyFailureFeedback(session, SessionService.Realm.MEMBER, status);
                            response.sendRedirect("/member/login?error");
                        })
                        .successHandler((request, response, authentication) -> {
                            String username = authentication.getName();
                            String clientIp = getClientIp(request);

                            // ✅ 帳號鎖定檢查已移至 userDetailsService()，這裡只檢查 IP 鎖定
                            // 檢查 IP 是否被鎖定
                            var ipStatus = loginAttemptService.getStatus(SessionService.Realm.MEMBER, clientIp);
                            if (ipStatus != null && ipStatus.locked()) {
                                HttpSession session = request.getSession(true);
                                sessionService.applyFailureFeedback(session, SessionService.Realm.MEMBER, ipStatus);
                                SecurityContextHolder.clearContext();
                                response.sendRedirect("/member/login?error");
                                return;
                            }

                            HttpSession session = request.getSession(true);
                            loginAttemptService.registerSuccess(SessionService.Realm.MEMBER, username);
                            loginAttemptService.registerSuccess(SessionService.Realm.MEMBER, clientIp);
                            sessionService.establishSession(session, SessionService.Realm.MEMBER);
                            sessionService.resetAttempts(session, SessionService.Realm.MEMBER);
                            handleMemberPostLogin(session, authentication);
                            response.sendRedirect("/member");
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
                .requestMatchers("/api/guest/**", "/api/movies/**").permitAll()
                .requestMatchers("/movies/**").permitAll() // 加這行
                .anyRequest().authenticated())
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**", "/api/guest/**"));
        return http.build();
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip != null ? ip : "unknown";
    }

    private org.springframework.security.authorization.AuthorizationDecision hasMinimumLevel(
            Authentication authentication, int requiredLevel) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return new org.springframework.security.authorization.AuthorizationDecision(false);
        }

        try {
            User user = userDao.findByUsername(authentication.getName()).orElse(null);
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
            User user = userDao.findByUsername(authentication.getName()).orElse(null);
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

        userService.ensureBasicRole(authentication.getName());
    }

    private boolean hasAuthority(java.util.Collection<? extends GrantedAuthority> authorities, String code) {
        if (authorities == null) {
            return false;
        }
        return authorities.stream()
                .anyMatch(granted -> code.equalsIgnoreCase(granted.getAuthority()));
    }
}

package com.example.cinema.config;

import com.example.cinema.config.SessionConstants;
import com.example.cinema.dao.UserDao;
import com.example.cinema.service.LoginAttemptService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final UserDao userDao;
    @Autowired
    private ObjectProvider<com.example.cinema.service.UserService> userServiceProvider;
    @Autowired
    private LoginAttemptService loginAttemptService;

    public SecurityConfig(UserDao userDao) {
        this.userDao = userDao;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Use DelegatingPasswordEncoder so stored hashes have {bcrypt} prefix
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            com.example.cinema.model.User user = userDao.findByUsername(username)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found with username: " + username));

            return new org.springframework.security.core.userdetails.User(
                    user.getUsername(),
                    user.getPasswordHash(),
                    user.getRoles().stream()
                            .map(role -> new SimpleGrantedAuthority(role.getCode()))
                            .collect(Collectors.toList())
            );
        };
    }

    
    
    
    @Bean
    @Order(1)
    public SecurityFilterChain employeeSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/employee/**", "/employee/login", "/employee/login/**")
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/employee/login", "/employee/login/**").permitAll()
                .requestMatchers("/", "/index.html", "/login", "/error",
                                 "/css/**", "/js/**", "/images/**", "/favicon.ico",
                                 "/webjars/**", "/assets/**").permitAll()
                .anyRequest().authenticated()
            )
            .csrf(csrf -> csrf.ignoringRequestMatchers("/employee/**"))
            .formLogin(login -> login
                .loginPage("/employee/login").permitAll()
                .loginProcessingUrl("/employee/login")
                .failureHandler((req, res, ex) -> {
                    String username = req.getParameter("username");
                    String normalized = (username == null) ? "_anonymous" : username.trim().toLowerCase(java.util.Locale.ROOT);
                    var afterFailure = loginAttemptService.registerFailure("employee:" + normalized);
                    var session = req.getSession(true);
                    Object c = session.getAttribute(SessionConstants.EMPLOYEE_ATTEMPT_COUNT);
                    int next = (c instanceof Integer) ? ((Integer) c) + 1 : 1;
                    if (next < 5) {
                        int remaining = Math.max(0, 5 - next);
                        session.setAttribute("employeeLoginErrorText", "帳號或密碼不正確，還可以再嘗試 " + remaining + " 次。");
                        session.setAttribute(SessionConstants.EMPLOYEE_ATTEMPT_COUNT, next);
                    }
                    if (afterFailure.locked() || next >= 5) {
                        var dur = afterFailure.lockDuration();
                        java.time.Duration effective = (dur == null || dur.isZero() || dur.isNegative())
                                ? java.time.Duration.ofMinutes(10) : dur;
                        java.time.Instant lockUntil = java.time.Instant.now().plus(effective);
                        session.setAttribute(SessionConstants.EMPLOYEE_LOCK_UNTIL, lockUntil);
                        session.setAttribute(SessionConstants.EMPLOYEE_ATTEMPT_COUNT, 5);
                        session.removeAttribute("employeeLoginErrorText");
                    }
                    res.sendRedirect("/employee/login?error");
                })
                .successHandler((req, res, auth) -> {
                    String normalized = (auth.getName() == null) ? "_anonymous" : auth.getName().trim().toLowerCase(java.util.Locale.ROOT);
                    loginAttemptService.registerSuccess("employee:" + normalized);
                    var session = req.getSession(true);
                    session.setAttribute(SessionConstants.EMPLOYEE_SESSION_KEY, true);
                    session.setAttribute(SessionConstants.EMPLOYEE_ACCESS_TOKEN, java.util.UUID.randomUUID().toString());
                    session.setAttribute(SessionConstants.EMPLOYEE_LAST_ACTIVITY, java.time.Instant.now());
                    session.removeAttribute(SessionConstants.EMPLOYEE_ATTEMPT_COUNT);
                    session.removeAttribute(SessionConstants.EMPLOYEE_LOCK_UNTIL);
                    res.sendRedirect("/employee");
                })
            )
            .logout(logout -> logout
                .logoutUrl("/employee/logout")
                .logoutSuccessUrl("/")
                .permitAll()
            );
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/index.html", "/login", "/error",
                                 "/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()
                .requestMatchers("/webjars/**", "/assets/**").permitAll()
                .requestMatchers("/api/guest/**", "/api/movies/**").permitAll()
                .requestMatchers("/member/login", "/member/login/**").permitAll()
                .requestMatchers("/logout").permitAll()
                .anyRequest().authenticated()
            )
            .csrf(csrf -> csrf.ignoringRequestMatchers(
                "/employee/**", "/member/**", "/api/**", "/api/guest/**"
            ))
            .formLogin(login -> login
                .loginProcessingUrl("/member/login")
                .loginPage("/member/login").permitAll()
                .failureHandler((req, res, ex) -> {
                    String username = req.getParameter("username");
                    String normalized = (username == null) ? "_anonymous" : username.trim().toLowerCase(Locale.ROOT);
                    String attemptKey = "member:" + normalized;
                    var afterFailure = loginAttemptService.registerFailure(attemptKey);
                    var session = req.getSession(true);
                    Object c = session.getAttribute(SessionConstants.MEMBER_ATTEMPT_COUNT);
                    int next = (c instanceof Integer) ? ((Integer) c) + 1 : 1;
                    if (next < 5) {
                        int remaining = Math.max(0, 5 - next);
                        session.setAttribute("memberLoginErrorText", "帳號或密碼不正確，還可以再嘗試 " + remaining + " 次。");
                        session.setAttribute(SessionConstants.MEMBER_ATTEMPT_COUNT, next);
                    }
                    if (afterFailure.locked() || next >= 5) {
                        var dur = afterFailure.lockDuration();
                        Duration effective = (dur == null || dur.isZero() || dur.isNegative())
                                ? Duration.ofMinutes(10) : dur;
                        Instant lockUntil = Instant.now().plus(effective);
                        session.setAttribute(SessionConstants.MEMBER_LOCK_UNTIL, lockUntil);
                        session.setAttribute(SessionConstants.MEMBER_ATTEMPT_COUNT, 5);
                        session.removeAttribute("memberLoginErrorText");
                    }
                    res.sendRedirect("/member/login?error");
                })
                .successHandler((req, res, auth) -> {
                    var userService = userServiceProvider.getIfAvailable();
                    var session = req.getSession(true);
                    // Reset login attempts on success
                    String normalized = (auth.getName() == null) ? "_anonymous" : auth.getName().trim().toLowerCase(Locale.ROOT);
                    loginAttemptService.registerSuccess("member:" + normalized);
                    if (session != null) {
                        @SuppressWarnings("unchecked")
                        Set<Long> set = (Set<Long>) session.getAttribute(SessionConstants.GUEST_WATCHLIST);
                        if (userService != null && set != null && !set.isEmpty()) {
                            userService.mergeGuestWatchlistIntoUser(auth.getName(), set);
                        }
                        session.removeAttribute(SessionConstants.GUEST_WATCHLIST);
                        session.setAttribute(SessionConstants.MEMBER_SESSION_KEY, true);
                        session.setAttribute(SessionConstants.MEMBER_ACCESS_TOKEN, UUID.randomUUID().toString());
                        session.setAttribute(SessionConstants.MEMBER_LAST_ACTIVITY, Instant.now());
                        session.removeAttribute(SessionConstants.MEMBER_ATTEMPT_COUNT);
                        session.removeAttribute(SessionConstants.MEMBER_LOCK_UNTIL);
                    }
                    if (userService != null) {
                        userService.ensureBasicRole(auth.getName());
                    }
                    res.sendRedirect("/member");
                })
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/")
                .permitAll()
            );
        return http.build();
    }
}

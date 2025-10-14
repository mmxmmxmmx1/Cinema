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
    public UserDetailsService userDetailsService() {
        return username -> {
            User user = userDao.findByUsername(username)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found with username: " + username));

            return new org.springframework.security.core.userdetails.User(
                    user.getUsername(),
                    user.getPassword(),
                    user.getRoles().stream()
                            .map(role -> new SimpleGrantedAuthority(role.getCode()))
                            .collect(Collectors.toList()));
        };
    }

    @Bean
    @Order(1)
    public SecurityFilterChain employeeSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/employee/**")
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/employee/login", "/employee/login/").permitAll()
                        .requestMatchers("/employee/**").hasRole("EMPLOYEE")
                        .anyRequest().authenticated())
                .csrf(csrf -> csrf.ignoringRequestMatchers("/employee/**"))
                .formLogin(login -> login
                        .loginPage("/employee/login").permitAll()
                        .loginProcessingUrl("/employee/login")
                        .failureHandler((request, response, exception) -> {
                            String username = request.getParameter("username");
                            var status = loginAttemptService.getStatus(SessionService.Realm.EMPLOYEE, username);
                            HttpSession session = request.getSession(true);
                            sessionService.applyFailureFeedback(session, SessionService.Realm.EMPLOYEE, status);
                            response.sendRedirect("/employee/login?error");
                        })
                        .successHandler((request, response, authentication) -> {
                            String username = authentication.getName();
                            HttpSession session = request.getSession(true);

                            loginAttemptService.registerSuccess(SessionService.Realm.EMPLOYEE, username);
                            sessionService.establishSession(session, SessionService.Realm.EMPLOYEE);
                            sessionService.resetAttempts(session, SessionService.Realm.EMPLOYEE);

                            response.sendRedirect("/employee");
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
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/member/login", "/member/login/").permitAll()
                        .requestMatchers("/member/**").hasRole("USER")
                        .anyRequest().authenticated())
                .csrf(csrf -> csrf.ignoringRequestMatchers("/member/**"))
                .formLogin(login -> login
                        .loginPage("/member/login").permitAll()
                        .loginProcessingUrl("/member/login")
                        .failureHandler((request, response, exception) -> {
                            String username = request.getParameter("username");
                            var status = loginAttemptService.getStatus(SessionService.Realm.MEMBER, username);
                            HttpSession session = request.getSession(true);
                            sessionService.applyFailureFeedback(session, SessionService.Realm.MEMBER, status);
                            response.sendRedirect("/member/login?error");
                        })
                        .successHandler((request, response, authentication) -> {
                            String username = authentication.getName();
                            HttpSession session = request.getSession(true);

                            loginAttemptService.registerSuccess(SessionService.Realm.MEMBER, username);
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
                .anyRequest().authenticated())
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**", "/api/guest/**"));

        return http.build();
    }

    private void handleMemberPostLogin(HttpSession session, Authentication authentication) {
        UserService userService = userServiceProvider.getIfAvailable();
        if (userService == null)
            return;

        if (!hasAuthority(authentication.getAuthorities(), "ROLE_USER")) {
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
        if (authorities == null)
            return false;
        return authorities.stream().anyMatch(granted -> code.equalsIgnoreCase(granted.getAuthority()));
    }
}

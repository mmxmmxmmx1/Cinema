package com.example.cinema.config;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.ObjectProvider;
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

import com.example.cinema.dao.UserDao;
import com.example.cinema.model.User;
import com.example.cinema.service.SessionService;
import com.example.cinema.service.UserService;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final UserDao userDao;
    private final LoginFlowHandlers loginFlowHandlers;
    private final ObjectProvider<UserService> userServiceProvider;

    public SecurityConfig(UserDao userDao,
                          LoginFlowHandlers loginFlowHandlers,
                          ObjectProvider<UserService> userServiceProvider) {
        this.userDao = userDao;
        this.loginFlowHandlers = loginFlowHandlers;
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
                            .collect(Collectors.toList())
            );
        };
    }

    @Bean
    @Order(1)
    public SecurityFilterChain employeeSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/employee/**", "/employee/login", "/employee/login/**")
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/employee/login", "/employee/login/**").permitAll()
                .requestMatchers("/", "/index.html", "/login", "/error",
                                 "/css/**", "/js/**", "/images/**", "/favicon.ico",
                                 "/webjars/**", "/assets/**").permitAll()
                .anyRequest().authenticated())
            .csrf(csrf -> csrf.ignoringRequestMatchers("/employee/**"))
            .formLogin(login -> login
                .loginPage("/employee/login").permitAll()
                .loginProcessingUrl("/employee/login")
                .failureHandler(loginFlowHandlers.failureHandler(SessionService.Realm.EMPLOYEE, "/employee/login?error"))
                .successHandler(loginFlowHandlers.successHandler(SessionService.Realm.EMPLOYEE, "/employee", null))
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
    public SecurityFilterChain memberSecurityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/index.html", "/login", "/error",
                                 "/css/**", "/js/**", "/images/**", "/favicon.ico",
                                 "/webjars/**", "/assets/**").permitAll()
                .requestMatchers("/api/guest/**", "/api/movies/**").permitAll()
                .requestMatchers("/member/login", "/member/login/**").permitAll()
                .requestMatchers("/logout").permitAll()
                .anyRequest().authenticated())
            .csrf(csrf -> csrf.ignoringRequestMatchers(
                    "/employee/**", "/member/**", "/api/**", "/api/guest/**"
            ))
            .formLogin(login -> login
                .loginProcessingUrl("/member/login")
                .loginPage("/member/login").permitAll()
                .failureHandler(loginFlowHandlers.failureHandler(SessionService.Realm.MEMBER, "/member/login?error"))
                .successHandler(loginFlowHandlers.successHandler(
                        SessionService.Realm.MEMBER,
                        "/member",
                        (session, authentication) -> {
                            UserService userService = userServiceProvider.getIfAvailable();
                            if (userService != null) {
                                @SuppressWarnings("unchecked")
                                Set<Long> watchlist = (Set<Long>) session.getAttribute(loginFlowHandlers.sessionService().guestWatchlistKey());
                                if (watchlist != null && !watchlist.isEmpty()) {
                                    userService.mergeGuestWatchlistIntoUser(authentication.getName(), watchlist);
                                }
                                session.removeAttribute(loginFlowHandlers.sessionService().guestWatchlistKey());
                                userService.ensureBasicRole(authentication.getName());
                            }
                        }))
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/")
                .permitAll()
            );
        return http.build();
    }
}

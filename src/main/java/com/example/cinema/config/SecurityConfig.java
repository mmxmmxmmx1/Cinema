package com.example.cinema.config;

import com.example.cinema.dao.UserDao;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final UserDao userDao;

    public SecurityConfig(UserDao userDao) {
        this.userDao = userDao;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
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
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/index.html", "/css/**", "/js/**", "/images/**").permitAll()
                .requestMatchers("/api/movies/**").permitAll()
                // permit login pages with slash style
                .requestMatchers("/member/login", "/member/login/**").permitAll()
                .requestMatchers("/employee/login", "/employee/login/**").permitAll()
                .requestMatchers("/login", "/logout").permitAll()
                .anyRequest().authenticated()
            )
            .csrf(csrf -> csrf.ignoringRequestMatchers(
                "/employee/**", "/member/**", "/api/**"
            ))
            .formLogin(form -> form
                .loginProcessingUrl("/member/login")
                .loginPage("/member/login") // keep your custom member login page if used elsewhere
                .loginProcessingUrl("/login")
                .defaultSuccessUrl("/member", true)
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/")
                .permitAll()
            );
        return http.build();
    }
}

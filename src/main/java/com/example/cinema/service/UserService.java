package com.example.cinema.service;

import com.example.cinema.dao.UserDao;
import com.example.cinema.model.User;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final UserDao userDao;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    public UserService(UserDao userDao, PasswordEncoder passwordEncoder, JdbcTemplate jdbcTemplate) {
        this.userDao = userDao;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
    }

    public Long registerUser(String username, String rawPassword, boolean asAdmin) {
        String hash = passwordEncoder.encode(rawPassword);
        Long userId = userDao.createUser(username, hash);
        userDao.assignRole(userId, "ROLE_USER");
        if (asAdmin) {
            userDao.assignRole(userId, "ROLE_ADMIN");
        }
        return userId;
    }

    @Transactional
    public void mergeGuestWatchlistIntoUser(String username, Collection<Long> movieIds) {
        if (movieIds == null || movieIds.isEmpty()) {
            return;
        }
        User user = userDao.findByUsername(username).orElse(null);
        if (user == null) {
            return;
        }

        Set<Long> deduplicated = movieIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        if (deduplicated.isEmpty()) {
            return;
        }

        Set<Long> existing;
        try {
            existing = new HashSet<>(jdbcTemplate.queryForList(
                    "SELECT movie_id FROM user_watchlist WHERE user_id = ?",
                    Long.class,
                    user.getId()
            ));
        } catch (DataAccessException ex) {
            existing = new HashSet<>();
        }

        for (Long movieId : deduplicated) {
            if (!existing.contains(movieId)) {
                try {
                    jdbcTemplate.update(
                            "INSERT INTO user_watchlist (user_id, movie_id) VALUES (?, ?)",
                            user.getId(),
                            movieId
                    );
                } catch (DataAccessException ignore) {
                    // Swallow exception to avoid breaking login flow when watchlist persistence is unavailable
                }
            }
        }
    }

    @Transactional
    public void ensureBasicRole(String username) {
        User user = userDao.findByUsername(username).orElse(null);
        if (user == null) {
            return;
        }

        if (user.getRoles() != null && !user.getRoles().isEmpty()) {
            return;
        }

        Long roleId;
        try {
            roleId = jdbcTemplate.queryForObject(
                    "SELECT id FROM roles WHERE code = ?",
                    Long.class,
                    "ROLE_CUSTOMER"
            );
        } catch (EmptyResultDataAccessException ex) {
            jdbcTemplate.update(
                    "INSERT INTO roles (code, name) VALUES (?, ?)",
                    "ROLE_CUSTOMER",
                    "Customer"
            );
            roleId = jdbcTemplate.queryForObject(
                    "SELECT id FROM roles WHERE code = ?",
                    Long.class,
                    "ROLE_CUSTOMER"
            );
        } catch (DataAccessException ex) {
            return;
        }

        try {
            jdbcTemplate.update(
                    "INSERT IGNORE INTO user_roles (user_id, role_id) VALUES (?, ?)",
                    user.getId(),
                    roleId
            );
        } catch (DataAccessException ignore) {
            // if role assignment fails we simply skip to keep authentication flow alive
        }
    }
}


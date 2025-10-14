package com.example.cinema.service;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.cinema.dao.UserDao;
import com.example.cinema.model.User;
import com.example.cinema.model.User.UserType;

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

    public Long registerUser(String username, String rawPassword, UserType userType, boolean asAdmin) {
        String hash = passwordEncoder.encode(rawPassword);
        Long userId = userDao.createUser(username, hash, userType);
        userDao.assignRole(userId, "ROLE_USER");
        if (asAdmin) {
            userDao.assignRole(userId, "ROLE_ADMIN");
        }
        return userId;
    }

    public Long registerEmployee(String username, String rawPassword) {
        String hash = passwordEncoder.encode(rawPassword);
        Long userId = userDao.createUser(username, hash, UserType.EMPLOYEE);
        userDao.assignRole(userId, "ROLE_EMPLOYEE");
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
                    user.getId()));
        } catch (DataAccessException ex) {
            existing = new HashSet<>();
        }

        for (Long movieId : deduplicated) {
            if (!existing.contains(movieId)) {
                try {
                    jdbcTemplate.update(
                            "INSERT INTO user_watchlist (user_id, movie_id) VALUES (?, ?)",
                            user.getId(),
                            movieId);
                } catch (DataAccessException ignore) {
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
                    "ROLE_USER");
        } catch (EmptyResultDataAccessException ex) {
            jdbcTemplate.update(
                    "INSERT INTO roles (code, name) VALUES (?, ?)",
                    "ROLE_USER",
                    "一般使用者");
            roleId = jdbcTemplate.queryForObject(
                    "SELECT id FROM roles WHERE code = ?",
                    Long.class,
                    "ROLE_USER");
        } catch (DataAccessException ex) {
            return;
        }

        try {
            jdbcTemplate.update(
                    "INSERT IGNORE INTO user_roles (user_id, role_id) VALUES (?, ?)",
                    user.getId(),
                    roleId);
        } catch (DataAccessException ignore) {
        }
    }
}

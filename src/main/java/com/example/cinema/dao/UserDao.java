package com.example.cinema.dao;

import com.example.cinema.model.Role;
import com.example.cinema.model.User;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
public class UserDao {

    private final JdbcTemplate jdbcTemplate;

    public UserDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<User> findByUsername(String username) {
        String userSql = "SELECT * FROM users WHERE username = ?";
        try {
            // 1. Find the user
            User user = jdbcTemplate.queryForObject(userSql, new UserMapper(), username);

            // 2. Find the user's roles
            String rolesSql = "SELECT r.id, r.code, r.name FROM roles r " +
                              "JOIN user_roles ur ON r.id = ur.role_id " +
                              "WHERE ur.user_id = ?";
            List<Role> roles = jdbcTemplate.query(rolesSql, new RoleMapper(), user.getId());

            // 3. Create the final User object with roles
            User finalUser = new User(
                user.getId(),
                user.getUsername(),
                user.getPasswordHash(),
                user.getCreatedAt(),
                roles
            );
            return Optional.of(finalUser);

        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    private static class UserMapper implements RowMapper<User> {
        @Override
        public User mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new User(
                    rs.getLong("id"),
                    rs.getString("username"),
                    rs.getString("password_hash"),
                    rs.getTimestamp("created_at").toLocalDateTime(),
                    null // Roles are loaded separately
            );
        }
    }

    private static class RoleMapper implements RowMapper<Role> {
        @Override
        public Role mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new Role(
                    rs.getLong("id"),
                    rs.getString("code"),
                    rs.getString("name")
            );
        }
    }

    public Long createUser(String username, String passwordHash) {
        jdbcTemplate.update(
                "INSERT INTO users (username, password_hash) VALUES (?, ?)",
                username, passwordHash
        );
        // 取得新 user 的 id（假設 username 為唯一鍵）
        return jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?",
                Long.class, username
        );
    }

    public void assignRole(Long userId, String roleCode) {
        Long roleId = jdbcTemplate.queryForObject(
                "SELECT id FROM roles WHERE code = ?",
                Long.class, roleCode
        );
        jdbcTemplate.update(
                "INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)",
                userId, roleId
        );
    }

}

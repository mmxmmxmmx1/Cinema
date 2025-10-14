package com.example.cinema.dao;

import com.example.cinema.model.Role;
import com.example.cinema.model.User;
import com.example.cinema.model.User.UserType;
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
            User user = jdbcTemplate.queryForObject(userSql, new UserMapper(), username);

            String rolesSql = "SELECT r.id, r.code, r.name FROM roles r " +
                    "JOIN user_roles ur ON r.id = ur.role_id " +
                    "WHERE ur.user_id = ?";
            List<Role> roles = jdbcTemplate.query(rolesSql, new RoleMapper(), user.getId());

            User finalUser = new User(
                    user.getId(),
                    user.getUsername(),
                    user.getFirstName(),
                    user.getLastName(),
                    user.getEmail(),
                    user.getPhone(),
                    user.getPassword(),
                    user.getUserType(),
                    user.getCreatedAt(),
                    roles);
            return Optional.of(finalUser);

        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    private static class UserMapper implements RowMapper<User> {
        @Override
        public User mapRow(ResultSet rs, int rowNum) throws SQLException {
            String userTypeStr = rs.getString("user_type");
            UserType userType = userTypeStr != null ? UserType.valueOf(userTypeStr) : UserType.CUSTOMER;

            return new User(
                    rs.getLong("id"),
                    rs.getString("username"),
                    rs.getString("first_name"),
                    rs.getString("last_name"),
                    rs.getString("email"),
                    rs.getString("phone"),
                    rs.getString("password"),
                    userType,
                    rs.getTimestamp("created_at").toLocalDateTime(),
                    null);
        }
    }

    private static class RoleMapper implements RowMapper<Role> {
        @Override
        public Role mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new Role(
                    rs.getLong("id"),
                    rs.getString("code"),
                    rs.getString("name"));
        }
    }

    public Long createUser(String username, String encodedPassword, UserType userType) {
        jdbcTemplate.update(
                "INSERT INTO users (username, password, user_type) VALUES (?, ?, ?)",
                username, encodedPassword, userType.name());
        return jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?",
                Long.class, username);
    }

    public void assignRole(Long userId, String roleCode) {
        Long roleId = jdbcTemplate.queryForObject(
                "SELECT id FROM roles WHERE code = ?",
                Long.class, roleCode);
        jdbcTemplate.update(
                "INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)",
                userId, roleId);
    }

    public void updateUserType(Long userId, UserType userType) {
        jdbcTemplate.update(
                "UPDATE users SET user_type = ? WHERE id = ?",
                userType.name(), userId);
    }
}

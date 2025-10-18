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
        // 先從 members 表查詢
        String memberSql = "SELECT * FROM members WHERE nickname = ?";
        try {
            User member = jdbcTemplate.queryForObject(memberSql, new MemberMapper(), username);
            return Optional.ofNullable(member);
        } catch (EmptyResultDataAccessException e) {
            // 會員不存在，繼續查詢員工
        }

        // 從 employee 表查詢（JOIN roles 取得 level）
        String employeeSql = "SELECT e.*, r.id as role_id, r.code as role_code, r.name as role_name, " +
                "r.description as role_desc, r.level as role_level " +
                "FROM employee e " +
                "JOIN roles r ON e.role_id = r.id " +
                "WHERE e.nickname = ?";
        try {
            User employee = jdbcTemplate.queryForObject(employeeSql, new EmployeeWithRoleMapper(), username);
            return Optional.ofNullable(employee);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    private static class MemberMapper implements RowMapper<User> {
        @Override
        public User mapRow(ResultSet rs, int rowNum) throws SQLException {
            // 會員固定 MEMBER 角色（SecurityConfig 會自動加上 ROLE_ 前綴）
            Role memberRole = new Role(5L, "MEMBER", "Member", "Regular member", 1);

            return new User(
                    rs.getLong("id"),
                    rs.getString("nickname"),
                    rs.getString("first_name"),
                    rs.getString("last_name"),
                    rs.getString("email"),
                    rs.getString("phone"),
                    rs.getString("password"),
                    UserType.MEMBER,
                    rs.getTimestamp("created_at").toLocalDateTime(),
                    List.of(memberRole) // 在建構子中傳入 roles
            );
        }
    }

    private static class EmployeeWithRoleMapper implements RowMapper<User> {
        @Override
        public User mapRow(ResultSet rs, int rowNum) throws SQLException {
            // 從查詢結果建立 Role
            Role role = new Role(
                    rs.getLong("role_id"),
                    rs.getString("role_code"),
                    rs.getString("role_name"),
                    rs.getString("role_desc"),
                    rs.getInt("role_level"));

            return new User(
                    rs.getLong("id"),
                    rs.getString("nickname"),
                    rs.getString("first_name"),
                    rs.getString("last_name"),
                    rs.getString("email"),
                    rs.getString("phone"),
                    rs.getString("password"),
                    UserType.EMPLOYEE,
                    rs.getTimestamp("created_at").toLocalDateTime(),
                    List.of(role) // 在建構子中傳入 roles
            );
        }
    }

    public Long createUser(String username, String encodedPassword, UserType userType) {
        if (userType == UserType.MEMBER) {
            // 插入會員
            jdbcTemplate.update(
                    "INSERT INTO members (nickname, password, first_name, last_name) VALUES (?, ?, '', '')",
                    username, encodedPassword);
            return jdbcTemplate.queryForObject(
                    "SELECT id FROM members WHERE nickname = ?",
                    Long.class, username);
        } else {
            // 插入員工（預設 role_id = 1, EMPLOYEE）
            jdbcTemplate.update(
                    "INSERT INTO employee (nickname, password, first_name, last_name, role_id) VALUES (?, ?, '', '', ?)",
                    username, encodedPassword, 1L);
            return jdbcTemplate.queryForObject(
                    "SELECT id FROM employee WHERE nickname = ?",
                    Long.class, username);
        }
    }

    public void assignRole(Long userId, String roleCode) {
        Long roleId = jdbcTemplate.queryForObject(
                "SELECT id FROM roles WHERE code = ?",
                Long.class, roleCode);

        // 更新員工的 role_id
        jdbcTemplate.update(
                "UPDATE employee SET role_id = ? WHERE id = ?",
                roleId, userId);
    }

    /**
     * 只從 members 表查詢用戶
     */
    public Optional<User> findMemberByUsername(String username) {
        String memberSql = "SELECT * FROM members WHERE nickname = ?";
        try {
            User member = jdbcTemplate.queryForObject(memberSql, new MemberMapper(), username);
            return Optional.ofNullable(member);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    /**
     * 只從 employee 表查詢用戶
     */
    public Optional<User> findEmployeeByUsername(String username) {
        String employeeSql = "SELECT e.*, r.id as role_id, r.code as role_code, r.name as role_name, " +
                "r.description as role_desc, r.level as role_level " +
                "FROM employee e " +
                "JOIN roles r ON e.role_id = r.id " +
                "WHERE e.nickname = ?";
        try {
            User employee = jdbcTemplate.queryForObject(employeeSql, new EmployeeWithRoleMapper(), username);
            return Optional.ofNullable(employee);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
}

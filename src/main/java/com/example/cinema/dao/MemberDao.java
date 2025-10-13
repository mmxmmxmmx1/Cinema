package com.example.cinema.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MemberDao {

    private final JdbcTemplate jdbcTemplate;

    public MemberDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Long createMember(String firstName, String lastName, String email, String phone, String passwordHash) {
        jdbcTemplate.update(
                "INSERT INTO members (first_name, last_name, email, phone, password) VALUES (?, ?, ?, ?, ?)",
                firstName, lastName, email, phone, passwordHash
        );
        return jdbcTemplate.queryForObject(
                "SELECT id FROM members WHERE email = ?",
                Long.class, email
        );
    }
}


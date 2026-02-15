package com.example.cinema.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SystemHealthDao {

    private final JdbcTemplate jdbcTemplate;

    public SystemHealthDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Integer pingDatabase() {
        return jdbcTemplate.queryForObject("SELECT 1", Integer.class);
    }
}

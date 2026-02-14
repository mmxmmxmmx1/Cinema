package com.example.cinema.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.cinema.config.AppClock;
import com.example.cinema.filter.TraceIdFilter;

@RestController
@RequestMapping("/api")
public class SystemHealthController {

    private final JdbcTemplate jdbcTemplate;

    public SystemHealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/health")
    public Map<String, Object> health(@RequestAttribute(name = TraceIdFilter.TRACE_ID_ATTR, required = false) String traceId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("time", AppClock.nowInstant().toString());
        body.put("zone", AppClock.zoneId().toString());
        body.put("traceId", traceId);
        body.put("db", pingDb());
        return body;
    }

    private String pingDb() {
        try {
            Integer one = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return one != null && one.intValue() == 1 ? "UP" : "UNKNOWN";
        } catch (Exception ex) {
            return "DOWN";
        }
    }
}

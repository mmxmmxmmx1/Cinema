package com.example.cinema.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.cinema.config.AppClock;
import com.example.cinema.filter.TraceIdFilter;
import com.example.cinema.service.SystemHealthService;

@RestController
@RequestMapping({ "/api", "/api/v1" })
public class SystemHealthController {

    private final SystemHealthService systemHealthService;

    public SystemHealthController(SystemHealthService systemHealthService) {
        this.systemHealthService = systemHealthService;
    }

    @GetMapping("/health")
    public Map<String, Object> health(@RequestAttribute(name = TraceIdFilter.TRACE_ID_ATTR, required = false) String traceId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("time", AppClock.nowInstant().toString());
        body.put("zone", AppClock.zoneId().toString());
        body.put("traceId", traceId);
        body.put("db", systemHealthService.databaseStatus());
        return body;
    }
}

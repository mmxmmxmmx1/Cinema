package com.example.cinema.service;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.cinema.config.AppClock;

@Service
public class EmployeeTodoService {

    private static final int MAX_ITEMS = 20;
    private static final int MAX_ITEM_LENGTH = 255;
    private static final List<String> DEFAULT_ITEMS = List.of(
            "08:30 — 影廳巡檢與音響測試",
            "12:30 — 售票櫃台交接",
            "16:45 — 小賣部盤點");

    private final JdbcTemplate jdbcTemplate;

    public EmployeeTodoService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<String> listTodayTodos() {
        return listTodos(AppClock.today());
    }

    public List<String> listTodos(LocalDate date) {
        LocalDate safeDate = date == null ? AppClock.today() : date;
        List<String> rows = jdbcTemplate.queryForList(
                "SELECT item_text FROM employee_todos WHERE todo_date = ? ORDER BY line_no ASC",
                String.class,
                Date.valueOf(safeDate));
        if (rows == null || rows.isEmpty()) {
            return DEFAULT_ITEMS;
        }
        return rows;
    }

    @Transactional
    public void replaceTodayTodos(List<String> items, String updatedBy) {
        replaceTodos(AppClock.today(), items, updatedBy);
    }

    @Transactional
    public void replaceTodos(LocalDate date, List<String> items, String updatedBy) {
        LocalDate safeDate = date == null ? AppClock.today() : date;
        String safeUpdater = (updatedBy == null || updatedBy.isBlank()) ? "unknown" : updatedBy.trim();
        List<String> normalized = normalizeItems(items);
        if (normalized.isEmpty()) {
            normalized = new ArrayList<>(DEFAULT_ITEMS);
        }

        jdbcTemplate.update("DELETE FROM employee_todos WHERE todo_date = ?", Date.valueOf(safeDate));
        int lineNo = 1;
        for (String item : normalized) {
            jdbcTemplate.update(
                    "INSERT INTO employee_todos (todo_date, line_no, item_text, updated_by) VALUES (?, ?, ?, ?)",
                    Date.valueOf(safeDate),
                    lineNo++,
                    item,
                    safeUpdater);
        }
    }

    private List<String> normalizeItems(List<String> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        Set<String> dedup = new LinkedHashSet<>();
        for (String item : items) {
            if (item == null) {
                continue;
            }
            String value = item.trim();
            if (value.isEmpty()) {
                continue;
            }
            if (value.length() > MAX_ITEM_LENGTH) {
                value = value.substring(0, MAX_ITEM_LENGTH);
            }
            dedup.add(value);
            if (dedup.size() >= MAX_ITEMS) {
                break;
            }
        }
        return new ArrayList<>(dedup);
    }
}

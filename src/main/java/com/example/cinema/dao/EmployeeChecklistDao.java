package com.example.cinema.dao;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EmployeeChecklistDao {

    private final JdbcTemplate jdbcTemplate;

    public EmployeeChecklistDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ChecklistEntryRow> findByDate(LocalDate checkDate) {
        return jdbcTemplate.query(
                "SELECT auditorium, item_code, item_label, checked, note, updated_by, updated_at " +
                        "FROM auditorium_checklist WHERE check_date = ?",
                this::mapEntryRow,
                Date.valueOf(checkDate));
    }

    public int upsertChecklistEntry(
            LocalDate checkDate,
            String auditorium,
            String itemCode,
            String itemLabel,
            boolean checked,
            String note,
            String operator) {
        return jdbcTemplate.update(
                "INSERT INTO auditorium_checklist " +
                        "(check_date, auditorium, item_code, item_label, checked, note, updated_by, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, NOW()) " +
                        "ON DUPLICATE KEY UPDATE " +
                        "item_label = VALUES(item_label), checked = VALUES(checked), note = VALUES(note), " +
                        "updated_by = VALUES(updated_by), updated_at = NOW()",
                Date.valueOf(checkDate),
                auditorium,
                itemCode,
                itemLabel,
                checked ? 1 : 0,
                note,
                operator);
    }

    public List<ChecklistHistoryRow> findHistorySince(LocalDate startDate) {
        return jdbcTemplate.query(
                "SELECT check_date, auditorium, checked, updated_by, updated_at " +
                        "FROM auditorium_checklist WHERE check_date >= ? " +
                        "ORDER BY check_date DESC, updated_at DESC",
                this::mapHistoryRow,
                Date.valueOf(startDate));
    }

    private ChecklistEntryRow mapEntryRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        String updatedAtText = updatedAt == null ? "" : updatedAt.toLocalDateTime().toString().replace('T', ' ');
        return new ChecklistEntryRow(
                rs.getString("auditorium"),
                rs.getString("item_code"),
                rs.getString("item_label"),
                rs.getInt("checked") != 0,
                rs.getString("note"),
                rs.getString("updated_by"),
                updatedAtText);
    }

    private ChecklistHistoryRow mapHistoryRow(ResultSet rs, int rowNum) throws SQLException {
        return new ChecklistHistoryRow(
                rs.getDate("check_date").toLocalDate(),
                rs.getString("auditorium"),
                rs.getInt("checked") != 0,
                rs.getString("updated_by"),
                rs.getTimestamp("updated_at"));
    }

    public record ChecklistEntryRow(
            String auditorium,
            String itemCode,
            String itemLabel,
            boolean checked,
            String note,
            String updatedBy,
            String updatedAt) {
    }

    public record ChecklistHistoryRow(
            LocalDate checkDate,
            String auditorium,
            boolean checked,
            String updatedBy,
            Timestamp updatedAt) {
    }
}

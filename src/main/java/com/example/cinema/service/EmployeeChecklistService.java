package com.example.cinema.service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.example.cinema.dao.EmployeeChecklistDao;
import com.example.cinema.dao.EmployeeChecklistDao.ChecklistEntryRow;
import com.example.cinema.dao.EmployeeChecklistDao.ChecklistHistoryRow;

@Service
public class EmployeeChecklistService {

    private static final int MAX_NOTE_LENGTH = 255;

    private final EmployeeChecklistDao employeeChecklistDao;

    public EmployeeChecklistService(EmployeeChecklistDao employeeChecklistDao) {
        this.employeeChecklistDao = employeeChecklistDao;
    }

    public Map<String, ChecklistEntryData> loadEntriesByDate(LocalDate checkDate) {
        Map<String, ChecklistEntryData> map = new LinkedHashMap<>();
        for (ChecklistEntryRow row : employeeChecklistDao.findByDate(checkDate)) {
            map.put(key(row.auditorium(), row.itemCode()), new ChecklistEntryData(
                    row.auditorium(),
                    row.itemCode(),
                    row.itemLabel(),
                    row.checked(),
                    row.note() == null ? "" : row.note(),
                    row.updatedBy() == null ? "" : row.updatedBy(),
                    row.updatedAt() == null ? "" : row.updatedAt()));
        }
        return map;
    }

    public int saveChecklist(LocalDate checkDate, String operator, List<ChecklistEntryCommand> commands) {
        if (commands == null || commands.isEmpty()) {
            return 0;
        }
        int updatedRows = 0;
        for (ChecklistEntryCommand command : commands) {
            if (command == null || isBlank(command.auditorium()) || isBlank(command.itemCode())) {
                continue;
            }
            String itemLabel = isBlank(command.itemLabel()) ? command.itemCode() : command.itemLabel().trim();
            String note = trimToLength(command.note(), MAX_NOTE_LENGTH);
            updatedRows += employeeChecklistDao.upsertChecklistEntry(
                    checkDate,
                    command.auditorium().trim(),
                    command.itemCode().trim(),
                    itemLabel,
                    command.checked(),
                    note,
                    operator);
        }
        return updatedRows;
    }

    public List<ChecklistHistoryData> loadHistorySince(LocalDate startDate) {
        Map<String, HistoryAgg> aggMap = new LinkedHashMap<>();
        for (ChecklistHistoryRow row : employeeChecklistDao.findHistorySince(startDate)) {
            String aggKey = row.checkDate() + "|" + row.auditorium();
            HistoryAgg agg = aggMap.computeIfAbsent(aggKey, ignored -> new HistoryAgg(row.checkDate(), row.auditorium()));
            agg.total += 1;
            if (row.checked()) {
                agg.checked += 1;
            }

            Timestamp updatedAt = row.updatedAt();
            if (updatedAt != null && (agg.latestAt == null || updatedAt.after(agg.latestAt))) {
                agg.latestAt = updatedAt;
                agg.latestBy = row.updatedBy() == null ? "" : row.updatedBy();
            }
        }

        return aggMap.values().stream()
                .sorted(java.util.Comparator
                        .comparing((HistoryAgg h) -> h.date).reversed()
                        .thenComparing(h -> h.auditorium))
                .map(h -> {
                    int rate = h.total <= 0 ? 0 : (h.checked * 100 / h.total);
                    String latestAt = h.latestAt == null ? "" : h.latestAt.toLocalDateTime().toString().replace('T', ' ');
                    return new ChecklistHistoryData(
                            h.date.toString(),
                            h.auditorium,
                            h.checked,
                            h.total,
                            rate,
                            h.latestBy,
                            latestAt);
                })
                .toList();
    }

    private static String key(String auditorium, String itemCode) {
        return auditorium + "|" + itemCode;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String trimToLength(String value, int maxLength) {
        String safe = value == null ? "" : value.trim();
        if (safe.length() <= maxLength) {
            return safe;
        }
        return safe.substring(0, maxLength);
    }

    private static class HistoryAgg {
        private final LocalDate date;
        private final String auditorium;
        private int checked;
        private int total;
        private String latestBy = "";
        private Timestamp latestAt;

        private HistoryAgg(LocalDate date, String auditorium) {
            this.date = date;
            this.auditorium = auditorium;
        }
    }

    public record ChecklistEntryData(
            String auditorium,
            String itemCode,
            String itemLabel,
            boolean checked,
            String note,
            String updatedBy,
            String updatedAt) {
    }

    public record ChecklistEntryCommand(
            String auditorium,
            String itemCode,
            String itemLabel,
            boolean checked,
            String note) {
    }

    public record ChecklistHistoryData(
            String checkDate,
            String auditorium,
            int checked,
            int total,
            int completionRate,
            String latestBy,
            String latestAt) {
    }
}

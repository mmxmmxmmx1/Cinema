package com.example.cinema.service;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.cinema.config.AppClock;
import com.example.cinema.dto.MaintenanceRequestSummary;

@Service
public class MaintenanceRequestService {

    private static final DateTimeFormatter TRACKING_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final int MAX_NOTE_LEN = 500;
    private static final EnumMap<RequestStatus, Set<RequestStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(RequestStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(RequestStatus.OPEN, Set.of(RequestStatus.IN_PROGRESS, RequestStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(RequestStatus.IN_PROGRESS, Set.of(RequestStatus.RESOLVED, RequestStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(RequestStatus.RESOLVED, Set.of(RequestStatus.CLOSED, RequestStatus.IN_PROGRESS));
        ALLOWED_TRANSITIONS.put(RequestStatus.CLOSED, Set.of());
        ALLOWED_TRANSITIONS.put(RequestStatus.CANCELLED, Set.of());
    }

    private final JdbcTemplate jdbcTemplate;

    public MaintenanceRequestService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public String createRequest(String requester, String auditorium, String title, String description, String priority) {
        String safeRequester = normalizeRequester(requester);
        String safeAuditorium = normalizeAuditorium(auditorium);
        String safeTitle = normalizeText(title, 150);
        String safeDescription = normalizeText(description, 1000);
        String safePriority = normalizePriority(priority);

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO maintenance_requests (requester, auditorium, title, description, priority, status) " +
                            "VALUES (?, ?, ?, ?, ?, 'OPEN')",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, safeRequester);
            ps.setString(2, safeAuditorium);
            ps.setString(3, safeTitle);
            ps.setString(4, safeDescription);
            ps.setString(5, safePriority);
            return ps;
        }, keyHolder);

        long id = extractGeneratedId(keyHolder);
        if (id <= 0) {
            return "";
        }

        String trackingNo = "MR-" + AppClock.today().format(TRACKING_DATE) + "-" + String.format("%05d", id);
        jdbcTemplate.update("UPDATE maintenance_requests SET tracking_no = ? WHERE id = ?", trackingNo, id);
        return trackingNo;
    }

    public List<MaintenanceRequestSummary> listRecent(int limit) {
        int safeLimit = Math.max(1, Math.min(100, limit));
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, tracking_no, requester, assignee, auditorium, title, priority, status, " +
                        "created_at, updated_at, started_at, resolved_at, closed_at, closed_by, resolution_note " +
                        "FROM maintenance_requests ORDER BY created_at DESC LIMIT ?",
                safeLimit);
        return rows.stream().map(this::mapSummary).toList();
    }

    @Transactional
    public void updateStatus(long requestId, String operator, String targetStatus, String assignee, String note) {
        String safeOperator = normalizeRequester(operator);
        RequestStatus next = RequestStatus.fromInput(targetStatus);
        if (next == null) {
            throw new IllegalArgumentException("不支援的狀態：" + targetStatus);
        }

        Map<String, Object> row = loadById(requestId);
        RequestStatus current = RequestStatus.fromInput(asText(row.get("status")));
        if (current == null) {
            throw new IllegalArgumentException("目前狀態不合法，無法更新。");
        }
        if (current == next) {
            return;
        }
        if (!ALLOWED_TRANSITIONS.getOrDefault(current, Set.of()).contains(next)) {
            throw new IllegalArgumentException("狀態不可由 " + current + " 變更為 " + next + "。");
        }

        String safeAssignee = normalizeRequester(assignee);
        String safeNote = normalizeText(note, MAX_NOTE_LEN);

        switch (next) {
            case IN_PROGRESS -> jdbcTemplate.update(
                    "UPDATE maintenance_requests " +
                            "SET status = 'IN_PROGRESS', assignee = ?, started_at = COALESCE(started_at, NOW()) " +
                            "WHERE id = ?",
                    safeAssignee, requestId);
            case RESOLVED -> jdbcTemplate.update(
                    "UPDATE maintenance_requests " +
                            "SET status = 'RESOLVED', assignee = ?, resolved_at = NOW(), resolution_note = ? " +
                            "WHERE id = ?",
                    safeAssignee, safeNote, requestId);
            case CLOSED -> jdbcTemplate.update(
                    "UPDATE maintenance_requests " +
                            "SET status = 'CLOSED', assignee = ?, closed_at = NOW(), closed_by = ?, " +
                            "resolution_note = CASE WHEN ? = '' THEN resolution_note ELSE ? END " +
                            "WHERE id = ?",
                    safeAssignee, safeOperator, safeNote, safeNote, requestId);
            case CANCELLED -> jdbcTemplate.update(
                    "UPDATE maintenance_requests " +
                            "SET status = 'CANCELLED', assignee = ?, closed_at = NOW(), closed_by = ?, " +
                            "resolution_note = CASE WHEN ? = '' THEN resolution_note ELSE ? END " +
                            "WHERE id = ?",
                    safeAssignee, safeOperator, safeNote, safeNote, requestId);
            default -> throw new IllegalArgumentException("不支援的狀態：" + next);
        }
    }

    private MaintenanceRequestSummary mapSummary(Map<String, Object> row) {
        return new MaintenanceRequestSummary(
                ((Number) row.get("id")).longValue(),
                asText(row.get("tracking_no")),
                asText(row.get("requester")),
                asText(row.get("assignee")),
                asText(row.get("auditorium")),
                asText(row.get("title")),
                asText(row.get("priority")),
                asText(row.get("status")),
                toInstant(row.get("created_at")),
                toInstant(row.get("updated_at")),
                toInstant(row.get("started_at")),
                toInstant(row.get("resolved_at")),
                toInstant(row.get("closed_at")),
                asText(row.get("closed_by")),
                asText(row.get("resolution_note")));
    }

    private Map<String, Object> loadById(long requestId) {
        try {
            return jdbcTemplate.queryForMap(
                    "SELECT id, status FROM maintenance_requests WHERE id = ?",
                    requestId);
        } catch (EmptyResultDataAccessException ex) {
            throw new IllegalArgumentException("找不到維修申請：" + requestId);
        }
    }

    private static String normalizeRequester(String requester) {
        if (requester == null || requester.isBlank()) {
            return "unknown";
        }
        return requester.trim();
    }

    private static String normalizeAuditorium(String auditorium) {
        if (auditorium == null || auditorium.isBlank()) {
            return "";
        }
        String value = auditorium.trim();
        return value.length() > 100 ? value.substring(0, 100) : value;
    }

    private static String normalizeText(String text, int maxLen) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) {
            return "";
        }
        return value.length() > maxLen ? value.substring(0, maxLen) : value;
    }

    private static String normalizePriority(String priority) {
        if (priority == null) {
            return "MEDIUM";
        }
        String value = priority.trim().toUpperCase();
        return switch (value) {
            case "LOW", "MEDIUM", "HIGH", "URGENT" -> value;
            default -> "MEDIUM";
        };
    }

    private static String asText(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static Instant toInstant(Object value) {
        if (value instanceof Timestamp ts) {
            return ts.toInstant();
        }
        if (value instanceof java.util.Date date) {
            return date.toInstant();
        }
        return null;
    }

    private static long extractGeneratedId(KeyHolder keyHolder) {
        if (keyHolder == null) {
            return 0L;
        }
        try {
            Number key = keyHolder.getKey();
            if (key != null) {
                return key.longValue();
            }
        } catch (Exception ignored) {
            // Some DBs (e.g. H2) may return multiple generated columns.
        }
        Map<String, Object> keys = keyHolder.getKeys();
        if (keys != null && keys.get("id") instanceof Number number) {
            return number.longValue();
        }
        if (keys != null && keys.get("ID") instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }

    private enum RequestStatus {
        OPEN,
        IN_PROGRESS,
        RESOLVED,
        CLOSED,
        CANCELLED;

        private static RequestStatus fromInput(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            try {
                return RequestStatus.valueOf(value.trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }
    }
}

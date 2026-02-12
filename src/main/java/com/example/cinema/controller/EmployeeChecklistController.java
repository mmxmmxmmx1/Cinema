package com.example.cinema.controller;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.cinema.config.AppClock;
import com.example.cinema.model.Movie;
import com.example.cinema.model.Showtime;
import com.example.cinema.service.MovieService;

@Controller
@RequestMapping("/employee")
public class EmployeeChecklistController {

    private static final int MAX_NOTE_LENGTH = 255;

    private static final List<ChecklistItem> DEFAULT_ITEMS = List.of(
            new ChecklistItem("SCREEN", "螢幕與投影"),
            new ChecklistItem("AUDIO", "音響測試"),
            new ChecklistItem("SEAT", "座椅與走道"),
            new ChecklistItem("CLEAN", "清潔狀況"),
            new ChecklistItem("SAFETY", "消防與緊急設備"));

    private final JdbcTemplate jdbcTemplate;
    private final MovieService movieService;

    public EmployeeChecklistController(JdbcTemplate jdbcTemplate, MovieService movieService) {
        this.jdbcTemplate = jdbcTemplate;
        this.movieService = movieService;
    }

    @GetMapping("/checklist")
    public String checklistPage(
            Authentication authentication,
            @RequestParam(value = "date", required = false) String date,
            Model model) {
        LocalDate today = AppClock.today();
        LocalDate requestedDate = parseDateOrToday(date);
        LocalDate checkDate = requestedDate.isAfter(today) ? today : requestedDate;
        String operator = authentication == null ? "unknown" : authentication.getName();
        ChecklistForm form = new ChecklistForm();
        form.setCheckDate(checkDate.toString());

        List<String> auditoriums = loadAuditoriums();
        Map<String, ChecklistEntryVm> existing = loadExistingEntries(checkDate);
        List<ChecklistEntryVm> entries = new ArrayList<>();

        for (String auditorium : auditoriums) {
            for (ChecklistItem item : DEFAULT_ITEMS) {
                String key = key(auditorium, item.code());
                ChecklistEntryVm current = existing.getOrDefault(key, new ChecklistEntryVm(
                        auditorium,
                        item.code(),
                        item.label(),
                        false,
                        "",
                        "",
                        ""));

                form.getEntries().add(new ChecklistEntryForm(
                        current.auditorium(),
                        current.itemCode(),
                        current.itemLabel(),
                        current.checked(),
                        current.note()));
                entries.add(current);
            }
        }

        model.addAttribute("title", "更新影廳檢查表");
        model.addAttribute("checkDate", checkDate.toString());
        model.addAttribute("maxCheckDate", today.toString());
        model.addAttribute("operator", operator);
        model.addAttribute("entries", entries);
        model.addAttribute("hallCompletion", buildHallCompletion(entries));
        model.addAttribute("historyRows", buildHistoryRows(checkDate.minusDays(14)));
        model.addAttribute("form", form);
        if (requestedDate.isAfter(today)) {
            model.addAttribute("error", "檢查日期不可晚於今天，已自動切回今日。");
        }
        return "employee-checklist";
    }

    @PostMapping("/checklist")
    public String saveChecklist(
            Authentication authentication,
            @ModelAttribute("form") ChecklistForm form,
            RedirectAttributes redirectAttributes) {
        LocalDate today = AppClock.today();
        LocalDate checkDate = parseDateOrToday(form.getCheckDate());
        if (checkDate.isAfter(today)) {
            redirectAttributes.addFlashAttribute("error", "不可儲存未來日期的檢查表。");
            return "redirect:/employee/checklist?date=" + today;
        }
        String operator = authentication == null ? "unknown" : authentication.getName();
        int updatedRows = 0;

        try {
            if (form.getEntries() == null || form.getEntries().isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "沒有可更新的檢查項目。");
                return "redirect:/employee/checklist?date=" + checkDate;
            }

            for (ChecklistEntryForm entry : form.getEntries()) {
                if (entry == null || isBlank(entry.getAuditorium()) || isBlank(entry.getItemCode())) {
                    continue;
                }
                String itemLabel = entry.getItemLabel();
                if (isBlank(itemLabel)) {
                    itemLabel = entry.getItemCode();
                }
                String note = trimToEmpty(entry.getNote());
                if (note.length() > MAX_NOTE_LENGTH) {
                    note = note.substring(0, MAX_NOTE_LENGTH);
                }

                updatedRows += jdbcTemplate.update(
                        "INSERT INTO auditorium_checklist " +
                                "(check_date, auditorium, item_code, item_label, checked, note, updated_by, updated_at) " +
                                "VALUES (?, ?, ?, ?, ?, ?, ?, NOW()) " +
                                "ON DUPLICATE KEY UPDATE " +
                                "item_label = VALUES(item_label), checked = VALUES(checked), note = VALUES(note), " +
                                "updated_by = VALUES(updated_by), updated_at = NOW()",
                        Date.valueOf(checkDate),
                        entry.getAuditorium(),
                        entry.getItemCode(),
                        itemLabel,
                        entry.isChecked() ? 1 : 0,
                        note,
                        operator);
            }
        } catch (DataAccessException ex) {
            redirectAttributes.addFlashAttribute("error", "儲存失敗：資料庫暫時不可用或資料表尚未建立。");
            return "redirect:/employee/checklist?date=" + checkDate;
        }

        redirectAttributes.addFlashAttribute("success",
                "已更新影廳檢查表（日期：" + checkDate + "，處理項目：" + updatedRows + "）。");
        return "redirect:/employee/checklist?date=" + checkDate;
    }

    private List<String> loadAuditoriums() {
        Set<String> halls = movieService.getMovies().stream()
                .flatMap((Movie m) -> m.getShowtimes().stream())
                .map(Showtime::getAuditorium)
                .filter(v -> !isBlank(v))
                .collect(Collectors.toSet());
        if (halls.isEmpty()) {
            return List.of("1號廳", "2號廳", "3號廳");
        }
        return halls.stream().sorted(Comparator.naturalOrder()).toList();
    }

    private Map<String, ChecklistEntryVm> loadExistingEntries(LocalDate checkDate) {
        Map<String, ChecklistEntryVm> map = new LinkedHashMap<>();
        List<Map<String, Object>> rows;
        try {
            rows = jdbcTemplate.queryForList(
                    "SELECT auditorium, item_code, item_label, checked, note, updated_by, updated_at " +
                            "FROM auditorium_checklist WHERE check_date = ?",
                    Date.valueOf(checkDate));
        } catch (DataAccessException ex) {
            return map;
        }

        for (Map<String, Object> row : rows) {
            String auditorium = String.valueOf(row.get("auditorium"));
            String itemCode = String.valueOf(row.get("item_code"));
            String itemLabel = String.valueOf(row.get("item_label"));
            boolean checked = ((Number) row.get("checked")).intValue() != 0;
            String note = row.get("note") == null ? "" : String.valueOf(row.get("note"));
            String updatedBy = row.get("updated_by") == null ? "" : String.valueOf(row.get("updated_by"));
            String updatedAt = "";
            Object raw = row.get("updated_at");
            if (raw instanceof Timestamp ts) {
                updatedAt = ts.toLocalDateTime().toString().replace('T', ' ');
            }
            map.put(key(auditorium, itemCode), new ChecklistEntryVm(
                    auditorium, itemCode, itemLabel, checked, note, updatedBy, updatedAt));
        }
        return map;
    }

    private static String key(String auditorium, String itemCode) {
        return auditorium + "|" + itemCode;
    }

    private List<HallCompletionVm> buildHallCompletion(List<ChecklistEntryVm> entries) {
        Map<String, int[]> stats = new LinkedHashMap<>();
        for (ChecklistEntryVm entry : entries) {
            int[] bucket = stats.computeIfAbsent(entry.auditorium(), k -> new int[] { 0, 0 });
            bucket[1] += 1;
            if (entry.checked()) {
                bucket[0] += 1;
            }
        }
        return stats.entrySet().stream()
                .map(e -> {
                    int checked = e.getValue()[0];
                    int total = e.getValue()[1];
                    int rate = total <= 0 ? 0 : (checked * 100 / total);
                    return new HallCompletionVm(e.getKey(), checked, total, rate);
                })
                .toList();
    }

    private List<ChecklistHistoryVm> buildHistoryRows(LocalDate startDate) {
        List<Map<String, Object>> rows;
        try {
            rows = jdbcTemplate.queryForList(
                    "SELECT check_date, auditorium, checked, updated_by, updated_at " +
                            "FROM auditorium_checklist WHERE check_date >= ? " +
                            "ORDER BY check_date DESC, updated_at DESC",
                    Date.valueOf(startDate));
        } catch (DataAccessException ex) {
            return List.of();
        }

        Map<String, HistoryAgg> aggMap = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            LocalDate date = toLocalDate(row.get("check_date"));
            String hall = String.valueOf(row.get("auditorium"));
            String aggKey = date + "|" + hall;
            HistoryAgg agg = aggMap.computeIfAbsent(aggKey, ignored -> new HistoryAgg(date, hall));

            int checked = ((Number) row.get("checked")).intValue();
            agg.total += 1;
            if (checked != 0) {
                agg.checked += 1;
            }

            String updatedBy = row.get("updated_by") == null ? "" : String.valueOf(row.get("updated_by"));
            Timestamp updatedAt = row.get("updated_at") instanceof Timestamp ts ? ts : null;
            if (updatedAt != null && (agg.latestAt == null || updatedAt.after(agg.latestAt))) {
                agg.latestAt = updatedAt;
                agg.latestBy = updatedBy;
            }
        }

        return aggMap.values().stream()
                .sorted(Comparator
                        .comparing((HistoryAgg h) -> h.date).reversed()
                        .thenComparing(h -> h.auditorium))
                .map(h -> {
                    int rate = h.total <= 0 ? 0 : (h.checked * 100 / h.total);
                    String latestAt = h.latestAt == null ? "" : h.latestAt.toLocalDateTime().toString().replace('T', ' ');
                    return new ChecklistHistoryVm(h.date.toString(), h.auditorium, h.checked, h.total, rate, h.latestBy, latestAt);
                })
                .toList();
    }

    private static LocalDate parseDateOrToday(String input) {
        if (isBlank(input)) {
            return AppClock.today();
        }
        try {
            return LocalDate.parse(input.trim());
        } catch (DateTimeParseException ex) {
            return AppClock.today();
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static LocalDate toLocalDate(Object value) {
        if (value instanceof Date d) {
            return d.toLocalDate();
        }
        if (value instanceof java.util.Date d) {
            return d.toInstant().atZone(AppClock.zoneId()).toLocalDate();
        }
        if (value == null) {
            return AppClock.today();
        }
        return parseDateOrToday(String.valueOf(value));
    }

    private record ChecklistItem(String code, String label) {
    }

    public record ChecklistEntryVm(
            String auditorium,
            String itemCode,
            String itemLabel,
            boolean checked,
            String note,
            String updatedBy,
            String updatedAt) {
    }

    public record HallCompletionVm(
            String auditorium,
            int checked,
            int total,
            int completionRate) {
    }

    public record ChecklistHistoryVm(
            String checkDate,
            String auditorium,
            int checked,
            int total,
            int completionRate,
            String latestBy,
            String latestAt) {
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

    public static class ChecklistForm {
        private String checkDate;
        private List<ChecklistEntryForm> entries = new ArrayList<>();

        public String getCheckDate() {
            return checkDate;
        }

        public void setCheckDate(String checkDate) {
            this.checkDate = checkDate;
        }

        public List<ChecklistEntryForm> getEntries() {
            return entries;
        }

        public void setEntries(List<ChecklistEntryForm> entries) {
            this.entries = entries == null ? new ArrayList<>() : entries;
        }
    }

    public static class ChecklistEntryForm {
        private String auditorium;
        private String itemCode;
        private String itemLabel;
        private boolean checked;
        private String note;

        public ChecklistEntryForm() {
        }

        public ChecklistEntryForm(String auditorium, String itemCode, String itemLabel, boolean checked, String note) {
            this.auditorium = auditorium;
            this.itemCode = itemCode;
            this.itemLabel = itemLabel;
            this.checked = checked;
            this.note = note;
        }

        public String getAuditorium() {
            return auditorium;
        }

        public void setAuditorium(String auditorium) {
            this.auditorium = auditorium;
        }

        public String getItemCode() {
            return itemCode;
        }

        public void setItemCode(String itemCode) {
            this.itemCode = itemCode;
        }

        public String getItemLabel() {
            return itemLabel;
        }

        public void setItemLabel(String itemLabel) {
            this.itemLabel = itemLabel;
        }

        public boolean isChecked() {
            return checked;
        }

        public void setChecked(boolean checked) {
            this.checked = checked;
        }

        public String getNote() {
            return note;
        }

        public void setNote(String note) {
            this.note = note;
        }
    }
}

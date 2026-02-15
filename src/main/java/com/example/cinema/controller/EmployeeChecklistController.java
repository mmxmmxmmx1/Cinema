package com.example.cinema.controller;

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
import com.example.cinema.service.EmployeeChecklistService;
import com.example.cinema.service.EmployeeChecklistService.ChecklistEntryCommand;
import com.example.cinema.service.EmployeeChecklistService.ChecklistEntryData;
import com.example.cinema.service.EmployeeChecklistService.ChecklistHistoryData;
import com.example.cinema.service.MovieService;

@Controller
@RequestMapping("/employee")
public class EmployeeChecklistController {

    private static final List<ChecklistItem> DEFAULT_ITEMS = List.of(
            new ChecklistItem("SCREEN", "螢幕與投影"),
            new ChecklistItem("AUDIO", "音響測試"),
            new ChecklistItem("SEAT", "座椅與走道"),
            new ChecklistItem("CLEAN", "清潔狀況"),
            new ChecklistItem("SAFETY", "消防與緊急設備"));

    private final EmployeeChecklistService employeeChecklistService;
    private final MovieService movieService;

    public EmployeeChecklistController(EmployeeChecklistService employeeChecklistService, MovieService movieService) {
        this.employeeChecklistService = employeeChecklistService;
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
        Map<String, ChecklistEntryData> existing;
        List<ChecklistHistoryData> historyData;
        try {
            existing = employeeChecklistService.loadEntriesByDate(checkDate);
            historyData = employeeChecklistService.loadHistorySince(checkDate.minusDays(14));
        } catch (DataAccessException ex) {
            existing = Map.of();
            historyData = List.of();
            model.addAttribute("error", "讀取檢查表失敗：資料庫暫時不可用或資料表尚未建立。");
        }
        List<ChecklistEntryVm> entries = new ArrayList<>();

        for (String auditorium : auditoriums) {
            for (ChecklistItem item : DEFAULT_ITEMS) {
                String key = key(auditorium, item.code());
                ChecklistEntryData current = existing.getOrDefault(key, new ChecklistEntryData(
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
                entries.add(new ChecklistEntryVm(
                        current.auditorium(),
                        current.itemCode(),
                        current.itemLabel(),
                        current.checked(),
                        current.note(),
                        current.updatedBy(),
                        current.updatedAt()));
            }
        }

        model.addAttribute("title", "更新影廳檢查表");
        model.addAttribute("checkDate", checkDate.toString());
        model.addAttribute("maxCheckDate", today.toString());
        model.addAttribute("operator", operator);
        model.addAttribute("entries", entries);
        model.addAttribute("hallCompletion", buildHallCompletion(entries));
        model.addAttribute("historyRows", mapHistoryRows(historyData));
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
            List<ChecklistEntryCommand> commands = form.getEntries().stream()
                    .map(entry -> new ChecklistEntryCommand(
                            entry.getAuditorium(),
                            entry.getItemCode(),
                            entry.getItemLabel(),
                            entry.isChecked(),
                            entry.getNote()))
                    .toList();
            updatedRows = employeeChecklistService.saveChecklist(checkDate, operator, commands);
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

    private List<ChecklistHistoryVm> mapHistoryRows(List<ChecklistHistoryData> rows) {
        return rows.stream()
                .map(row -> new ChecklistHistoryVm(
                        row.checkDate(),
                        row.auditorium(),
                        row.checked(),
                        row.total(),
                        row.completionRate(),
                        row.latestBy(),
                        row.latestAt()))
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

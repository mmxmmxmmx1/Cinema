package com.example.cinema.controller;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.springframework.dao.DataAccessException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.cinema.dto.MaintenanceRequestSummary;
import com.example.cinema.model.Movie;
import com.example.cinema.model.Showtime;
import com.example.cinema.service.MaintenanceRequestService;
import com.example.cinema.service.MovieService;

@Controller
@RequestMapping("/employee")
public class EmployeeMaintenanceController {

    private final MaintenanceRequestService maintenanceRequestService;
    private final MovieService movieService;

    public EmployeeMaintenanceController(MaintenanceRequestService maintenanceRequestService, MovieService movieService) {
        this.maintenanceRequestService = maintenanceRequestService;
        this.movieService = movieService;
    }

    @GetMapping("/maintenance")
    public String page(Model model) {
        model.addAttribute("title", "提交維修申請");
        model.addAttribute("halls", auditoriums());
        model.addAttribute("form", new MaintenanceForm());
        try {
            List<MaintenanceRequestSummary> requests = maintenanceRequestService.listRecent(50);
            model.addAttribute("requests", requests);
        } catch (DataAccessException ex) {
            model.addAttribute("error", "無法讀取維修申請紀錄（資料庫可能尚未啟動）。");
        }
        return "employee-maintenance";
    }

    @PostMapping("/maintenance")
    public String submit(
            Authentication authentication,
            @ModelAttribute("form") MaintenanceForm form,
            RedirectAttributes redirectAttributes) {
        String operator = authentication == null ? "unknown" : authentication.getName();
        String title = trim(form.getTitle());
        String description = trim(form.getDescription());
        if (title.isBlank() || description.isBlank()) {
            redirectAttributes.addFlashAttribute("error", "請填寫維修主旨與描述。");
            return "redirect:/employee/maintenance";
        }

        try {
            String trackingNo = maintenanceRequestService.createRequest(
                    operator,
                    trim(form.getAuditorium()),
                    title,
                    description,
                    trim(form.getPriority()));
            if (trackingNo == null || trackingNo.isBlank()) {
                redirectAttributes.addFlashAttribute("error", "維修申請送出失敗，請稍後再試。");
            } else {
                redirectAttributes.addFlashAttribute("success", "已送出維修申請，追蹤編號：" + trackingNo);
            }
        } catch (DataAccessException ex) {
            redirectAttributes.addFlashAttribute("error", "維修申請送出失敗（資料庫可能尚未啟動）。");
        }
        return "redirect:/employee/maintenance";
    }

    @PostMapping("/maintenance/{requestId}/status")
    public String updateStatus(
            Authentication authentication,
            @PathVariable("requestId") long requestId,
            @RequestParam("status") String status,
            @RequestParam(value = "assignee", required = false) String assignee,
            @RequestParam(value = "resolutionNote", required = false) String resolutionNote,
            RedirectAttributes redirectAttributes) {
        String operator = authentication == null ? "unknown" : authentication.getName();
        try {
            maintenanceRequestService.updateStatus(
                    requestId,
                    operator,
                    trim(status),
                    trim(assignee),
                    trim(resolutionNote));
            redirectAttributes.addFlashAttribute("success", "維修申請 #" + requestId + " 狀態已更新。");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        } catch (DataAccessException ex) {
            redirectAttributes.addFlashAttribute("error", "狀態更新失敗（資料庫可能尚未啟動）。");
        }
        return "redirect:/employee/maintenance";
    }

    private List<String> auditoriums() {
        Set<String> halls = movieService.getMovies().stream()
                .flatMap((Movie m) -> m.getShowtimes().stream())
                .map(Showtime::getAuditorium)
                .collect(java.util.stream.Collectors.toSet());
        if (halls.isEmpty()) {
            return List.of("1號廳", "2號廳", "3號廳");
        }
        return halls.stream().sorted(Comparator.naturalOrder()).toList();
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    public static class MaintenanceForm {
        private String auditorium;
        private String title;
        private String description;
        private String priority = "MEDIUM";

        public String getAuditorium() {
            return auditorium;
        }

        public void setAuditorium(String auditorium) {
            this.auditorium = auditorium;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getPriority() {
            return priority;
        }

        public void setPriority(String priority) {
            this.priority = priority;
        }
    }
}

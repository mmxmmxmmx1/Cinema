package com.example.cinema.controller;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.cinema.model.Showtime;
import com.example.cinema.service.MovieService;

@Controller
@RequestMapping("/employee/admin/showtimes")
public class EmployeeShowtimeAdminController {

    private final MovieService movieService;

    public EmployeeShowtimeAdminController(MovieService movieService) {
        this.movieService = movieService;
    }

    @GetMapping
    public String page(
            @RequestParam(value = "movieId", required = false) String movieId,
            Model model) {
        List<MovieService.MovieCatalogItem> movies = movieService.listCatalogItems();
        String selectedMovieId = selectMovie(movieId, movies);
        List<Showtime> showtimes = selectedMovieId == null ? List.of() : movieService.listConfiguredShowtimes(selectedMovieId);

        model.addAttribute("title", "場次管理");
        model.addAttribute("movies", movies);
        model.addAttribute("selectedMovieId", selectedMovieId);
        model.addAttribute("showtimes", showtimes);
        return "admin-showtimes";
    }

    @PostMapping("/save")
    public String save(
            Authentication authentication,
            @RequestParam("movieId") String movieId,
            @RequestParam("showtimeId") String showtimeId,
            @RequestParam("startTime") String startTime,
            @RequestParam("durationMinutes") int durationMinutes,
            @RequestParam("auditorium") String auditorium,
            RedirectAttributes redirectAttributes) {
        String operator = authentication == null ? "unknown" : authentication.getName();
        try {
            movieService.saveShowtimeOverride(
                    movieId,
                    showtimeId,
                    startTime,
                    durationMinutes,
                    auditorium,
                    operator);
            redirectAttributes.addFlashAttribute("success", "場次已儲存：" + showtimeId);
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/employee/admin/showtimes?movieId=" + movieId;
    }

    @PostMapping("/disable")
    public String disable(
            Authentication authentication,
            @RequestParam("movieId") String movieId,
            @RequestParam("showtimeId") String showtimeId,
            RedirectAttributes redirectAttributes) {
        String operator = authentication == null ? "unknown" : authentication.getName();
        try {
            movieService.disableShowtime(movieId, showtimeId, operator);
            redirectAttributes.addFlashAttribute("success", "場次已停用：" + showtimeId);
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/employee/admin/showtimes?movieId=" + movieId;
    }

    private static String selectMovie(String movieId, List<MovieService.MovieCatalogItem> movies) {
        if (movies == null || movies.isEmpty()) {
            return null;
        }
        if (movieId != null) {
            String safe = movieId.trim();
            boolean exists = movies.stream().anyMatch(m -> m.id().equals(safe));
            if (exists) {
                return safe;
            }
        }
        return movies.get(0).id();
    }
}

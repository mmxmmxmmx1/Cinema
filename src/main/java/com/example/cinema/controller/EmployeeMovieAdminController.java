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

import com.example.cinema.service.MovieService;

@Controller
@RequestMapping("/employee/admin/movies")
public class EmployeeMovieAdminController {

    private final MovieService movieService;

    public EmployeeMovieAdminController(MovieService movieService) {
        this.movieService = movieService;
    }

    @GetMapping
    public String page(
            @RequestParam(value = "movieId", required = false) String movieId,
            Model model) {
        List<MovieService.MovieAdminItem> movies = movieService.listMovieAdminItems();
        String selectedMovieId = selectMovie(movieId, movies);
        MovieService.MovieAdminItem selectedMovie = selectedMovieId == null
                ? emptyMovie()
                : movieService.getMovieAdminItem(selectedMovieId).orElseGet(this::emptyMovie);

        model.addAttribute("title", "電影資料管理");
        model.addAttribute("movies", movies);
        model.addAttribute("selectedMovie", selectedMovie);
        return "admin-movies";
    }

    @PostMapping("/save")
    public String save(
            Authentication authentication,
            @RequestParam("movieId") String movieId,
            @RequestParam("title") String title,
            @RequestParam(value = "subtitle", required = false) String subtitle,
            @RequestParam("posterUrl") String posterUrl,
            @RequestParam("carouselImageUrl") String carouselImageUrl,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "sortOrder", defaultValue = "0") int sortOrder,
            @RequestParam(value = "enabled", defaultValue = "false") boolean enabled,
            RedirectAttributes redirectAttributes) {
        String operator = authentication == null ? "unknown" : authentication.getName();
        String safeMovieId = movieId == null ? "" : movieId.trim();
        try {
            movieService.saveMovieCatalog(
                    safeMovieId,
                    title,
                    subtitle,
                    posterUrl,
                    carouselImageUrl,
                    description,
                    sortOrder,
                    enabled,
                    operator);
            redirectAttributes.addFlashAttribute("success", "電影資料已儲存：" + safeMovieId);
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/employee/admin/movies?movieId=" + safeMovieId;
    }

    @PostMapping("/toggle")
    public String toggle(
            Authentication authentication,
            @RequestParam("movieId") String movieId,
            @RequestParam("enabled") boolean enabled,
            RedirectAttributes redirectAttributes) {
        String operator = authentication == null ? "unknown" : authentication.getName();
        String safeMovieId = movieId == null ? "" : movieId.trim();
        try {
            movieService.setMovieEnabled(safeMovieId, enabled, operator);
            redirectAttributes.addFlashAttribute(
                    "success",
                    enabled ? "已啟用電影：" + safeMovieId : "已停用電影：" + safeMovieId);
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/employee/admin/movies?movieId=" + safeMovieId;
    }

    private static String selectMovie(String movieId, List<MovieService.MovieAdminItem> movies) {
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

    private MovieService.MovieAdminItem emptyMovie() {
        return new MovieService.MovieAdminItem(
                "",
                "",
                "",
                "",
                "",
                "",
                true,
                0);
    }
}

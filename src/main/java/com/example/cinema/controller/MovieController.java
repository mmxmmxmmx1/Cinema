package com.example.cinema.controller;

import com.example.cinema.model.Movie;
import com.example.cinema.model.ShowtimeDetails;
import com.example.cinema.service.MovieService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/movies")
public class MovieController {

    private final MovieService movieService;

    public MovieController(MovieService movieService) {
        this.movieService = movieService;
    }

    @GetMapping
    public List<Movie> getMovies() {
        return movieService.getMovies();
    }

    @GetMapping("/{movieId}")
    public ResponseEntity<Movie> getMovie(@PathVariable String movieId) {
        return movieService.getMovie(movieId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{movieId}/showtimes/{showtimeId}")
    public ResponseEntity<ShowtimeDetails> getShowtimeDetails(@PathVariable String movieId, @PathVariable String showtimeId) {
        try {
            return ResponseEntity.ok(movieService.getShowtimeDetails(movieId, showtimeId));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.notFound().build();
        }
    }
}

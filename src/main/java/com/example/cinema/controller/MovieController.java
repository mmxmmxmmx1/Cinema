package com.example.cinema.controller;

import com.example.cinema.model.Movie;
import com.example.cinema.model.ShowtimeDetails;
import com.example.cinema.dto.BookingWindowResponse;
import com.example.cinema.service.MovieService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping({ "/api/movies", "/api/v1/movies" })
public class MovieController {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

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
        return movieService.getMovieWithUpcomingShowtimes(movieId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{movieId}/showtimes/{showtimeId}")
    public ResponseEntity<ShowtimeDetails> getShowtimeDetails(@PathVariable String movieId,
            @PathVariable String showtimeId) {
        if (!movieService.isShowtimeOpenForPurchase(movieId, showtimeId)) {
            return ResponseEntity.status(HttpStatus.GONE).build();
        }
        try {
            return ResponseEntity.ok(movieService.getShowtimeDetails(movieId, showtimeId));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/booking-window")
    public BookingWindowResponse getBookingWindow() {
        MovieService.BookingWindowStatus status = movieService.getBookingWindowStatus();
        return new BookingWindowResponse(
                status.bookingOpen(),
                status.warning(),
                HH_MM.format(status.now()),
                HH_MM.format(status.openTime()),
                HH_MM.format(status.closeTime()),
                status.message());
    }
}

package com.example.cinema.dto;

import com.example.cinema.model.Movie;

import java.util.List;

public class MovieDto {
    private final String id;
    private final String title;
    private final String subtitle;
    private final String posterUrl;
    private final String description;
    private final List<ShowtimeDto> showtimes;

    public MovieDto(Movie movie, List<ShowtimeDto> showtimes) {
        this.id = movie.getId();
        this.title = movie.getTitle();
        this.subtitle = movie.getSubtitle();
        this.posterUrl = movie.getPosterUrl();
        this.description = movie.getDescription();
        this.showtimes = showtimes;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public String getPosterUrl() {
        return posterUrl;
    }

    public String getDescription() {
        return description;
    }

    public List<ShowtimeDto> getShowtimes() {
        return showtimes;
    }
}

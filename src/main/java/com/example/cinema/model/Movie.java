package com.example.cinema.model;

import java.util.List;

public class Movie {
    private final String id;
    private final String title;
    private final String subtitle;
    private final String posterUrl;
    private final String carouselImageUrl;
    private final String description;
    private final List<Showtime> showtimes;

    public Movie(String id, String title, String subtitle, String posterUrl, String description, List<Showtime> showtimes) {
        this(id, title, subtitle, posterUrl, posterUrl, description, showtimes);
    }

    public Movie(
            String id,
            String title,
            String subtitle,
            String posterUrl,
            String carouselImageUrl,
            String description,
            List<Showtime> showtimes) {
        this.id = id;
        this.title = title;
        this.subtitle = subtitle;
        this.posterUrl = posterUrl;
        this.carouselImageUrl = carouselImageUrl;
        this.description = description;
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

    public String getCarouselImageUrl() {
        return carouselImageUrl;
    }

    public String getDescription() {
        return description;
    }

    public List<Showtime> getShowtimes() {
        return showtimes;
    }
}

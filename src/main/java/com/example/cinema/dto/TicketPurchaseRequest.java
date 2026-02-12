package com.example.cinema.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class TicketPurchaseRequest {

    @NotBlank(message = "movieId 不可為空")
    private String movieId;

    @NotBlank(message = "showtimeId 不可為空")
    private String showtimeId;

    @NotEmpty(message = "seatIds 不可為空")
    @Size(max = 4, message = "一次最多只能買 4 張票")
    private List<
            @Pattern(regexp = "^[A-L]0[1-8]$", message = "seatId 格式需為 A01 ~ L08")
            String> seatIds;

    public String getMovieId() {
        return movieId;
    }

    public void setMovieId(String movieId) {
        this.movieId = movieId;
    }

    public String getShowtimeId() {
        return showtimeId;
    }

    public void setShowtimeId(String showtimeId) {
        this.showtimeId = showtimeId;
    }

    public List<String> getSeatIds() {
        return seatIds;
    }

    public void setSeatIds(List<String> seatIds) {
        this.seatIds = seatIds;
    }
}


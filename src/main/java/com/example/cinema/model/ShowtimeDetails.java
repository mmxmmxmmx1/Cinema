package com.example.cinema.model;

public class ShowtimeDetails {
    private final Showtime showtime;
    private final SeatLayout seatLayout;

    public ShowtimeDetails(Showtime showtime, SeatLayout seatLayout) {
        this.showtime = showtime;
        this.seatLayout = seatLayout;
    }

    public Showtime getShowtime() {
        return showtime;
    }

    public SeatLayout getSeatLayout() {
        return seatLayout;
    }
}

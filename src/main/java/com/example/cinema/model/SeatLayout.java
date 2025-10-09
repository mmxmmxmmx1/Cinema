package com.example.cinema.model;

import java.util.List;

public class SeatLayout {
    private final String showtimeId;
    private final int rows;
    private final int columns;
    private final List<SeatStatus> seats;

    public SeatLayout(String showtimeId, int rows, int columns, List<SeatStatus> seats) {
        this.showtimeId = showtimeId;
        this.rows = rows;
        this.columns = columns;
        this.seats = seats;
    }

    public String getShowtimeId() {
        return showtimeId;
    }

    public int getRows() {
        return rows;
    }

    public int getColumns() {
        return columns;
    }

    public List<SeatStatus> getSeats() {
        return seats;
    }
}

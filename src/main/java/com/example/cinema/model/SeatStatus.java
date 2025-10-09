package com.example.cinema.model;

public class SeatStatus {
    private final String seatId;
    private final boolean reserved;

    public SeatStatus(String seatId, boolean reserved) {
        this.seatId = seatId;
        this.reserved = reserved;
    }

    public String getSeatId() {
        return seatId;
    }

    public boolean isReserved() {
        return reserved;
    }
}

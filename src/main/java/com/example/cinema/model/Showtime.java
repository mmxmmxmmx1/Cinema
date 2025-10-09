package com.example.cinema.model;

public class Showtime {
    private final String id;
    private final String startTime;
    private final int durationMinutes;
    private final String auditorium;

    public Showtime(String id, String startTime, int durationMinutes, String auditorium) {
        this.id = id;
        this.startTime = startTime;
        this.durationMinutes = durationMinutes;
        this.auditorium = auditorium;
    }

    public String getId() {
        return id;
    }

    public String getStartTime() {
        return startTime;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public String getAuditorium() {
        return auditorium;
    }
}

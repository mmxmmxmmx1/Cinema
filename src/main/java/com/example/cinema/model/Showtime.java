package com.example.cinema.model;

public class Showtime {
    private final String id;
    private final String startTime;
    private final int durationMinutes;
    private final String auditorium;
    private final boolean bookable;

    public Showtime(String id, String startTime, int durationMinutes, String auditorium) {
        this(id, startTime, durationMinutes, auditorium, false);
    }

    public Showtime(String id, String startTime, int durationMinutes, String auditorium, boolean bookable) {
        this.id = id;
        this.startTime = startTime;
        this.durationMinutes = durationMinutes;
        this.auditorium = auditorium;
        this.bookable = bookable;
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

    public boolean isBookable() {
        return bookable;
    }

    public Showtime withBookable(boolean value) {
        if (this.bookable == value) {
            return this;
        }
        return new Showtime(id, startTime, durationMinutes, auditorium, value);
    }
}

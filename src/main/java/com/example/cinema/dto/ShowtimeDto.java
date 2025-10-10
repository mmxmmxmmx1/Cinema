package com.example.cinema.dto;

public class ShowtimeDto {
    private final String id;
    private final String startTime;
    private final int durationMinutes;
    private final String auditorium;
    private final boolean isBookable;

    public ShowtimeDto(String id, String startTime, int durationMinutes, String auditorium, boolean isBookable) {
        this.id = id;
        this.startTime = startTime;
        this.durationMinutes = durationMinutes;
        this.auditorium = auditorium;
        this.isBookable = isBookable;
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
        return isBookable;
    }
}

package com.example.cinema.model;

public class Showtime {
    private final String id;
    private final String startTime;
    private final int durationMinutes;
    private final String auditorium;
    private final String locationCode;
    private final String locationName;
    private final boolean bookable;

    public Showtime(String id, String startTime, int durationMinutes, String auditorium) {
        this(id, startTime, durationMinutes, auditorium, "taipei-main", "台北信義館", false);
    }

    public Showtime(
            String id,
            String startTime,
            int durationMinutes,
            String auditorium,
            String locationCode,
            String locationName) {
        this(id, startTime, durationMinutes, auditorium, locationCode, locationName, false);
    }

    public Showtime(String id, String startTime, int durationMinutes, String auditorium, boolean bookable) {
        this(id, startTime, durationMinutes, auditorium, "taipei-main", "台北信義館", bookable);
    }

    public Showtime(
            String id,
            String startTime,
            int durationMinutes,
            String auditorium,
            String locationCode,
            String locationName,
            boolean bookable) {
        this.id = id;
        this.startTime = startTime;
        this.durationMinutes = durationMinutes;
        this.auditorium = auditorium;
        this.locationCode = locationCode;
        this.locationName = locationName;
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

    public String getLocationCode() {
        return locationCode;
    }

    public String getLocationName() {
        return locationName;
    }

    public boolean isBookable() {
        return bookable;
    }

    public Showtime withBookable(boolean value) {
        if (this.bookable == value) {
            return this;
        }
        return new Showtime(id, startTime, durationMinutes, auditorium, locationCode, locationName, value);
    }
}

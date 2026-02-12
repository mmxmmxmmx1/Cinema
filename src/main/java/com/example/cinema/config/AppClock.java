package com.example.cinema.config;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AppClock {

    private static volatile Clock clock = Clock.system(ZoneId.of("Asia/Taipei"));

    public AppClock(@Value("${app.time-zone:Asia/Taipei}") String configuredZoneId) {
        ZoneId zone;
        try {
            zone = ZoneId.of(configuredZoneId);
        } catch (Exception ex) {
            zone = ZoneId.of("Asia/Taipei");
        }
        clock = Clock.system(zone);
    }

    public static Instant nowInstant() {
        return Instant.now(clock);
    }

    public static LocalDate today() {
        return LocalDate.now(clock);
    }

    public static LocalTime nowLocalTime() {
        return LocalTime.now(clock);
    }

    public static ZoneId zoneId() {
        return clock.getZone();
    }
}

package com.example.cinema.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.example.cinema.service.MovieService;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("場次管理整合測試")
class ShowtimeAdminIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MovieService movieService;

    @BeforeEach
    void setupSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS showtime_overrides");
        jdbcTemplate.execute(
                "CREATE TABLE showtime_overrides (" +
                        "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                        "movie_id VARCHAR(50) NOT NULL, " +
                        "showtime_id VARCHAR(50) NOT NULL, " +
                        "start_time VARCHAR(5) NOT NULL, " +
                        "duration_minutes INT NOT NULL, " +
                        "auditorium VARCHAR(100) NOT NULL, " +
                        "enabled TINYINT(1) NOT NULL DEFAULT 1, " +
                        "updated_by VARCHAR(100) NOT NULL DEFAULT 'system', " +
                        "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                        "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                        "UNIQUE (movie_id, showtime_id))");
    }

    @Test
    @DisplayName("管理員應可新增與停用場次")
    void shouldSaveAndDisableShowtime() {
        movieService.saveShowtimeOverride("mv-01", "mv-01-st99", "22:10", 130, "2號廳", "admin01");
        assertTrue(movieService.listConfiguredShowtimes("mv-01").stream()
                .anyMatch(s -> "mv-01-st99".equals(s.getId())));

        movieService.disableShowtime("mv-01", "mv-01-st99", "admin01");
        assertFalse(movieService.listConfiguredShowtimes("mv-01").stream()
                .anyMatch(s -> "mv-01-st99".equals(s.getId())));
    }
}

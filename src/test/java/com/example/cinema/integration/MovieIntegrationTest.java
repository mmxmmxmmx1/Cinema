package com.example.cinema.integration;

import com.example.cinema.model.Movie;
import com.example.cinema.service.MovieService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 電影功能整合測試
 * 測試完整的電影相關流程
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("電影功能整合測試")
class MovieIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private MovieService movieService;

    @Test
    @DisplayName("應該能夠獲取所有電影列表")
    void shouldGetAllMovies() {
        // When
        List<Movie> movies = movieService.getMovies();

        // Then
        assertNotNull(movies, "電影列表不應為 null");
        assertEquals(10, movies.size(), "應該有 10 部電影");
        
        // 驗證第一部電影的基本信息
        Movie firstMovie = movies.get(0);
        assertNotNull(firstMovie.getId(), "電影 ID 不應為 null");
        assertNotNull(firstMovie.getTitle(), "電影標題不應為 null");
        assertNotNull(firstMovie.getShowtimes(), "場次列表不應為 null");
    }

    @Test
    @DisplayName("應該能夠完整流程：查看電影 -> 選擇場次 -> 查看座位")
    void shouldCompleteFullBookingFlow() {
        // Step 1: 獲取電影列表
        List<Movie> movies = movieService.getMovies();
        assertFalse(movies.isEmpty(), "應該有電影可選");

        // Step 2: 選擇第一部電影
        Movie selectedMovie = movies.get(0);
        assertNotNull(selectedMovie, "選擇的電影不應為 null");

        // Step 3: 獲取電影詳情
        var movieDetail = movieService.getMovieWithAvailability(selectedMovie.getId());
        assertTrue(movieDetail.isPresent(), "應該能獲取電影詳情");

        // Step 4: 選擇場次
        var showtimes = movieDetail.get().getShowtimes();
        assertFalse(showtimes.isEmpty(), "應該有場次可選");
        var selectedShowtime = showtimes.get(0);

        // Step 5: 獲取座位佈局
        var seatLayout = movieService.getSeatLayout(
                selectedMovie.getId(), 
                selectedShowtime.getId());
        
        assertNotNull(seatLayout, "座位佈局不應為 null");
        assertEquals(96, seatLayout.getSeats().size(), "應該有 96 個座位");
        
        // 驗證有可用座位
        long availableSeats = seatLayout.getSeats().stream()
                .filter(seat -> !seat.isReserved())
                .count();
        assertTrue(availableSeats > 0, "應該有可用座位");
    }

    @Test
    @DisplayName("所有電影都應該有有效的場次信息")
    void shouldHaveValidShowtimesForAllMovies() {
        // When
        List<Movie> movies = movieService.getMovies();

        // Then
        for (Movie movie : movies) {
            assertNotNull(movie.getShowtimes(), 
                    "電影 " + movie.getTitle() + " 應該有場次列表");
            assertFalse(movie.getShowtimes().isEmpty(), 
                    "電影 " + movie.getTitle() + " 應該至少有一個場次");
            
            // 驗證每個場次都有必要的信息
            for (var showtime : movie.getShowtimes()) {
                assertNotNull(showtime.getId(), "場次 ID 不應為 null");
                assertNotNull(showtime.getStartTime(), "場次時間不應為 null");
                assertTrue(showtime.getDurationMinutes() > 0, "場次時長應該大於 0");
                assertNotNull(showtime.getAuditorium(), "影廳信息不應為 null");
            }
        }
    }

    @Test
    @DisplayName("每個場次的座位佈局應該一致且有效")
    void shouldHaveConsistentSeatLayoutForEachShowtime() {
        // Given
        List<Movie> movies = movieService.getMovies();
        Movie testMovie = movies.get(0);
        var showtime = testMovie.getShowtimes().get(0);

        // When
        var seatLayout = movieService.getSeatLayout(testMovie.getId(), showtime.getId());

        // Then
        assertEquals(12, seatLayout.getRows(), "應該有 12 排");
        assertEquals(8, seatLayout.getColumns(), "應該有 8 列");
        assertEquals(96, seatLayout.getSeats().size(), "總座位數應該是 96");

        // 驗證座位 ID 格式正確 (例如: A01, B02, etc.)
        for (var seat : seatLayout.getSeats()) {
            assertNotNull(seat.getSeatId(), "座位 ID 不應為 null");
            assertTrue(seat.getSeatId().matches("[A-L]\\d{2}"), 
                    "座位 ID 格式應該是字母+兩位數字: " + seat.getSeatId());
        }
    }

    @Test
    @DisplayName("應該能夠處理無效的電影 ID")
    void shouldHandleInvalidMovieId() {
        // Given
        String invalidMovieId = "invalid-movie-id";

        // When
        var result = movieService.getMovieWithAvailability(invalidMovieId);

        // Then
        assertFalse(result.isPresent(), "無效的電影 ID 應該返回空結果");
    }

    @Test
    @DisplayName("應該能夠處理無效的場次 ID")
    void shouldHandleInvalidShowtimeId() {
        // Given
        String validMovieId = "mv-01";
        String invalidShowtimeId = "invalid-showtime-id";

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            movieService.getSeatLayout(validMovieId, invalidShowtimeId);
        }, "無效的場次 ID 應該拋出異常");
    }

    @Test
    @DisplayName("電影海報 URL 應該有效")
    void shouldHaveValidPosterUrls() {
        // When
        List<Movie> movies = movieService.getMovies();

        // Then
        for (Movie movie : movies) {
            assertNotNull(movie.getPosterUrl(), 
                    "電影 " + movie.getTitle() + " 應該有海報 URL");
            assertFalse(movie.getPosterUrl().isEmpty(), 
                    "電影 " + movie.getTitle() + " 的海報 URL 不應為空");
        }
    }

    @Test
    @DisplayName("電影描述應該存在且不為空")
    void shouldHaveValidDescriptions() {
        // When
        List<Movie> movies = movieService.getMovies();

        // Then
        for (Movie movie : movies) {
            assertNotNull(movie.getDescription(), 
                    "電影 " + movie.getTitle() + " 應該有描述");
            assertFalse(movie.getDescription().isEmpty(), 
                    "電影 " + movie.getTitle() + " 的描述不應為空");
        }
    }
}

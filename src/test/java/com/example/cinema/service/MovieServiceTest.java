package com.example.cinema.service;

import com.example.cinema.model.Movie;
import com.example.cinema.model.SeatLayout;
import com.example.cinema.model.Showtime;
import com.example.cinema.model.ShowtimeDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MovieService 測試單元
 * 測試電影服務的核心功能
 */
@DisplayName("電影服務測試")
class MovieServiceTest {

    private MovieService movieService;

    @BeforeEach
    void setUp() {
        movieService = new MovieService();
    }

    @Test
    @DisplayName("應該返回所有電影列表")
    void shouldReturnAllMovies() {
        // When
        List<Movie> movies = movieService.getMovies();

        // Then
        assertNotNull(movies, "電影列表不應為 null");
        assertFalse(movies.isEmpty(), "電影列表不應為空");
        assertEquals(10, movies.size(), "應該有 10 部電影");
    }

    @Test
    @DisplayName("應該根據 ID 返回特定電影")
    void shouldReturnMovieById() {
        // Given
        String movieId = "mv-01";

        // When
        Optional<Movie> movie = movieService.getMovieWithAvailability(movieId);

        // Then
        assertTrue(movie.isPresent(), "應該找到電影");
        assertEquals(movieId, movie.get().getId(), "電影 ID 應該匹配");
        assertEquals("沙丘:第二部", movie.get().getTitle(), "電影標題應該匹配");
    }

    @Test
    @DisplayName("當電影 ID 不存在時應該返回空")
    void shouldReturnEmptyWhenMovieNotFound() {
        // Given
        String invalidMovieId = "mv-999";

        // When
        Optional<Movie> movie = movieService.getMovieWithAvailability(invalidMovieId);

        // Then
        assertFalse(movie.isPresent(), "不存在的電影應該返回空");
    }

    @Test
    @DisplayName("應該返回電影的場次信息")
    void shouldReturnShowtimeForMovie() {
        // Given
        String movieId = "mv-01";
        String showtimeId = "mv-01-st1";

        // When
        Optional<Showtime> showtime = movieService.getShowtime(movieId, showtimeId);

        // Then
        assertTrue(showtime.isPresent(), "應該找到場次");
        assertEquals(showtimeId, showtime.get().getId(), "場次 ID 應該匹配");
        assertEquals("09:30", showtime.get().getStartTime(), "場次時間應該匹配");
    }

    @Test
    @DisplayName("當場次 ID 不存在時應該返回空")
    void shouldReturnEmptyWhenShowtimeNotFound() {
        // Given
        String movieId = "mv-01";
        String invalidShowtimeId = "mv-01-st999";

        // When
        Optional<Showtime> showtime = movieService.getShowtime(movieId, invalidShowtimeId);

        // Then
        assertFalse(showtime.isPresent(), "不存在的場次應該返回空");
    }

    @Test
    @DisplayName("應該返回座位佈局")
    void shouldReturnSeatLayout() {
        // Given
        String movieId = "mv-01";
        String showtimeId = "mv-01-st1";

        // When
        SeatLayout seatLayout = movieService.getSeatLayout(movieId, showtimeId);

        // Then
        assertNotNull(seatLayout, "座位佈局不應為 null");
        assertEquals(12, seatLayout.getRows(), "應該有 12 排");
        assertEquals(8, seatLayout.getColumns(), "應該有 8 列");
        assertEquals(96, seatLayout.getSeats().size(), "應該有 96 個座位");
    }

    @Test
    @DisplayName("應該返回場次詳細信息包含座位佈局")
    void shouldReturnShowtimeDetails() {
        // Given
        String movieId = "mv-01";
        String showtimeId = "mv-01-st1";

        // When
        ShowtimeDetails details = movieService.getShowtimeDetails(movieId, showtimeId);

        // Then
        assertNotNull(details, "場次詳情不應為 null");
        assertNotNull(details.getShowtime(), "場次信息不應為 null");
        assertNotNull(details.getSeatLayout(), "座位佈局不應為 null");
        assertEquals(showtimeId, details.getShowtime().getId(), "場次 ID 應該匹配");
    }

    @Test
    @DisplayName("當場次不存在時獲取座位佈局應該拋出異常")
    void shouldThrowExceptionWhenGettingSeatLayoutForInvalidShowtime() {
        // Given
        String movieId = "mv-01";
        String invalidShowtimeId = "mv-01-st999";

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            movieService.getSeatLayout(movieId, invalidShowtimeId);
        }, "應該拋出 IllegalArgumentException");
    }

    @Test
    @DisplayName("電影應該包含場次列表")
    void shouldReturnMovieWithShowtimes() {
        // Given
        String movieId = "mv-01";

        // When
        Optional<Movie> movie = movieService.getMovieWithAvailability(movieId);

        // Then
        assertTrue(movie.isPresent(), "應該找到電影");
        assertNotNull(movie.get().getShowtimes(), "場次列表不應為 null");
        assertFalse(movie.get().getShowtimes().isEmpty(), "場次列表不應為空");
    }

    @Test
    @DisplayName("座位佈局應該包含已預訂和可用座位")
    void shouldHaveBothReservedAndAvailableSeats() {
        // Given
        String movieId = "mv-01";
        String showtimeId = "mv-01-st1";

        // When
        SeatLayout seatLayout = movieService.getSeatLayout(movieId, showtimeId);

        // Then
        long reservedCount = seatLayout.getSeats().stream()
                .filter(seat -> seat.isReserved())
                .count();
        long availableCount = seatLayout.getSeats().stream()
                .filter(seat -> !seat.isReserved())
                .count();

        assertTrue(reservedCount > 0, "應該有已預訂的座位");
        assertTrue(availableCount > 0, "應該有可用的座位");
        assertEquals(96, reservedCount + availableCount, "總座位數應該是 96");
    }

    @Test
    @DisplayName("相同場次的座位佈局應該保持一致")
    void shouldReturnConsistentSeatLayoutForSameShowtime() {
        // Given
        String movieId = "mv-01";
        String showtimeId = "mv-01-st1";

        // When
        SeatLayout layout1 = movieService.getSeatLayout(movieId, showtimeId);
        SeatLayout layout2 = movieService.getSeatLayout(movieId, showtimeId);

        // Then
        assertEquals(layout1.getSeats().size(), layout2.getSeats().size(), 
                "兩次獲取的座位數量應該相同");
        
        for (int i = 0; i < layout1.getSeats().size(); i++) {
            assertEquals(layout1.getSeats().get(i).getSeatId(), 
                        layout2.getSeats().get(i).getSeatId(),
                        "座位 ID 應該相同");
            assertEquals(layout1.getSeats().get(i).isReserved(), 
                        layout2.getSeats().get(i).isReserved(),
                        "座位預訂狀態應該相同");
        }
    }
}

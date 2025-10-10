package com.example.cinema.service;

import com.example.cinema.dto.MovieDto;
import com.example.cinema.dto.ShowtimeDto;
import com.example.cinema.model.Movie;
import com.example.cinema.model.SeatLayout;
import com.example.cinema.model.SeatStatus;
import com.example.cinema.model.Showtime;
import com.example.cinema.model.ShowtimeDetails;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class MovieService {
    private static final int ROW_COUNT = 12;
    private static final int COLUMN_COUNT = 8;

    private final Map<String, Movie> movies;

    public MovieService() {
        List<Movie> movieList = List.of(
                createMovie("mv-01", "沙丘：第二部", "命運正在等你", "/images/dune-part-two.jpg",
                        "保羅·亞崔迪與弗瑞曼人聯手復仇，阻止可怕的未來成真。",
                        showtimes("mv-01", 166, "09:30", "12:15", "15:00", "18:40", "21:25", "23:00")),
                createMovie("mv-02", "奧本海默", "世界的毀滅者", "/images/cinema1.png",
                        "講述J·羅伯特·奧本海默與原子彈誕生的真實歷程。",
                        showtimes("mv-02", 180, "08:50", "11:45", "14:30", "17:30", "20:40", "23:00")),
                createMovie("mv-03", "蜘蛛人：穿越新宇宙", "多重宇宙冒險", "/images/spider-verse.jpg",
                        "麥爾斯·莫拉雷斯展開全新跨越多重宇宙的高速冒險。",
                        showtimes("mv-03", 140, "10:20", "12:40", "15:20", "18:05", "21:10", "23:00")),
                createMovie("mv-04", "星際異攻隊3", "最後一次旅程", "https://image.tmdb.org/t/p/w500/r2J02Z2OpNTctfOSN1Ydgii51I3.jpg",
                        "星際異攻隊為了守護夥伴與宇宙，再度踏上驚險任務。",
                        showtimes("mv-04", 150, "09:10", "12:10", "15:10", "18:10", "21:15", "23:00")),
                createMovie("mv-05", "芭比", "最閃耀的芭比", "https://image.tmdb.org/t/p/w500/iuFNMS8U5cb6xfzi51Dbkovj7vM.jpg",
                        "芭比離開芭比樂園前往現實世界，展開自我探索之旅。",
                        showtimes("mv-05", 114, "11:00", "13:20", "15:40", "18:00", "20:30", "23:00")),
                createMovie("mv-06", "不可能的任務：致命清算", "誰都不能信", "https://image.tmdb.org/t/p/w500/NNxYkU70HPurnNCSiCjYAmacwm.jpg",
                        "伊森·韓特率領IMF團隊阻止致命武器落入不法之徒手中。",
                        showtimes("mv-06", 163, "08:30", "11:45", "15:00", "18:10", "21:25", "23:00")),
                createMovie("mv-07", "捍衛任務4", "無路可退", "https://image.tmdb.org/t/p/w500/vZloFAK7NmvMGKE7VkF5UHaz0I.jpg",
                        "約翰·維克找到對抗高桌會的途徑，但自由必須付出代價。",
                        showtimes("mv-07", 169, "11:20", "14:10", "17:00", "19:50", "22:40")),
                createMovie("mv-08", "蝙蝠俠", "揭開真相", "https://image.tmdb.org/t/p/w500/74xTEgt7R36Fpooo50r9T25onhq.jpg",
                        "蝙蝠俠追查連環殺手留下的謎題，深入高譚市的黑暗角落。",
                        showtimes("mv-08", 176, "09:00", "12:20", "15:40", "19:00", "22:20", "23:00")),
                createMovie("mv-09", "阿凡達：水之道", "重返潘朵拉", "https://image.tmdb.org/t/p/w500/t6HIqrRAclMCA60NsSmeqe9RmNV.jpg",
                        "傑克與奈蒂莉在潘朵拉撫養家人，同時面對熟悉的威脅回歸。",
                        showtimes("mv-09", 192, "10:00", "13:20", "16:40", "20:00", "23:20")),
                createMovie("mv-10", "黑豹2：瓦干達萬歲", "瓦干達萬歲", "https://image.tmdb.org/t/p/w500/ps2oKfhY6DL3alynlSqY97gHSsg.jpg",
                        "皇后拉蒙達與舒莉率領朵拉親衛隊，守護瓦干達免受列強覬覦。",
                        showtimes("mv-10", 161, "09:10", "12:20", "15:30", "18:40", "21:50", "23:00"))
        );

        this.movies = movieList.stream().collect(Collectors.toMap(Movie::getId, movie -> movie));
    }

    public List<Movie> getMovies() {
        return new ArrayList<>(movies.values());
    }

    public Optional<Movie> getMovie(String movieId) {
        return Optional.ofNullable(movies.get(movieId));
    }

    public Optional<MovieDto> getMovieDto(String movieId) {
        Optional<Movie> movieOptional = getMovie(movieId);
        if (movieOptional.isEmpty()) {
            return Optional.empty();
        }

        Movie movie = movieOptional.get();
        final LocalTime OPERATIONAL_DAY_START = LocalTime.of(5, 0);
        LocalTime now = LocalTime.now(ZoneId.of("Asia/Taipei"));
        LocalTime effectiveNow = now.isBefore(OPERATIONAL_DAY_START) ? now.plusHours(24) : now;

        List<Showtime> upcomingShowtimes = new ArrayList<>();
        for (Showtime showtime : movie.getShowtimes()) {
            LocalTime startTime = LocalTime.parse(showtime.getStartTime());
            LocalTime effectiveStartTime = startTime.isBefore(OPERATIONAL_DAY_START) ? startTime.plusHours(24) : startTime;
            if (effectiveStartTime.isAfter(effectiveNow)) {
                upcomingShowtimes.add(showtime);
            }
        }

        upcomingShowtimes.sort(Comparator.comparing(s -> {
            LocalTime startTime = LocalTime.parse(s.getStartTime());
            return startTime.isBefore(OPERATIONAL_DAY_START) ? startTime.plusHours(24) : startTime;
        }));

        Set<String> bookableShowtimeIds = upcomingShowtimes.stream()
                .limit(2)
                .map(Showtime::getId)
                .collect(Collectors.toSet());

        List<ShowtimeDto> showtimeDtos = movie.getShowtimes().stream()
                .map(showtime -> new ShowtimeDto(
                        showtime.getId(),
                        showtime.getStartTime(),
                        showtime.getDurationMinutes(),
                        showtime.getAuditorium(),
                        bookableShowtimeIds.contains(showtime.getId())
                ))
                .collect(Collectors.toList());

        return Optional.of(new MovieDto(movie, showtimeDtos));
    }

    public Optional<Showtime> getShowtime(String movieId, String showtimeId) {
        return getMovie(movieId)
                .flatMap(movie -> movie.getShowtimes().stream()
                        .filter(showtime -> showtime.getId().equals(showtimeId))
                        .findFirst());
    }

    public SeatLayout getSeatLayout(String movieId, String showtimeId) {
        return getShowtimeDetails(movieId, showtimeId).getSeatLayout();
    }

    public ShowtimeDetails getShowtimeDetails(String movieId, String showtimeId) {
        Showtime showtime = getShowtime(movieId, showtimeId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Showtime not found for movie: " + movieId + " and showtime: " + showtimeId));

        List<SeatStatus> seats = generateSeats(showtimeId);
        SeatLayout layout = new SeatLayout(showtimeId, ROW_COUNT, COLUMN_COUNT, seats);
        return new ShowtimeDetails(showtime, layout);
    }

    private Movie createMovie(String id, String title, String subtitle, String posterUrl, String description, List<Showtime> showtimes) {
        return new Movie(id, title, subtitle, posterUrl, description, showtimes);
    }

    private List<Showtime> showtimes(String movieId, int durationMinutes, String... startTimes) {
        List<Showtime> entries = new ArrayList<>();
        for (int i = 0; i < startTimes.length; i++) {
            entries.add(new Showtime(movieId + "-st" + (i + 1), startTimes[i], durationMinutes,
                    "第" + ((i % 3) + 1) + "廳"));
        }
        return entries;
    }

    private List<SeatStatus> generateSeats(String showtimeId) {
        Random random = new Random(showtimeId.hashCode());
        Map<String, Boolean> reservedLookup = new HashMap<>();
        int reservedSeats = 15 + random.nextInt(10);
        while (reservedLookup.size() < reservedSeats) {
            int row = random.nextInt(ROW_COUNT);
            int col = random.nextInt(COLUMN_COUNT);
            reservedLookup.putIfAbsent(buildSeatId(row, col), Boolean.TRUE);
        }

        List<SeatStatus> seats = new ArrayList<>(ROW_COUNT * COLUMN_COUNT);
        for (int row = 0; row < ROW_COUNT; row++) {
            for (int col = 0; col < COLUMN_COUNT; col++) {
                String seatId = buildSeatId(row, col);
                boolean reserved = reservedLookup.containsKey(seatId);
                seats.add(new SeatStatus(seatId, reserved));
            }
        }
        return seats;
    }

    private String buildSeatId(int row, int column) {
        char rowLetter = (char) ('A' + row);
        return rowLetter + String.format("%02d", column + 1);
    }
}
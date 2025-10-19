package com.example.cinema.service;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.example.cinema.model.Movie;
import com.example.cinema.model.SeatLayout;
import com.example.cinema.model.SeatStatus;
import com.example.cinema.model.Showtime;
import com.example.cinema.model.ShowtimeDetails;

@Service
public class MovieService {

    private static final int ROW_COUNT = 12;
    private static final int COLUMN_COUNT = 8;
    private static final ZoneId LOCAL_ZONE = ZoneId.of("Asia/Taipei");
    private static final LocalTime OPERATIONAL_DAY_START = LocalTime.of(5, 0);

    private final Map<String, Movie> catalog;

    public MovieService() {
        List<Movie> seeds = List.of(
                createMovie("mv-01", "沙丘:第二部", "", "/images/dune-part-two.jpg",
                        "保羅亞崔迪與錢妮以及弗瑞曼人聯手,向毀滅他家族的陰謀者展開報復。",
                        showtimes("mv-01", 166, "09:30", "12:15", "15:00", "18:40", "21:25", "23:00")),

                createMovie("mv-02", "奧本海默", "", "/images/cinema1.png",
                        "羅伯特奧本海默的一生,從他在原子彈研發中的角色,到他面臨的道德困境。",
                        showtimes("mv-02", 180, "08:50", "11:45", "14:30", "17:30", "20:40", "23:00")),

                createMovie("mv-03", "蜘蛛人:穿越新宇宙", "", "/images/spider-verse.jpg",
                        "邁爾斯摩拉斯再次穿越多重宇宙,與其他蜘蛛人並肩作戰。",
                        showtimes("mv-03", 140, "10:20", "12:40", "15:20", "18:05", "21:10", "23:00")),

                createMovie("mv-04", "星際異攻隊3", "", "https://image.tmdb.org/t/p/w500/r2J02Z2OpNTctfOSN1Ydgii51I3.jpg",
                        "星爵與他的團隊踏上一場全新的冒險,面對來自宇宙的威脅。",
                        showtimes("mv-04", 150, "09:10", "12:10", "15:10", "18:10", "21:15", "23:00")),

                createMovie("mv-05", "芭比", "", "https://image.tmdb.org/t/p/w500/iuFNMS8U5cb6xfzi51Dbkovj7vM.jpg",
                        "芭比與肯踏上現實世界的旅程,發現真實生活的美好與挑戰。",
                        showtimes("mv-05", 114, "11:00", "13:20", "15:40", "18:00", "20:30", "23:00")),

                createMovie("mv-06", "不可能的任務:致命清算", "",
                        "https://image.tmdb.org/t/p/w500/NNxYkU70HPurnNCSiCjYAmacwm.jpg",
                        "IMF探員伊森韓特面對他職業生涯中最致命的任務。",
                        showtimes("mv-06", 163, "08:30", "11:45", "15:00", "18:10", "21:25", "23:00")),

                createMovie("mv-07", "捍衛任務4", "", "https://image.tmdb.org/t/p/w500/vZloFAK7NmvMGKE7VkF5UHaz0I.jpg",
                        "約翰維克尋找擊敗高桌會的方法,以贏回他的自由。",
                        showtimes("mv-07", 169, "11:20", "14:10", "17:00", "19:50", "22:40")),

                createMovie("mv-08", "蝙蝠俠", "", "https://image.tmdb.org/t/p/w500/74xTEgt7R36Fpooo50r9T25onhq.jpg",
                        "布魯斯韋恩在高譚市擔任蝙蝠俠的第二年,追蹤一名殘忍的連環殺手。",
                        showtimes("mv-08", 176, "09:00", "12:20", "15:40", "19:00", "22:20", "23:00")),

                createMovie("mv-09", "阿凡達:水之道", "", "https://image.tmdb.org/t/p/w500/t6HIqrRAclMCA60NsSmeqe9RmNV.jpg",
                        "傑克蘇里與娜蒂莉在潘朵拉星球建立家庭,面對新的威脅。",
                        showtimes("mv-09", 192, "10:00", "13:20", "16:40", "20:00", "23:20")),

                createMovie("mv-10", "黑豹2:瓦干達萬歲", "", "https://image.tmdb.org/t/p/w500/ps2oKfhY6DL3alynlSqY97gHSsg.jpg",
                        "瓦干達王國的領袖們為了保護國家,與強大的海底王國對抗。",
                        showtimes("mv-10", 161, "09:10", "12:20", "15:30", "18:40", "21:50", "23:00")));

        this.catalog = seeds.stream().collect(Collectors.toMap(Movie::getId, movie -> movie));
    }

    public List<Movie> getMovies() {
        return catalog.values().stream()
                .map(this::enrichMovie)
                .collect(Collectors.toList());
    }

    public Optional<Movie> getMovieWithAvailability(String movieId) {
        return Optional.ofNullable(catalog.get(movieId)).map(this::enrichMovie);
    }

    public Optional<Showtime> getShowtime(String movieId, String showtimeId) {
        return Optional.ofNullable(catalog.get(movieId))
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
                        "Showtime not found for movie " + movieId + " and id " + showtimeId));

        List<SeatStatus> seats = generateSeats(showtimeId);
        SeatLayout layout = new SeatLayout(showtimeId, ROW_COUNT, COLUMN_COUNT, seats);
        return new ShowtimeDetails(showtime, layout);
    }

    private Movie enrichMovie(Movie movie) {
        List<Showtime> base = movie.getShowtimes();
        LocalTime now = LocalTime.now(LOCAL_ZONE);
        LocalTime effectiveNow = now.isBefore(OPERATIONAL_DAY_START) ? now.plusHours(24) : now;

        List<Showtime> upcoming = new ArrayList<>();
        for (Showtime showtime : base) {
            LocalTime start = LocalTime.parse(showtime.getStartTime());
            LocalTime normalized = start.isBefore(OPERATIONAL_DAY_START) ? start.plusHours(24) : start;
            if (normalized.isAfter(effectiveNow)) {
                upcoming.add(showtime);
            }
        }

        upcoming.sort(Comparator.comparing(s -> {
            LocalTime start = LocalTime.parse(s.getStartTime());
            return start.isBefore(OPERATIONAL_DAY_START) ? start.plusHours(24) : start;
        }));

        Set<String> bookableIds = upcoming.stream()
                .limit(2)
                .map(Showtime::getId)
                .collect(Collectors.toSet());

        List<Showtime> decorated = base.stream()
                .map(showtime -> showtime.withBookable(bookableIds.contains(showtime.getId())))
                .collect(Collectors.toList());

        return new Movie(movie.getId(), movie.getTitle(), movie.getSubtitle(),
                movie.getPosterUrl(), movie.getDescription(), decorated);
    }

    private Movie createMovie(String id, String title, String subtitle, String posterUrl,
            String description, List<Showtime> showtimes) {
        return new Movie(id, title, subtitle, posterUrl, description, showtimes);
    }

    private List<Showtime> showtimes(String movieId, int durationMinutes, String... startTimes) {
        List<Showtime> entries = new ArrayList<>();
        for (int i = 0; i < startTimes.length; i++) {
            entries.add(new Showtime(
                    movieId + "-st" + (i + 1),
                    startTimes[i],
                    durationMinutes,
                    (i % 3+1) + "號廳"));
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

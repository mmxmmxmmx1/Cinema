package com.example.cinema.service;

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
                createMovie("mv-01", "沙丘：第二部", "史詩級科幻冒險", "/images/dune-part-two.jpg",
                        "保羅．亞崔迪與弗瑞曼人攜手展開復仇，並對抗帝國勢力的陰謀。",
                        showtimes("mv-01", 166, "09:30", "12:15", "15:00", "18:40", "21:25", "23:00")),
                createMovie("mv-02", "奧本海默", "世紀原子風暴", "/images/cinema1.png",
                        "羅伯特．奧本海默主導曼哈頓計畫，見證原子時代的誕生與代價。",
                        showtimes("mv-02", 180, "08:50", "11:45", "14:30", "17:30", "20:40", "23:00")),
                createMovie("mv-03", "蜘蛛人：穿越新宇宙", "多重宇宙新篇章", "/images/spider-verse.jpg",
                        "邁爾斯．莫拉雷斯與蜘蛛軍團橫跨多重宇宙，阻止未來災難的高速冒險。",
                        showtimes("mv-03", 140, "10:20", "12:40", "15:20", "18:05", "21:10", "23:00")),
                createMovie("mv-04", "星際異攻隊 3", "銀河守護者最終章", "https://image.tmdb.org/t/p/w500/r2J02Z2OpNTctfOSN1Ydgii51I3.jpg",
                        "星爵與夥伴展開最後任務，在拯救火箭浣熊的同時守護銀河。",
                        showtimes("mv-04", 150, "09:10", "12:10", "15:10", "18:10", "21:15", "23:00")),
                createMovie("mv-05", "芭比", "粉紅炫風", "https://image.tmdb.org/t/p/w500/iuFNMS8U5cb6xfzi51Dbkovj7vM.jpg",
                        "芭比離開完美世界前往現實生活，踏上幽默又感性的自我探索之旅。",
                        showtimes("mv-05", 114, "11:00", "13:20", "15:40", "18:00", "20:30", "23:00")),
                createMovie("mv-06", "不可能的任務：致命清算", "無法停歇的戰鬥", "https://image.tmdb.org/t/p/w500/NNxYkU70HPurnNCSiCjYAmacwm.jpg",
                        "伊森．韓特與 IMF 團隊追查失控的 AI 武器，展開全球高強度任務。",
                        showtimes("mv-06", 163, "08:30", "11:45", "15:00", "18:10", "21:25", "23:00")),
                createMovie("mv-07", "捍衛任務 4", "殺神最後通牒", "https://image.tmdb.org/t/p/w500/vZloFAK7NmvMGKE7VkF5UHaz0I.jpg",
                        "約翰．維克對抗高桌會，為追求自由展開最艱鉅的復仇旅程。",
                        showtimes("mv-07", 169, "11:20", "14:10", "17:00", "19:50", "22:40")),
                createMovie("mv-08", "蝙蝠俠", "黑暗騎士覺醒", "https://image.tmdb.org/t/p/w500/74xTEgt7R36Fpooo50r9T25onhq.jpg",
                        "蝙蝠俠追查連環殺人案，揭露哥譚市權力與腐敗交織的陰謀。",
                        showtimes("mv-08", 176, "09:00", "12:20", "15:40", "19:00", "22:20", "23:00")),
                createMovie("mv-09", "阿凡達：水之道", "潘朵拉再臨", "https://image.tmdb.org/t/p/w500/t6HIqrRAclMCA60NsSmeqe9RmNV.jpg",
                        "傑克．蘇利一家面對新威脅，與海洋部落結盟展開壯闊冒險。",
                        showtimes("mv-09", 192, "10:00", "13:20", "16:40", "20:00", "23:20")),
                createMovie("mv-10", "黑豹 2：瓦干達萬歲", "瓦干達新世代", "https://image.tmdb.org/t/p/w500/ps2oKfhY6DL3alynlSqY97gHSsg.jpg",
                        "瓦干達王國面對強敵入侵，新一代繼承人挺身守護家園與傳承。",
                        showtimes("mv-10", 161, "09:10", "12:20", "15:30", "18:40", "21:50", "23:00"))
        );

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
                .orElseThrow(() -> new IllegalArgumentException("Showtime not found for movie " + movieId + " and id " + showtimeId));

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

        return new Movie(movie.getId(), movie.getTitle(), movie.getSubtitle(), movie.getPosterUrl(), movie.getDescription(), decorated);
    }

    private Movie createMovie(String id, String title, String subtitle, String posterUrl, String description, List<Showtime> showtimes) {
        return new Movie(id, title, subtitle, posterUrl, description, showtimes);
    }

    private List<Showtime> showtimes(String movieId, int durationMinutes, String... startTimes) {
        List<Showtime> entries = new ArrayList<>();
        for (int i = 0; i < startTimes.length; i++) {
            entries.add(new Showtime(
                    movieId + "-st" + (i + 1),
                    startTimes[i],
                    durationMinutes,
                    "第" + ((i % 3) + 1) + "廳"
            ));
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

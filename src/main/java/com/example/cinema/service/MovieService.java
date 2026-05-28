package com.example.cinema.service;

import java.time.LocalTime;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.cinema.config.AppClock;
import com.example.cinema.model.Movie;
import com.example.cinema.model.SeatLayout;
import com.example.cinema.model.SeatStatus;
import com.example.cinema.model.Showtime;
import com.example.cinema.model.ShowtimeDetails;

@Service
public class MovieService {

    private static final int ROW_COUNT = 12;
    private static final int COLUMN_COUNT = 8;
    private static final LocalTime BOOKING_OPEN_TIME = LocalTime.of(7, 0);
    private static final LocalTime BOOKING_WARNING_TIME = LocalTime.of(22, 40);
    private static final LocalTime BOOKING_CLOSE_TIME = LocalTime.of(22, 45);
    private static final LocalTime OPERATIONAL_DAY_START = LocalTime.of(5, 0);

    private final Map<String, Movie> fallbackCatalog;
    @Value("${app.order.pending-timeout-minutes:15}")
    private int pendingTimeoutMinutes;
    private JdbcTemplate jdbcTemplate;

    public MovieService() {
        List<Movie> seeds = List.of(
                createMovie(
                        "mv-01",
                        "貓砂:第二部",
                        "",
                        "/images/貓砂(1).png",
                        "/images/貓砂(2).png",
                        "保羅貓崔迪與貓妮以及弗瑞曼貓人聯手,向毀滅他家族的陰謀者展開報復。",
                        showtimes("mv-01", 166, "09:30", "12:15", "15:00", "18:40", "21:25", "23:00")),

                createMovie(
                        "mv-02",
                        "貓本海默",
                        "",
                        "/images/貓本海默(1).png",
                        "/images/貓本海默(2).png",
                        "貓伯特貓本海默的一生,從他在原子彈研發中的角色,到他面臨的道德困境。",
                        showtimes("mv-02", 180, "08:50", "11:45", "14:30", "17:30", "20:40", "23:00")),

                createMovie(
                        "mv-03",
                        "狗狗人:穿越新宇宙",
                        "",
                        "/images/狗狗人(1).png",
                        "/images/狗狗人(2).png",
                        "狗爾斯摩拉斯再次穿越多重宇宙,與其他狗狗人並肩作戰。",
                        showtimes("mv-03", 140, "10:20", "12:40", "15:20", "18:05", "21:10", "23:00")),

                createMovie(
                        "mv-04",
                        "星際貓攻隊3",
                        "",
                        "/images/星際貓攻隊3(1).png",
                        "/images/星際貓攻隊3(2).png",
                        "貓爵與他的團隊踏上一場全新的冒險,面對來自宇宙的威脅。",
                        showtimes("mv-04", 150, "09:10", "12:10", "15:10", "18:10", "21:15", "23:00")),

                createMovie(
                        "mv-05",
                        "狗比",
                        "",
                        "/images/狗比(1).png",
                        "/images/狗比(2).png",
                        "狗比與肯踏上現實世界的旅程,發現真實生活的美好與挑戰。",
                        showtimes("mv-05", 114, "11:00", "13:20", "15:40", "18:00", "20:30", "23:00")),

                createMovie("mv-06", "不可能的任務-貓咪神算", "",
                        "/images/不可能的任務-貓咪神算(1).png",
                        "/images/不可能的任務-貓咪神算(2).png",
                        "TICA 探員貓森韓特面對他職業生涯中最致命的任務。",
                        showtimes("mv-06", 163, "08:30", "11:45", "15:00", "18:10", "21:25", "23:00")),

                createMovie(
                        "mv-07",
                        "捍衛狗狗4",
                        "",
                        "/images/捍衛狗狗4(2_3).png",
                        "/images/捍衛狗狗4(16_9).png",
                        "約翰狗狗尋找擊敗高桌會的方法,以贏回他的自由。",
                        showtimes("mv-07", 169, "11:20", "14:10", "17:00", "19:50", "22:40")),

                createMovie(
                        "mv-08",
                        "貓貓俠",
                        "",
                        "/images/貓貓俠(1).png",
                        "/images/貓貓俠(2).png",
                        "布魯斯貓恩在高譚市擔任蝙蝠俠的第二年,追蹤一名殘忍的連環殺手。",
                        showtimes("mv-08", 176, "09:00", "12:20", "15:40", "19:00", "22:20", "23:00")),

                createMovie(
                        "mv-09",
                        "阿凡狗_誰之道",
                        "",
                        "/images/阿凡狗_誰之道(1).png",
                        "/images/阿凡狗_誰之道(2).png",
                        "狗克蘇里與狗蒂莉在潘朵拉星球建立家庭,面對新的威脅。",
                        showtimes("mv-09", 192, "10:00", "13:20", "16:40", "20:00", "23:20")),

                createMovie(
                        "mv-10",
                        "黑貓-烏干達萬歲",
                        "",
                        "/images/黑貓-烏干達萬歲(1).png",
                        "/images/黑貓-烏干達萬歲(2).png",
                        "烏干達王國的領袖們為了保護國家,與強大的海底王國對抗。",
                        showtimes("mv-10", 161, "09:10", "12:20", "15:30", "18:40", "21:50", "23:00")));

        this.fallbackCatalog = seeds.stream().collect(Collectors.toMap(Movie::getId, movie -> movie));
    }

    @Autowired(required = false)
    void setJdbcTemplate(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Movie> getMovies() {
        Map<String, Movie> catalog = effectiveCatalog();
        Map<String, List<ShowtimeOverrideRow>> overrides = loadOverridesByMovieIds(catalog.keySet());
        return catalog.values().stream()
                .map(movie -> movieWithOverrides(movie, overrides.get(movie.getId())))
                .map(this::enrichMovie)
                .collect(Collectors.toList());
    }

    public Optional<Movie> getMovieWithAvailability(String movieId) {
        Map<String, Movie> catalog = effectiveCatalog();
        Map<String, List<ShowtimeOverrideRow>> overrides = loadOverridesByMovieIds(Collections.singleton(movieId));
        return Optional.ofNullable(catalog.get(movieId))
                .map(movie -> movieWithOverrides(movie, overrides.get(movie.getId())))
                .map(this::enrichMovie);
    }

    public Optional<Movie> getMovieWithUpcomingShowtimes(String movieId) {
        Map<String, Movie> catalog = effectiveCatalog();
        Map<String, List<ShowtimeOverrideRow>> overrides = loadOverridesByMovieIds(Collections.singleton(movieId));
        return Optional.ofNullable(catalog.get(movieId))
                .map(movie -> movieWithOverrides(movie, overrides.get(movie.getId())))
                .map(this::enrichMovieForCustomer);
    }

    public Optional<Showtime> getShowtime(String movieId, String showtimeId) {
        Map<String, Movie> catalog = effectiveCatalog();
        Map<String, List<ShowtimeOverrideRow>> overrides = loadOverridesByMovieIds(Collections.singleton(movieId));
        return Optional.ofNullable(catalog.get(movieId))
                .map(movie -> movieWithOverrides(movie, overrides.get(movie.getId())))
                .flatMap(movie -> movie.getShowtimes().stream()
                        .filter(showtime -> showtime.getId().equals(showtimeId))
                        .findFirst());
    }

    public List<MovieCatalogItem> listCatalogItems() {
        return effectiveCatalog().values().stream()
                .map(m -> new MovieCatalogItem(m.getId(), m.getTitle()))
                .sorted(Comparator.comparing(MovieCatalogItem::title))
                .toList();
    }

    public List<Showtime> listConfiguredShowtimes(String movieId) {
        Map<String, Movie> catalog = effectiveCatalog();
        Movie movie = catalog.get(movieId);
        if (movie == null) {
            return List.of();
        }
        Movie configured = movieWithOverrides(movie, loadOverridesByMovieIds(Collections.singleton(movieId)).get(movieId));
        return configured.getShowtimes().stream()
                .sorted(Comparator.comparing(s -> normalizeShowtimeTime(LocalTime.parse(s.getStartTime()))))
                .toList();
    }

    public List<MovieAdminItem> listMovieAdminItems() {
        List<MovieAdminItem> fromDb = loadMovieAdminItemsFromDatabase();
        if (!fromDb.isEmpty()) {
            return fromDb;
        }
        return fallbackCatalog.values().stream()
                .map(movie -> new MovieAdminItem(
                        movie.getId(),
                        movie.getTitle(),
                        movie.getSubtitle(),
                        movie.getPosterUrl(),
                        movie.getCarouselImageUrl(),
                        movie.getDescription(),
                        true,
                        0))
                .sorted(Comparator.comparing(MovieAdminItem::id))
                .toList();
    }

    public Optional<MovieAdminItem> getMovieAdminItem(String movieId) {
        if (movieId == null || movieId.isBlank()) {
            return Optional.empty();
        }
        String safeMovieId = movieId.trim();
        return listMovieAdminItems().stream()
                .filter(item -> safeMovieId.equals(item.id()))
                .findFirst();
    }

    @Transactional
    public void saveMovieCatalog(
            String movieId,
            String title,
            String subtitle,
            String posterUrl,
            String carouselImageUrl,
            String description,
            int sortOrder,
            boolean enabled,
            String operator) {
        String safeMovieId = normalizeId(movieId, "movieId");
        String safeTitle = normalizeMovieTitle(title);
        String safeSubtitle = normalizeOptional(subtitle, 120);
        String safePosterUrl = normalizePosterUrl(posterUrl);
        String safeCarouselImageUrl = normalizePosterUrl(carouselImageUrl);
        String safeDescription = normalizeOptional(description, 1000);
        int safeSortOrder = Math.max(0, Math.min(10_000, sortOrder));
        String safeOperator = operator == null || operator.isBlank() ? "unknown" : operator.trim();

        boolean updated = false;
        boolean useFallback = jdbcTemplate == null;
        if (jdbcTemplate != null) {
            try {
                jdbcTemplate.update(
                        "INSERT INTO movie_catalog (movie_id, title, subtitle, poster_url, carousel_image_url, description, enabled, sort_order) " +
                                "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                                "ON DUPLICATE KEY UPDATE " +
                                "title = VALUES(title), subtitle = VALUES(subtitle), poster_url = VALUES(poster_url), " +
                                "carousel_image_url = VALUES(carousel_image_url), description = VALUES(description), " +
                                "enabled = VALUES(enabled), sort_order = VALUES(sort_order)",
                        safeMovieId,
                        safeTitle,
                        safeSubtitle,
                        safePosterUrl,
                        safeCarouselImageUrl,
                        safeDescription,
                        enabled ? 1 : 0,
                        safeSortOrder);
                updated = true;
            } catch (DataAccessException ex) {
                useFallback = true;
            }
        }

        if (useFallback) {
            Movie current = fallbackCatalog.get(safeMovieId);
            if (!enabled) {
                fallbackCatalog.remove(safeMovieId);
                updated = true;
            } else {
                List<Showtime> showtimes = current != null
                        ? current.getShowtimes()
                        : showtimes(safeMovieId, 120, "09:30", "14:00", "18:30");
                fallbackCatalog.put(
                        safeMovieId,
                        new Movie(
                                safeMovieId,
                                safeTitle,
                                safeSubtitle,
                                safePosterUrl,
                                safeCarouselImageUrl,
                                safeDescription,
                                showtimes));
                updated = true;
            }
        }

        if (!updated) {
            throw new IllegalStateException("電影資料儲存失敗。");
        }

        appendAuditLog(
                safeOperator,
                "MOVIE_CATALOG_SAVE",
                safeMovieId,
                "enabled=" + (enabled ? 1 : 0) + ",sortOrder=" + safeSortOrder);
    }

    @Transactional
    public void setMovieEnabled(String movieId, boolean enabled, String operator) {
        String safeMovieId = normalizeId(movieId, "movieId");
        String safeOperator = operator == null || operator.isBlank() ? "unknown" : operator.trim();
        boolean updated = false;

        if (jdbcTemplate != null) {
            try {
                int count = jdbcTemplate.update(
                        "UPDATE movie_catalog SET enabled = ? WHERE movie_id = ?",
                        enabled ? 1 : 0,
                        safeMovieId);
                updated = count > 0;
            } catch (DataAccessException ex) {
                updated = false;
            }
        }

        if (!updated) {
            if (enabled) {
                Movie movie = fallbackCatalog.get(safeMovieId);
                if (movie == null) {
                    throw new IllegalArgumentException("找不到電影：" + safeMovieId);
                }
                fallbackCatalog.put(safeMovieId, movie);
                updated = true;
            } else {
                updated = fallbackCatalog.remove(safeMovieId) != null;
            }
        }

        if (!updated) {
            throw new IllegalArgumentException("找不到電影：" + safeMovieId);
        }

        appendAuditLog(
                safeOperator,
                "MOVIE_CATALOG_TOGGLE",
                safeMovieId,
                "enabled=" + (enabled ? 1 : 0));
    }

    @Transactional
    public void saveShowtimeOverride(
            String movieId,
            String showtimeId,
            String startTime,
            int durationMinutes,
            String auditorium,
            String operator) {
        String safeMovieId = normalizeId(movieId, "movieId");
        String safeShowtimeId = normalizeId(showtimeId, "showtimeId");
        if (!effectiveCatalog().containsKey(safeMovieId)) {
            throw new IllegalArgumentException("找不到電影：" + safeMovieId);
        }
        String safeStartTime = normalizeStartTime(startTime);
        int safeDuration = Math.max(30, Math.min(400, durationMinutes));
        String safeAuditorium = normalizeAuditorium(auditorium);
        String safeOperator = operator == null || operator.isBlank() ? "unknown" : operator.trim();

        if (jdbcTemplate == null) {
            throw new IllegalStateException("資料庫尚未就緒，無法寫入場次。");
        }
        int updated = jdbcTemplate.update(
                "UPDATE showtime_overrides SET start_time = ?, duration_minutes = ?, auditorium = ?, enabled = 1, updated_by = ? " +
                        "WHERE movie_id = ? AND showtime_id = ?",
                safeStartTime, safeDuration, safeAuditorium, safeOperator, safeMovieId, safeShowtimeId);
        if (updated == 0) {
            jdbcTemplate.update(
                    "INSERT INTO showtime_overrides (movie_id, showtime_id, start_time, duration_minutes, auditorium, enabled, updated_by) " +
                            "VALUES (?, ?, ?, ?, ?, 1, ?)",
                    safeMovieId, safeShowtimeId, safeStartTime, safeDuration, safeAuditorium, safeOperator);
        }
    }

    private Map<String, Movie> effectiveCatalog() {
        Map<String, Movie> fromDb = loadCatalogFromDatabase();
        if (!fromDb.isEmpty()) {
            return fromDb;
        }
        return fallbackCatalog;
    }

    private Map<String, Movie> loadCatalogFromDatabase() {
        if (jdbcTemplate == null) {
            return Map.of();
        }
        try {
            List<Map<String, Object>> movieRows = jdbcTemplate.queryForList(
                    "SELECT movie_id, title, subtitle, poster_url, carousel_image_url, description, sort_order " +
                            "FROM movie_catalog WHERE enabled = 1 ORDER BY sort_order ASC, movie_id ASC");
            if (movieRows.isEmpty()) {
                return Map.of();
            }

            List<Map<String, Object>> showtimeRows = loadMovieShowtimeRows();

            Map<String, List<Showtime>> showtimesByMovie = new HashMap<>();
            for (Map<String, Object> row : showtimeRows) {
                String movieId = String.valueOf(row.get("movie_id"));
                String showtimeId = String.valueOf(row.get("showtime_id"));
                String startTime = String.valueOf(row.get("start_time"));
                int duration = ((Number) row.get("duration_minutes")).intValue();
                String auditorium = String.valueOf(row.get("auditorium"));
                showtimesByMovie.computeIfAbsent(movieId, ignored -> new ArrayList<>())
                        .add(new Showtime(showtimeId, startTime, duration, auditorium));
            }

            Map<String, Movie> mapped = new LinkedHashMap<>();
            for (Map<String, Object> row : movieRows) {
                String movieId = String.valueOf(row.get("movie_id"));
                List<Showtime> showtimes = showtimesByMovie.getOrDefault(movieId, List.of());
                String posterUrl = row.get("poster_url") == null ? "" : String.valueOf(row.get("poster_url"));
                String carouselImageUrl = row.get("carousel_image_url") == null
                        ? posterUrl
                        : String.valueOf(row.get("carousel_image_url"));
                mapped.put(movieId,
                        new Movie(
                                movieId,
                                String.valueOf(row.get("title")),
                                row.get("subtitle") == null ? "" : String.valueOf(row.get("subtitle")),
                                posterUrl,
                                carouselImageUrl,
                                row.get("description") == null ? "" : String.valueOf(row.get("description")),
                                showtimes));
            }
            return mapped;
        } catch (DataAccessException ex) {
            return Map.of();
        }
    }

    @Transactional
    public void disableShowtime(String movieId, String showtimeId, String operator) {
        String safeMovieId = normalizeId(movieId, "movieId");
        String safeShowtimeId = normalizeId(showtimeId, "showtimeId");
        String safeOperator = operator == null || operator.isBlank() ? "unknown" : operator.trim();
        if (jdbcTemplate == null) {
            throw new IllegalStateException("資料庫尚未就緒，無法更新場次。");
        }
        int updated = jdbcTemplate.update(
                "UPDATE showtime_overrides SET enabled = 0, updated_by = ? WHERE movie_id = ? AND showtime_id = ?",
                safeOperator, safeMovieId, safeShowtimeId);
        if (updated == 0) {
            jdbcTemplate.update(
                    "INSERT INTO showtime_overrides (movie_id, showtime_id, start_time, duration_minutes, auditorium, enabled, updated_by) " +
                            "VALUES (?, ?, '00:00', 120, '未設定', 0, ?)",
                    safeMovieId, safeShowtimeId, safeOperator);
        }
    }

    @Transactional
    public void updatePosterUrl(String movieId, String posterUrl, String operator) {
        String safeMovieId = normalizeId(movieId, "movieId");
        String safePosterUrl = normalizePosterUrl(posterUrl);
        String safeOperator = operator == null || operator.isBlank() ? "unknown" : operator.trim();

        boolean shouldFallback = jdbcTemplate == null;
        boolean updated = false;
        if (jdbcTemplate != null) {
            try {
                int count = jdbcTemplate.update(
                        "UPDATE movie_catalog SET poster_url = ? WHERE movie_id = ?",
                        safePosterUrl, safeMovieId);
                if (count <= 0) {
                    throw new IllegalArgumentException("找不到電影：" + safeMovieId);
                }
                updated = true;
            } catch (DataAccessException ex) {
                // Fall back to in-memory catalog for lightweight/local runs.
                shouldFallback = true;
            }
        }

        if (shouldFallback) {
            Movie current = fallbackCatalog.get(safeMovieId);
            if (current != null) {
                fallbackCatalog.put(
                        safeMovieId,
                        new Movie(
                                current.getId(),
                                current.getTitle(),
                                current.getSubtitle(),
                                safePosterUrl,
                                current.getCarouselImageUrl(),
                                current.getDescription(),
                                current.getShowtimes()));
                updated = true;
            }
        }

        if (!updated) {
            throw new IllegalArgumentException("找不到電影：" + safeMovieId);
        }

        if (jdbcTemplate != null) {
            try {
                jdbcTemplate.update(
                        "INSERT INTO audit_logs (actor_type, actor_id, action, target_type, target_id, result, detail) " +
                                "VALUES ('EMPLOYEE', ?, 'MOVIE_POSTER_UPDATE', 'MOVIE', ?, 'SUCCESS', ?)",
                        safeOperator,
                        safeMovieId,
                        "poster_url=" + safePosterUrl);
            } catch (DataAccessException ignored) {
                // Ignore when audit table is unavailable in lightweight schemas.
            }
        }
    }

    public SeatLayout getSeatLayout(String movieId, String showtimeId) {
        return getShowtimeDetails(movieId, showtimeId).getSeatLayout();
    }

    public ShowtimeDetails getShowtimeDetails(String movieId, String showtimeId) {
        Showtime showtime = getShowtime(movieId, showtimeId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Showtime not found for movie " + movieId + " and id " + showtimeId));

        List<SeatStatus> seats = generateSeats(movieId, showtimeId);
        SeatLayout layout = new SeatLayout(showtimeId, ROW_COUNT, COLUMN_COUNT, seats);
        return new ShowtimeDetails(showtime, layout);
    }

    public LocalDate currentOperationalDate() {
        LocalDate today = AppClock.today();
        LocalTime now = AppClock.nowLocalTime();
        return now.isBefore(OPERATIONAL_DAY_START) ? today.minusDays(1) : today;
    }

    // Resolve screening start instant for current operational day.
    // This keeps date calculation stable and avoids silently rolling a past showtime to tomorrow.
    public Instant resolveShowStartInstant(String movieId, String showtimeId) {
        Showtime showtime = getShowtime(movieId, showtimeId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Showtime not found for movie " + movieId + " and id " + showtimeId));

        LocalTime start = LocalTime.parse(showtime.getStartTime());
        LocalDate operationalDate = currentOperationalDate();
        LocalDate showDate = start.isBefore(OPERATIONAL_DAY_START) ? operationalDate.plusDays(1) : operationalDate;

        return ZonedDateTime.of(showDate, start, AppClock.zoneId()).toInstant();
    }

    public boolean isShowtimeOpenForPurchase(String movieId, String showtimeId) {
        try {
            if (!isBookingWindowOpenNow()) {
                return false;
            }
            return resolveShowStartInstant(movieId, showtimeId).isAfter(AppClock.nowInstant());
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    public boolean isBookingWindowOpenNow() {
        return isBookingWindowOpen(AppClock.nowLocalTime());
    }

    public BookingWindowStatus getBookingWindowStatus() {
        LocalTime now = AppClock.nowLocalTime();
        boolean open = isBookingWindowOpen(now);
        boolean warning = isBookingWarning(now);
        String message = "";
        if (warning) {
            message = "提醒：22:45 將強制停止訂票，請於 22:45 前完成付款。";
        } else if (!open) {
            if (now.isBefore(BOOKING_OPEN_TIME)) {
                message = "目前尚未開放訂票（每日 07:00 開放）。";
            } else {
                message = "今日訂票已於 22:45 截止，請明日 07:00 再次訂購。";
            }
        }
        return new BookingWindowStatus(open, warning, now, BOOKING_OPEN_TIME, BOOKING_CLOSE_TIME, message);
    }

    boolean isBookingWindowOpen(LocalTime now) {
        if (now == null) {
            return false;
        }
        return !now.isBefore(BOOKING_OPEN_TIME) && now.isBefore(BOOKING_CLOSE_TIME);
    }

    boolean isBookingWarning(LocalTime now) {
        if (now == null) {
            return false;
        }
        return !now.isBefore(BOOKING_WARNING_TIME) && now.isBefore(BOOKING_CLOSE_TIME);
    }

    private Movie enrichMovie(Movie movie) {
        List<Showtime> base = movie.getShowtimes();
        List<Showtime> upcoming = upcomingShowtimes(base);

        Set<String> bookableIds = upcoming.stream()
                .limit(2)
                .map(Showtime::getId)
                .collect(Collectors.toSet());

        List<Showtime> decorated = base.stream()
                .map(showtime -> showtime.withBookable(bookableIds.contains(showtime.getId())))
                .collect(Collectors.toList());

        return new Movie(movie.getId(), movie.getTitle(), movie.getSubtitle(),
                movie.getPosterUrl(), movie.getCarouselImageUrl(), movie.getDescription(), decorated);
    }

    private Movie enrichMovieForCustomer(Movie movie) {
        Movie enriched = enrichMovie(movie);
        List<Showtime> filtered = upcomingShowtimes(enriched.getShowtimes());
        return new Movie(
                enriched.getId(),
                enriched.getTitle(),
                enriched.getSubtitle(),
                enriched.getPosterUrl(),
                enriched.getCarouselImageUrl(),
                enriched.getDescription(),
                filtered);
    }

    private List<Showtime> upcomingShowtimes(List<Showtime> source) {
        if (!isBookingWindowOpenNow()) {
            return List.of();
        }

        LocalTime now = AppClock.nowLocalTime();
        LocalTime effectiveNow = now.isBefore(OPERATIONAL_DAY_START) ? now.plusHours(24) : now;

        List<Showtime> upcoming = new ArrayList<>();
        for (Showtime showtime : source) {
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
        return upcoming;
    }

    private Movie movieWithOverrides(Movie movie, List<ShowtimeOverrideRow> overrides) {
        if (movie == null) {
            return movie;
        }
        Map<String, Showtime> map = new LinkedHashMap<>();
        for (Showtime showtime : movie.getShowtimes()) {
            map.put(showtime.getId(), showtime);
        }

        if (overrides != null) {
            for (ShowtimeOverrideRow row : overrides) {
                String showtimeId = row.showtimeId();
                if (!row.enabled()) {
                    map.remove(showtimeId);
                    continue;
                }
                map.put(
                        showtimeId,
                        new Showtime(
                                showtimeId,
                                row.startTime(),
                                row.durationMinutes(),
                                row.auditorium()));
            }
        }

        List<Showtime> result = map.values().stream()
                .sorted(Comparator.comparing(s -> normalizeShowtimeTime(LocalTime.parse(s.getStartTime()))))
                .toList();
        return new Movie(
                movie.getId(),
                movie.getTitle(),
                movie.getSubtitle(),
                movie.getPosterUrl(),
                movie.getCarouselImageUrl(),
                movie.getDescription(),
                result);
    }

    private Map<String, List<ShowtimeOverrideRow>> loadOverridesByMovieIds(Collection<String> movieIds) {
        Map<String, List<ShowtimeOverrideRow>> overridesByMovie = new HashMap<>();
        if (jdbcTemplate == null || movieIds == null || movieIds.isEmpty()) {
            return overridesByMovie;
        }
        List<String> ids = movieIds.stream()
                .filter(Objects::nonNull)
                .filter(id -> !id.isBlank())
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return overridesByMovie;
        }

        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        String sql = "SELECT movie_id, showtime_id, start_time, duration_minutes, auditorium, enabled " +
                "FROM showtime_overrides WHERE movie_id IN (" + placeholders + ")";
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, ids.toArray());
            for (Map<String, Object> row : rows) {
                String movieId = String.valueOf(row.get("movie_id"));
                ShowtimeOverrideRow override = new ShowtimeOverrideRow(
                        String.valueOf(row.get("showtime_id")),
                        String.valueOf(row.get("start_time")),
                        ((Number) row.get("duration_minutes")).intValue(),
                        String.valueOf(row.get("auditorium")),
                        row.get("enabled") != null && ((Number) row.get("enabled")).intValue() != 0);
                overridesByMovie.computeIfAbsent(movieId, ignored -> new ArrayList<>()).add(override);
            }
        } catch (DataAccessException ex) {
            return Map.of();
        }
        return overridesByMovie;
    }

    private List<Map<String, Object>> loadMovieShowtimeRows() {
        return jdbcTemplate.queryForList(
                "SELECT movie_id, showtime_id, start_time, duration_minutes, auditorium, sort_order " +
                        "FROM movie_showtimes WHERE enabled = 1 ORDER BY movie_id ASC, sort_order ASC, showtime_id ASC");
    }

    private List<MovieAdminItem> loadMovieAdminItemsFromDatabase() {
        if (jdbcTemplate == null) {
            return List.of();
        }
        try {
            return jdbcTemplate.query(
                    "SELECT movie_id, title, subtitle, poster_url, carousel_image_url, description, enabled, sort_order " +
                            "FROM movie_catalog ORDER BY sort_order ASC, movie_id ASC",
                    (rs, rowNum) -> {
                        String posterUrl = rs.getString("poster_url");
                        String carouselImageUrl = rs.getString("carousel_image_url");
                        if (carouselImageUrl == null || carouselImageUrl.isBlank()) {
                            carouselImageUrl = posterUrl;
                        }
                        return new MovieAdminItem(
                                rs.getString("movie_id"),
                                rs.getString("title"),
                                rs.getString("subtitle"),
                                posterUrl,
                                carouselImageUrl,
                                rs.getString("description"),
                                rs.getInt("enabled") != 0,
                                rs.getInt("sort_order"));
                    });
        } catch (DataAccessException ex) {
            return List.of();
        }
    }

    private void appendAuditLog(String actorId, String action, String targetId, String detail) {
        if (jdbcTemplate == null) {
            return;
        }
        try {
            jdbcTemplate.update(
                    "INSERT INTO audit_logs (actor_type, actor_id, action, target_type, target_id, result, detail) " +
                            "VALUES ('EMPLOYEE', ?, ?, 'MOVIE', ?, 'SUCCESS', ?)",
                    actorId,
                    action,
                    targetId,
                    detail);
        } catch (DataAccessException ignored) {
            // Ignore when audit table is unavailable in lightweight schemas.
        }
    }

    private Movie createMovie(String id, String title, String subtitle, String posterUrl, String carouselImageUrl,
            String description, List<Showtime> showtimes) {
        return new Movie(id, title, subtitle, posterUrl, carouselImageUrl, description, showtimes);
    }

    private List<Showtime> showtimes(String movieId, int durationMinutes, String... startTimes) {
        List<Showtime> entries = new ArrayList<>();
        for (int i = 0; i < startTimes.length; i++) {
            entries.add(new Showtime(
                    movieId + "-st" + (i + 1),
                    startTimes[i],
                    durationMinutes,
                    (i % 3 + 1) + "號廳"));
        }
        return entries;
    }

    private List<SeatStatus> generateSeats(String movieId, String showtimeId) {
        Map<String, Boolean> reservedLookup = new HashMap<>();

        // Overlay real booked seats (persisted purchases).
        if (jdbcTemplate != null) {
            try {
                Instant showStartAt = resolveShowStartInstant(movieId, showtimeId);
                Instant holdThreshold = AppClock.nowInstant().minus(Math.max(1, pendingTimeoutMinutes), ChronoUnit.MINUTES);
                List<String> booked = jdbcTemplate.queryForList(
                        "SELECT mt.seat_id " +
                                "FROM member_tickets mt " +
                                "LEFT JOIN member_orders mo ON mo.id = mt.order_id " +
                                "WHERE mt.showtime_id = ? AND mt.show_start_at = ? " +
                                "AND (mt.order_id IS NULL OR mo.status = 'PAID' " +
                                "OR (mo.status IN ('PENDING', 'FAILED') AND mo.created_at >= ?))",
                        String.class,
                        showtimeId,
                        java.sql.Timestamp.from(showStartAt),
                        java.sql.Timestamp.from(holdThreshold));
                for (String seatId : booked) {
                    if (seatId != null && !seatId.isBlank()) {
                        reservedLookup.put(seatId.trim(), Boolean.TRUE);
                    }
                }
            } catch (Exception ignored) {
                // If DB isn't ready (e.g. during early bootstrap), fall back to simulated occupancy.
            }
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

    private static LocalTime normalizeShowtimeTime(LocalTime start) {
        return start.isBefore(OPERATIONAL_DAY_START) ? start.plusHours(24) : start;
    }

    private static String normalizeId(String value, String fieldName) {
        String safe = value == null ? "" : value.trim();
        if (safe.isBlank()) {
            throw new IllegalArgumentException(fieldName + " 不可為空。");
        }
        return safe;
    }

    private static String normalizeStartTime(String value) {
        String safe = value == null ? "" : value.trim();
        try {
            LocalTime.parse(safe);
        } catch (Exception ex) {
            throw new IllegalArgumentException("開演時間格式錯誤，請使用 HH:mm。");
        }
        return safe;
    }

    private static String normalizeAuditorium(String value) {
        String safe = value == null ? "" : value.trim();
        if (safe.isBlank()) {
            throw new IllegalArgumentException("影廳不可為空。");
        }
        return safe.length() > 100 ? safe.substring(0, 100) : safe;
    }

    private static String normalizeMovieTitle(String value) {
        String safe = value == null ? "" : value.trim();
        if (safe.isBlank()) {
            throw new IllegalArgumentException("電影名稱不可為空。");
        }
        return safe.length() > 120 ? safe.substring(0, 120) : safe;
    }

    private static String normalizeOptional(String value, int maxLength) {
        String safe = value == null ? "" : value.trim();
        return safe.length() > maxLength ? safe.substring(0, maxLength) : safe;
    }

    private static String normalizePosterUrl(String value) {
        String safe = value == null ? "" : value.trim();
        if (safe.isBlank()) {
            throw new IllegalArgumentException("海報網址不可為空。");
        }
        boolean allowExternal = safe.startsWith("https://");
        boolean allowLocal = safe.startsWith("/images/");
        if (!allowExternal && !allowLocal) {
            throw new IllegalArgumentException("海報網址格式錯誤，請使用 https:// 或 /images/ 開頭。");
        }
        return safe.length() > 500 ? safe.substring(0, 500) : safe;
    }

    public record BookingWindowStatus(
            boolean bookingOpen,
            boolean warning,
            LocalTime now,
            LocalTime openTime,
            LocalTime closeTime,
            String message) {
    }

    public record MovieCatalogItem(String id, String title) {
    }

    public record MovieAdminItem(
            String id,
            String title,
            String subtitle,
            String posterUrl,
            String carouselImageUrl,
            String description,
            boolean enabled,
            int sortOrder) {
    }

    private record ShowtimeOverrideRow(
            String showtimeId,
            String startTime,
            int durationMinutes,
            String auditorium,
            boolean enabled) {
    }
}

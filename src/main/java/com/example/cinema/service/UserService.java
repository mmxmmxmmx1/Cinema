package com.example.cinema.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.cinema.config.AppClock;
import com.example.cinema.dao.UserDao;
import com.example.cinema.exception.UserRegistrationException;
import com.example.cinema.model.User;
import com.example.cinema.model.User.UserType;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 用戶服務，處理用戶註冊、認證和個人資料管理
 */
@Service
@Tag(name = "User Service", description = "處理用戶註冊、認證和個人資料管理")
public class UserService {
    private static final Logger logger = LoggerFactory.getLogger(UserService.class);
    private static final Pattern ACCOUNT_PATTERN = Pattern.compile("^[A-Za-z0-9]+$");
    private static final int MIN_PASSWORD_LENGTH = 4;
    private static final int MAX_PASSWORD_LENGTH = 100;
    private static final int RESET_TOKEN_EXPIRE_MINUTES = 15;

    private final UserDao userDao;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;
    private final Map<String, PasswordResetToken> memberResetTokens = new ConcurrentHashMap<>();

    public UserService(UserDao userDao, PasswordEncoder passwordEncoder, JdbcTemplate jdbcTemplate) {
        this.userDao = userDao;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 註冊新用戶
     * 
     * @param username 用戶名
     * @param rawPassword 明文密碼
     * @param userType 用戶類型
     * @param asAdmin 是否為管理員
     * @return 用戶ID
     * @throws UserRegistrationException 當註冊失敗時拋出
     */
    @Operation(summary = "註冊新用戶", description = "註冊一個新的用戶帳號")
    @ApiResponse(responseCode = "200", description = "用戶註冊成功")
    @ApiResponse(responseCode = "400", description = "無效的輸入參數")
    @ApiResponse(responseCode = "409", description = "用戶名已存在")
    public Long registerUser(
            @Parameter(description = "用戶名", required = true) String username,
            @Parameter(description = "明文密碼", required = true) String rawPassword,
            @Parameter(description = "用戶類型") UserType userType,
            @Parameter(description = "是否為管理員") boolean asAdmin) {

        String safeUsername = normalizeUsername(username);
        validateAccountName(safeUsername);

        logger.info("開始註冊用戶: {}", safeUsername);
        try {
            UserType effectiveType = (userType == null) ? UserType.MEMBER : userType;
            String hash = passwordEncoder.encode(rawPassword);
            Long userId = userDao.createUser(safeUsername, hash, effectiveType);

            // This project models employee roles via employee.role_id -> roles.code.
            // Member logins use the MEMBER role implicitly (see UserDao.MemberMapper).
            if (effectiveType == UserType.EMPLOYEE && asAdmin) {
                userDao.assignRole(userId, "ADMIN");
                logger.info("已為用戶 {} 分配管理員權限", safeUsername);
            } else if (effectiveType == UserType.MEMBER && asAdmin) {
                logger.warn("忽略 member 註冊的 asAdmin=true：管理/IT/主管身分需建立員工帳號。");
            }
            
            logger.info("用戶註冊成功，用戶ID: {}", userId);
            return userId;
            
        } catch (DuplicateKeyException e) {
            String errorMsg = String.format("用戶名 %s 已存在", safeUsername);
            logger.warn(errorMsg);
            throw new UserRegistrationException(errorMsg, e);
        } catch (Exception e) {
            String errorMsg = String.format("註冊用戶 %s 時發生錯誤", safeUsername);
            logger.error(errorMsg, e);
            throw new UserRegistrationException(errorMsg, e);
        }
    }

    /**
     * 註冊新員工
     * 
     * @param username 員工用戶名
     * @param rawPassword 明文密碼
     * @return 員工用戶ID
     * @throws UserRegistrationException 當註冊失敗時拋出
     */
    @Operation(summary = "註冊新員工", description = "註冊一個新的員工帳號")
    @ApiResponse(responseCode = "200", description = "員工註冊成功")
    @ApiResponse(responseCode = "400", description = "無效的輸入參數")
    @ApiResponse(responseCode = "409", description = "用戶名已存在")
    public Long registerEmployee(
            @Parameter(description = "員工用戶名", required = true) String username,
            @Parameter(description = "明文密碼", required = true) String rawPassword) {

        String safeUsername = normalizeUsername(username);
        validateAccountName(safeUsername);

        logger.info("開始註冊員工: {}", safeUsername);
        try {
            String hash = passwordEncoder.encode(rawPassword);
            Long userId = userDao.createUser(safeUsername, hash, UserType.EMPLOYEE);
            
            logger.info("員工註冊成功，用戶ID: {}", userId);
            return userId;
            
        } catch (DuplicateKeyException e) {
            String errorMsg = String.format("員工用戶名 %s 已存在", safeUsername);
            logger.warn(errorMsg);
            throw new UserRegistrationException(errorMsg, e);
        } catch (Exception e) {
            String errorMsg = String.format("註冊員工 %s 時發生錯誤", safeUsername);
            logger.error(errorMsg, e);
            throw new UserRegistrationException(errorMsg, e);
        }
    }

    public String issueMemberPasswordResetToken(String username) {
        String safeUsername = normalizeUsername(username);
        validateAccountName(safeUsername);
        cleanupExpiredResetTokens();

        User member = userDao.findMemberByUsername(safeUsername).orElse(null);
        if (member == null) {
            // Do not reveal whether account exists to avoid user enumeration.
            return null;
        }

        memberResetTokens.entrySet()
                .removeIf(entry -> safeUsername.equalsIgnoreCase(entry.getValue().username()));

        String token = generateResetToken();
        memberResetTokens.put(token, new PasswordResetToken(
                safeUsername,
                AppClock.nowInstant().plus(RESET_TOKEN_EXPIRE_MINUTES, ChronoUnit.MINUTES)));
        return token;
    }

    @Transactional
    public void resetMemberPasswordWithToken(String username, String token, String newPassword) {
        String safeUsername = normalizeUsername(username);
        validateAccountName(safeUsername);
        validatePassword(newPassword);

        String safeToken = normalizeResetToken(token);
        if (safeToken.isBlank()) {
            throw new UserRegistrationException("重設碼不可為空");
        }

        cleanupExpiredResetTokens();
        PasswordResetToken resetToken = memberResetTokens.get(safeToken);
        Instant now = AppClock.nowInstant();
        if (resetToken == null || resetToken.expiresAt().isBefore(now)
                || !safeUsername.equalsIgnoreCase(resetToken.username())) {
            throw new UserRegistrationException("重設碼無效或已過期");
        }

        updateMemberPassword(safeUsername, newPassword);
        memberResetTokens.remove(safeToken);
    }

    @Transactional
    public void changeMemberPassword(String username, String currentPassword, String newPassword) {
        String safeUsername = normalizeUsername(username);
        validateAccountName(safeUsername);
        validatePassword(newPassword);

        if (currentPassword == null || currentPassword.isBlank()) {
            throw new UserRegistrationException("目前密碼不可為空");
        }

        User member = userDao.findMemberByUsername(safeUsername)
                .orElseThrow(() -> new UserRegistrationException("找不到會員帳號"));

        if (!passwordEncoder.matches(currentPassword, member.getPassword())) {
            throw new UserRegistrationException("目前密碼不正確");
        }
        if (passwordEncoder.matches(newPassword, member.getPassword())) {
            throw new UserRegistrationException("新密碼不可與目前密碼相同");
        }

        updateMemberPassword(safeUsername, newPassword);
        memberResetTokens.entrySet()
                .removeIf(entry -> safeUsername.equalsIgnoreCase(entry.getValue().username()));
    }

    private static String normalizeUsername(String username) {
        return username == null ? "" : username.trim();
    }

    private static void validateAccountName(String username) {
        if (username.isBlank()) {
            throw new UserRegistrationException("用戶名不可為空");
        }
        if (username.length() > 100) {
            throw new UserRegistrationException("用戶名長度不可超過 100");
        }
        if (!ACCOUNT_PATTERN.matcher(username).matches()) {
            throw new UserRegistrationException("用戶名只能使用英文與數字");
        }
    }

    private static void validatePassword(String password) {
        if (password == null || password.isBlank()) {
            throw new UserRegistrationException("密碼不可為空");
        }
        String safe = password.trim();
        if (safe.length() < MIN_PASSWORD_LENGTH) {
            throw new UserRegistrationException("密碼長度至少 " + MIN_PASSWORD_LENGTH + " 碼");
        }
        if (safe.length() > MAX_PASSWORD_LENGTH) {
            throw new UserRegistrationException("密碼長度不可超過 " + MAX_PASSWORD_LENGTH + " 碼");
        }
    }

    private void updateMemberPassword(String username, String newPassword) {
        String hash = passwordEncoder.encode(newPassword);
        int updated = jdbcTemplate.update(
                "UPDATE members SET password = ? WHERE nickname = ?",
                hash,
                username);
        if (updated <= 0) {
            throw new UserRegistrationException("密碼更新失敗，找不到會員帳號");
        }
    }

    private static String generateResetToken() {
        return UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 10)
                .toUpperCase(Locale.ROOT);
    }

    private static String normalizeResetToken(String token) {
        return token == null ? "" : token.trim().toUpperCase(Locale.ROOT);
    }

    private void cleanupExpiredResetTokens() {
        Instant now = AppClock.nowInstant();
        memberResetTokens.entrySet()
                .removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    /**
     * 合併訪客的觀看清單到用戶帳號
     * 
     * @param username 目標用戶名
     * @param movieIds 要合併的電影ID集合
     */
    @Transactional
    @Operation(summary = "合併觀看清單", description = "將訪客的觀看清單合併到登入用戶的帳號")
    @ApiResponse(responseCode = "200", description = "清單合併成功")
    @ApiResponse(responseCode = "400", description = "無效的輸入參數")
    public void mergeGuestWatchlistIntoUser(
            @Parameter(description = "目標用戶名", required = true) String username,
            @Parameter(description = "要合併的電影ID集合") Collection<Long> movieIds) {
        
        logger.debug("開始合併觀看清單，用戶: {}", username);
        
        if (movieIds == null || movieIds.isEmpty()) {
            logger.debug("沒有需要合併的電影ID");
            return;
        }
        
        // Watchlists are stored against members.id, so resolve the member record explicitly.
        User user = userDao.findMemberByUsername(username).orElse(null);
        if (user == null) {
            logger.warn("找不到用戶: {}", username);
            return;
        }

        // 去重並過濾空值
        Set<Long> deduplicated = movieIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
                
        if (deduplicated.isEmpty()) {
            logger.debug("過濾後沒有有效的電影ID");
            return;
        }

        // 獲取用戶現有的觀看清單
        Set<Long> existing = new HashSet<>();
        try {
            existing = new HashSet<>(jdbcTemplate.queryForList(
                    "SELECT movie_id FROM user_watchlist WHERE user_id = ?",
                    Long.class,
                    user.getId()));
            logger.debug("用戶 {} 現有 {} 部電影在觀看清單中", username, existing.size());
        } catch (DataAccessException ex) {
            logger.error("獲取用戶 {} 的觀看清單時出錯", username, ex);
            return;
        }

        // 合併清單
        int addedCount = 0;
        for (Long movieId : deduplicated) {
            if (!existing.contains(movieId)) {
                try {
                    jdbcTemplate.update(
                            "INSERT INTO user_watchlist (user_id, movie_id) VALUES (?, ?)",
                            user.getId(),
                            movieId);
                    addedCount++;
                } catch (DataAccessException e) {
                    logger.warn("無法將電影ID {} 添加到用戶 {} 的觀看清單", movieId, username, e);
                }
            }
        }
        
        logger.info("成功為用戶 {} 添加了 {} 部新電影到觀看清單", username, addedCount);
    
    }

    private record PasswordResetToken(
            String username,
            Instant expiresAt) {
    }
}

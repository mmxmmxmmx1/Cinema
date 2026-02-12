package com.example.cinema.service;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private final UserDao userDao;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

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
        
        logger.info("開始註冊用戶: {}", username);
        try {
            UserType effectiveType = (userType == null) ? UserType.MEMBER : userType;
            String hash = passwordEncoder.encode(rawPassword);
            Long userId = userDao.createUser(username, hash, effectiveType);

            // This project models employee roles via employee.role_id -> roles.code.
            // Member logins use the MEMBER role implicitly (see UserDao.MemberMapper).
            if (effectiveType == UserType.EMPLOYEE && asAdmin) {
                userDao.assignRole(userId, "ADMIN");
                logger.info("已為用戶 {} 分配管理員權限", username);
            } else if (effectiveType == UserType.MEMBER && asAdmin) {
                logger.warn("忽略 member 註冊的 asAdmin=true：管理/IT/主管身分需建立員工帳號。");
            }
            
            logger.info("用戶註冊成功，用戶ID: {}", userId);
            return userId;
            
        } catch (DuplicateKeyException e) {
            String errorMsg = String.format("用戶名 %s 已存在", username);
            logger.warn(errorMsg);
            throw new UserRegistrationException(errorMsg, e);
        } catch (Exception e) {
            String errorMsg = String.format("註冊用戶 %s 時發生錯誤", username);
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
        
        logger.info("開始註冊員工: {}", username);
        try {
            String hash = passwordEncoder.encode(rawPassword);
            Long userId = userDao.createUser(username, hash, UserType.EMPLOYEE);
            
            logger.info("員工註冊成功，用戶ID: {}", userId);
            return userId;
            
        } catch (DuplicateKeyException e) {
            String errorMsg = String.format("員工用戶名 %s 已存在", username);
            logger.warn(errorMsg);
            throw new UserRegistrationException(errorMsg, e);
        } catch (Exception e) {
            String errorMsg = String.format("註冊員工 %s 時發生錯誤", username);
            logger.error(errorMsg, e);
            throw new UserRegistrationException(errorMsg, e);
        }
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
}

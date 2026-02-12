package com.example.cinema.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.cinema.dao.UserDao;
import com.example.cinema.exception.UserRegistrationException;
import com.example.cinema.model.Role;
import com.example.cinema.model.User;
import com.example.cinema.model.User.UserType;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserDao userDao;
    
    @Mock
    private PasswordEncoder passwordEncoder;
    
    @Mock
    private JdbcTemplate jdbcTemplate;
    
    @InjectMocks
    private UserService userService;
    
    private static final String TEST_USERNAME = "testuser";
    private static final String TEST_PASSWORD = "password123";
    private static final String ENCODED_PASSWORD = "encodedPassword123";
    private static final Long USER_ID = 1L;
    
    private User testUser;
    private Role userRole;
    
    @BeforeEach
    void setUp() {
        // 初始化角色
        userRole = new Role(1L, "ROLE_USER", "一般使用者", "基本用戶角色", 10);
        
        // 初始化測試用戶
        testUser = new User(
            USER_ID,
            TEST_USERNAME,
            "Test",
            "User",
            "test@example.com",
            "1234567890",
            ENCODED_PASSWORD,
            UserType.MEMBER,
            LocalDateTime.now(),
            Arrays.asList(userRole)
        );
        
        // 模擬密碼編碼器（使用 lenient 避免不必要的 stubbing 警告）
        lenient().when(passwordEncoder.encode(anyString())).thenReturn(ENCODED_PASSWORD);
    }
    
    @Test
    void registerUser_Success() {
        // 模擬 UserDao 行為
        when(userDao.createUser(anyString(), anyString(), any(UserType.class))).thenReturn(USER_ID);
        
        // 執行測試
        Long userId = userService.registerUser(TEST_USERNAME, TEST_PASSWORD, UserType.MEMBER, false);
        
        // 驗證結果
        assertNotNull(userId);
        assertEquals(USER_ID, userId);
        
        // 驗證方法調用
        verify(userDao).createUser(eq(TEST_USERNAME), eq(ENCODED_PASSWORD), eq(UserType.MEMBER));
        verify(userDao, never()).assignRole(anyLong(), anyString());
    }
    
    @Test
    void registerUser_WithAdminRole_Success() {
        // 模擬 UserDao 行為
        when(userDao.createUser(anyString(), anyString(), any(UserType.class))).thenReturn(USER_ID);
        
        // 執行測試
        Long userId = userService.registerUser(TEST_USERNAME, TEST_PASSWORD, UserType.MEMBER, true);
        
        // 驗證結果
        assertNotNull(userId);
        
        // member 註冊不會透過 roles/employee.role_id 指派管理權限
        verify(userDao, never()).assignRole(anyLong(), anyString());
    }
    
    @Test
    void registerUser_DuplicateUsername_ThrowsException() {
        // 模擬重複用戶名異常
        when(userDao.createUser(anyString(), anyString(), any(UserType.class)))
            .thenThrow(new DuplicateKeyException("Duplicate username"));
        
        // 驗證是否拋出預期異常
        UserRegistrationException exception = assertThrows(
            UserRegistrationException.class,
            () -> userService.registerUser(TEST_USERNAME, TEST_PASSWORD, UserType.MEMBER, false)
        );
        
        assertTrue(exception.getMessage().contains("已存在"));
    }
    
    @Test
    void mergeGuestWatchlistIntoUser_WithNewMovies_Success() {
        // 模擬用戶查詢
        when(userDao.findMemberByUsername(TEST_USERNAME)).thenReturn(Optional.of(testUser));
        
        // 模擬現有觀看清單（空列表）
        when(jdbcTemplate.queryForList(
            eq("SELECT movie_id FROM user_watchlist WHERE user_id = ?"),
            eq(Long.class),
            eq(USER_ID))).thenReturn(Collections.emptyList());
        
        // 執行測試
        Set<Long> movieIds = new HashSet<>(Arrays.asList(1L, 2L, 3L));
        userService.mergeGuestWatchlistIntoUser(TEST_USERNAME, movieIds);
        
        // 驗證插入操作被調用3次
        verify(jdbcTemplate, times(3)).update(
            eq("INSERT INTO user_watchlist (user_id, movie_id) VALUES (?, ?)"),
            eq(USER_ID),
            anyLong()
        );
    }
    
    @Test
    void mergeGuestWatchlistIntoUser_WithExistingMovies_NoDuplicates() {
        // 模擬用戶查詢
        when(userDao.findMemberByUsername(TEST_USERNAME)).thenReturn(Optional.of(testUser));
        
        // 模擬現有觀看清單（已包含電影ID 1和2）
        when(jdbcTemplate.queryForList(
            eq("SELECT movie_id FROM user_watchlist WHERE user_id = ?"),
            eq(Long.class),
            eq(USER_ID))).thenReturn(Arrays.asList(1L, 2L));
        
        // 執行測試（包含重複的電影ID）
        Set<Long> movieIds = new HashSet<>(Arrays.asList(1L, 2L, 3L));
        userService.mergeGuestWatchlistIntoUser(TEST_USERNAME, movieIds);
        
        // 驗證只插入了新的電影ID（3）
        verify(jdbcTemplate, times(1)).update(
            eq("INSERT INTO user_watchlist (user_id, movie_id) VALUES (?, ?)"),
            eq(USER_ID),
            eq(3L)
        );
    }
    
    // ensureBasicRole 已移除：此專案的 member 角色為隱含 MEMBER；employee 角色由 employee.role_id -> roles.code 決定。
}

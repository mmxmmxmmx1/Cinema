package com.example.cinema.service;

import com.example.cinema.service.SessionService.Realm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.mock.web.MockHttpSession;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SessionService 測試單元
 * 測試會話服務的功能
 */
@DisplayName("會話服務測試")
class SessionServiceTest {

    private SessionService sessionService;
    private MockHttpSession session;

    @BeforeEach
    void setUp() {
        sessionService = new SessionService();
        session = new MockHttpSession();
    }

    @Test
    @DisplayName("應該返回訪客觀看清單鍵")
    void shouldReturnGuestWatchlistKey() {
        // When
        String key = sessionService.guestWatchlistKey();

        // Then
        assertNotNull(key, "鍵不應為 null");
        assertEquals("guestWatchlist", key, "鍵應該是 'guestWatchlist'");
    }

    @Test
    @DisplayName("未認證的會話應該返回 false")
    void shouldReturnFalseForUnauthenticatedSession() {
        // When
        boolean isAuthenticated = sessionService.isAuthenticated(session, Realm.MEMBER);

        // Then
        assertFalse(isAuthenticated, "未認證的會話應該返回 false");
    }

    @Test
    @DisplayName("已認證的會話應該返回 true")
    void shouldReturnTrueForAuthenticatedSession() {
        // Given
        sessionService.establishSession(session, Realm.MEMBER);

        // When
        boolean isAuthenticated = sessionService.isAuthenticated(session, Realm.MEMBER);

        // Then
        assertTrue(isAuthenticated, "已認證的會話應該返回 true");
    }

    @Test
    @DisplayName("不同領域的認證應該獨立")
    void shouldHaveIndependentAuthenticationForDifferentRealms() {
        // Given
        sessionService.establishSession(session, Realm.MEMBER);

        // When
        boolean memberAuth = sessionService.isAuthenticated(session, Realm.MEMBER);
        boolean employeeAuth = sessionService.isAuthenticated(session, Realm.EMPLOYEE);

        // Then
        assertTrue(memberAuth, "會員應該已認證");
        assertFalse(employeeAuth, "員工不應該已認證");
    }

    @Test
    @DisplayName("應該能夠建立會話")
    void shouldEstablishSession() {
        // When
        sessionService.establishSession(session, Realm.MEMBER);

        // Then
        assertTrue(sessionService.isAuthenticated(session, Realm.MEMBER), 
                "會話應該已建立");
    }

    @Test
    @DisplayName("應該能夠清除認證")
    void shouldClearAuthentication() {
        // Given
        sessionService.establishSession(session, Realm.MEMBER);

        // When
        sessionService.clearAuthentication(session, Realm.MEMBER);

        // Then
        assertFalse(sessionService.isAuthenticated(session, Realm.MEMBER), 
                "認證應該被清除");
    }

    @Test
    @DisplayName("應該能夠重置嘗試次數")
    void shouldResetAttempts() {
        // When
        sessionService.resetAttempts(session, Realm.MEMBER);

        // Then - 不應拋出異常
        assertDoesNotThrow(() -> sessionService.resetAttempts(session, Realm.MEMBER));
    }

    @Test
    @DisplayName("未鎖定的會話應該返回零剩餘鎖定時間")
    void shouldReturnZeroDurationForUnlockedSession() {
        // When
        Duration remaining = sessionService.remainingLockDuration(session, Realm.MEMBER);

        // Then
        assertEquals(Duration.ZERO, remaining, "未鎖定的會話應該返回零時間");
    }

    @Test
    @DisplayName("應該能夠儲存和消費錯誤訊息")
    void shouldStoreAndConsumeErrorMessage() {
        // Given
        String errorMessage = "測試錯誤訊息";

        // When
        sessionService.storeErrorMessage(session, Realm.MEMBER, errorMessage);
        String consumed = sessionService.consumeErrorMessage(session, Realm.MEMBER);

        // Then
        assertEquals(errorMessage, consumed, "應該返回儲存的錯誤訊息");
        
        // 再次消費應該返回 null
        String secondConsume = sessionService.consumeErrorMessage(session, Realm.MEMBER);
        assertNull(secondConsume, "錯誤訊息應該只能消費一次");
    }

    @Test
    @DisplayName("清除一個領域的認證不應影響其他領域")
    void shouldNotAffectOtherRealmsWhenClearingAuthentication() {
        // Given
        sessionService.establishSession(session, Realm.MEMBER);
        sessionService.establishSession(session, Realm.EMPLOYEE);

        // When
        sessionService.clearAuthentication(session, Realm.MEMBER);

        // Then
        assertFalse(sessionService.isAuthenticated(session, Realm.MEMBER), 
                "會員認證應該被清除");
        assertTrue(sessionService.isAuthenticated(session, Realm.EMPLOYEE), 
                "員工認證不應該被影響");
    }
}

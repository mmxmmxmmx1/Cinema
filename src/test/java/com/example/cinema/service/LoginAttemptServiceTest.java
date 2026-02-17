package com.example.cinema.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.cinema.config.AppClock;
import com.example.cinema.service.LoginAttemptService.LoginAttemptStatus;
import com.example.cinema.service.SessionService.Realm;

@DisplayName("登入嘗試鎖定機制測試")
class LoginAttemptServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Taipei");

    private final LoginAttemptService service = new LoginAttemptService();

    @AfterEach
    void resetClock() throws Exception {
        Field field = AppClock.class.getDeclaredField("clock");
        field.setAccessible(true);
        field.set(null, Clock.system(ZONE));
    }

    @Test
    @DisplayName("同帳號連續失敗 5 次後應鎖定 10 分鐘")
    void shouldLockAfterFiveFailures() throws Exception {
        setClock("2026-02-17T06:00:00Z");
        for (int i = 1; i <= 4; i++) {
            LoginAttemptStatus status = service.registerFailure(Realm.MEMBER, "test123");
            assertFalse(status.locked());
            assertEquals(LoginAttemptService.MAX_ATTEMPTS - i, status.remainingAttempts());
        }

        LoginAttemptStatus locked = service.registerFailure(Realm.MEMBER, "test123");
        assertTrue(locked.locked());
        assertEquals(0, locked.remainingAttempts());
        assertTrue(locked.lockDuration().toMinutes() <= 10 && locked.lockDuration().toMinutes() >= 9);
    }

    @Test
    @DisplayName("登入成功後應清除失敗紀錄")
    void shouldResetAttemptsOnSuccess() throws Exception {
        setClock("2026-02-17T06:00:00Z");
        service.registerFailure(Realm.MEMBER, "test123");
        service.registerFailure(Realm.MEMBER, "test123");

        service.registerSuccess(Realm.MEMBER, "test123");
        LoginAttemptStatus status = service.getStatus(Realm.MEMBER, "test123");
        assertFalse(status.locked());
        assertEquals(LoginAttemptService.MAX_ATTEMPTS, status.remainingAttempts());
    }

    @Test
    @DisplayName("同 realm 下帳號大小寫應視為同一個鍵")
    void shouldTreatUsernameCaseInsensitiveInSameRealm() throws Exception {
        setClock("2026-02-17T06:00:00Z");
        service.registerFailure(Realm.MEMBER, "Test123");

        LoginAttemptStatus status = service.getStatus(Realm.MEMBER, "test123");
        assertFalse(status.locked());
        assertEquals(LoginAttemptService.MAX_ATTEMPTS - 1, status.remainingAttempts());
    }

    @Test
    @DisplayName("不同 realm 之間嘗試次數應互不影響")
    void shouldIsolateAttemptsAcrossRealms() throws Exception {
        setClock("2026-02-17T06:00:00Z");
        service.registerFailure(Realm.MEMBER, "test123");

        LoginAttemptStatus employeeStatus = service.getStatus(Realm.EMPLOYEE, "test123");
        assertFalse(employeeStatus.locked());
        assertEquals(LoginAttemptService.MAX_ATTEMPTS, employeeStatus.remainingAttempts());
    }

    @Test
    @DisplayName("鎖定過期後應自動恢復可登入")
    void shouldUnlockAfterLockDuration() throws Exception {
        setClock("2026-02-17T06:00:00Z");
        for (int i = 0; i < LoginAttemptService.MAX_ATTEMPTS; i++) {
            service.registerFailure(Realm.MEMBER, "test123");
        }
        assertTrue(service.getStatus(Realm.MEMBER, "test123").locked());

        setClock("2026-02-17T06:11:00Z");
        LoginAttemptStatus status = service.getStatus(Realm.MEMBER, "test123");
        assertFalse(status.locked());
        assertEquals(LoginAttemptService.MAX_ATTEMPTS, status.remainingAttempts());
    }

    @Test
    @DisplayName("清理器應移除過久未活動紀錄")
    void cleanupShouldRemoveStaleEntry() throws Exception {
        setClock("2026-02-17T06:00:00Z");
        service.registerFailure(Realm.MEMBER, "stale-user");
        assertEquals(LoginAttemptService.MAX_ATTEMPTS - 1,
                service.getStatus(Realm.MEMBER, "stale-user").remainingAttempts());

        setClock("2026-02-17T08:10:00Z");
        service.cleanup();
        LoginAttemptStatus status = service.getStatus(Realm.MEMBER, "stale-user");
        assertEquals(LoginAttemptService.MAX_ATTEMPTS, status.remainingAttempts());
        assertFalse(status.locked());
    }

    private static void setClock(String isoInstant) throws Exception {
        Field field = AppClock.class.getDeclaredField("clock");
        field.setAccessible(true);
        field.set(null, Clock.fixed(Instant.parse(isoInstant), ZONE));
    }
}

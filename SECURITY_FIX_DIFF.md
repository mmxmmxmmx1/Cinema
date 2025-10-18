# 🔒 SecurityConfig 修改對比 - 登入鎖定邏輯修正

## 📋 修改目的
**修正問題：** 被鎖定的帳號輸入正確密碼仍會進行密碼驗證，只是最後被重定向回登入頁。

**修正後：** 在驗證密碼之前就檢查鎖定狀態，被鎖定的帳號不會進行密碼驗證。

---

## 🔴 修改前的流程（有漏洞）

```
用戶輸入帳密
  ↓
Spring Security 載入用戶資料
  ↓
驗證密碼（會查詢資料庫比對！）
  ↓
密碼正確 → 進入 successHandler
  ↓
檢查是否被鎖定 ⚠️ 發現被鎖定
  ↓
清除認證 + 重定向到登入頁
```

**問題：即使帳號被鎖定，系統仍會驗證密碼！**

---

## ✅ 修改後的流程（正確）

```
用戶輸入帳密
  ↓
userDetailsService() 載入用戶前先檢查鎖定
  ↓
如果被鎖定 → 直接拋出 LockedException ❌ 不驗證密碼！
  ↓
如果沒被鎖定 → 才載入用戶資料並驗證密碼
  ↓
successHandler 只檢查 IP 鎖定
```

**修正：被鎖定的帳號不會進行密碼驗證！**

---

## 📝 詳細修改內容

### 1️⃣ 新增 import

```diff
+ import org.springframework.security.authentication.LockedException;
  import org.springframework.security.core.userdetails.UserDetailsService;
  import org.springframework.security.core.userdetails.UsernameNotFoundException;
```

---

### 2️⃣ 修改 userDetailsService() 方法

#### ❌ **修改前**
```java
@Bean
public UserDetailsService userDetailsService() {
    return username -> {
        User user = userDao.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with username: " + username));
        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                user.getRoles().stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getCode()))
                        .collect(Collectors.toList()));
    };
}
```

**問題：直接載入用戶，沒有檢查鎖定狀態！**

---

#### ✅ **修改後**
```java
@Bean
public UserDetailsService userDetailsService() {
    return username -> {
        // ⚠️ 重要：在驗證密碼之前先檢查帳號是否被鎖定
        // 檢查會員鎖定狀態
        var memberStatus = loginAttemptService.getStatus(SessionService.Realm.MEMBER, username);
        if (memberStatus.locked()) {
            long minutes = Math.max(1, (memberStatus.lockDuration().getSeconds() + 59) / 60);
            throw new LockedException("帳號已被鎖定 " + minutes + " 分鐘，請稍後再試。");
        }
        
        // 檢查員工鎖定狀態
        var employeeStatus = loginAttemptService.getStatus(SessionService.Realm.EMPLOYEE, username);
        if (employeeStatus.locked()) {
            long minutes = Math.max(1, (employeeStatus.lockDuration().getSeconds() + 59) / 60);
            throw new LockedException("帳號已被鎖定 " + minutes + " 分鐘，請稍後再試。");
        }
        
        // 只有在帳號未被鎖定時才載入用戶資料並驗證密碼
        User user = userDao.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with username: " + username));
        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                user.getRoles().stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getCode()))
                        .collect(Collectors.toList()));
    };
}
```

**修正：先檢查鎖定，被鎖定則直接拋出異常，不進行密碼驗證！**

---

### 3️⃣ 簡化 Employee successHandler

#### ❌ **修改前**
```java
.successHandler((request, response, authentication) -> {
    String username = authentication.getName();
    String clientIp = getClientIp(request);

    // 檢查帳號是否被鎖定
    var userStatus = loginAttemptService.getStatus(SessionService.Realm.EMPLOYEE, username);
    if (userStatus != null && userStatus.locked()) {
        // 被鎖定時，保留 session 並設定鎖定狀態
        HttpSession session = request.getSession(true);
        sessionService.applyFailureFeedback(session, SessionService.Realm.EMPLOYEE, userStatus);
        
        // 清除 Spring Security 的認證，但保留 session
        SecurityContextHolder.clearContext();
        
        // 導回登入頁
        response.sendRedirect("/employee/login?error");
        return;
    }

    // 檢查 IP 是否被鎖定
    var ipStatus = loginAttemptService.getStatus(SessionService.Realm.EMPLOYEE, clientIp);
    ...
})
```

**問題：這段帳號鎖定檢查永遠不會執行（因為鎖定的帳號已經無法通過認證）！**

---

#### ✅ **修改後**
```java
.successHandler((request, response, authentication) -> {
    String username = authentication.getName();
    String clientIp = getClientIp(request);

    // ✅ 帳號鎖定檢查已移至 userDetailsService()，這裡只檢查 IP 鎖定
    // 檢查 IP 是否被鎖定
    var ipStatus = loginAttemptService.getStatus(SessionService.Realm.EMPLOYEE, clientIp);
    if (ipStatus != null && ipStatus.locked()) {
        HttpSession session = request.getSession(true);
        sessionService.applyFailureFeedback(session, SessionService.Realm.EMPLOYEE, ipStatus);
        SecurityContextHolder.clearContext();
        response.sendRedirect("/employee/login?error");
        return;
    }
    ...
})
```

**修正：移除無用的帳號鎖定檢查，只保留 IP 鎖定檢查！**

---

### 4️⃣ 簡化 Member successHandler

#### ❌ **修改前**
```java
.successHandler((request, response, authentication) -> {
    String username = authentication.getName();
    String clientIp = getClientIp(request);

    // 檢查帳號是否被鎖定
    var userStatus = loginAttemptService.getStatus(SessionService.Realm.MEMBER, username);
    if (userStatus != null && userStatus.locked()) {
        // 被鎖定時，保留 session 並設定鎖定狀態
        HttpSession session = request.getSession(true);
        sessionService.applyFailureFeedback(session, SessionService.Realm.MEMBER, userStatus);
        
        // 清除 Spring Security 的認證，但保留 session
        SecurityContextHolder.clearContext();
        
        // 導回登入頁
        response.sendRedirect("/member/login?error");
        return;
    }

    // 檢查 IP 是否被鎖定
    var ipStatus = loginAttemptService.getStatus(SessionService.Realm.MEMBER, clientIp);
    ...
})
```

**問題：同樣的問題，這段帳號鎖定檢查永遠不會執行！**

---

#### ✅ **修改後**
```java
.successHandler((request, response, authentication) -> {
    String username = authentication.getName();
    String clientIp = getClientIp(request);

    // ✅ 帳號鎖定檢查已移至 userDetailsService()，這裡只檢查 IP 鎖定
    // 檢查 IP 是否被鎖定
    var ipStatus = loginAttemptService.getStatus(SessionService.Realm.MEMBER, clientIp);
    if (ipStatus != null && ipStatus.locked()) {
        HttpSession session = request.getSession(true);
        sessionService.applyFailureFeedback(session, SessionService.Realm.MEMBER, ipStatus);
        SecurityContextHolder.clearContext();
        response.sendRedirect("/member/login?error");
        return;
    }
    ...
})
```

**修正：移除無用的帳號鎖定檢查，只保留 IP 鎖定檢查！**

---

## 📊 修改統計

| 項目 | 修改前 | 修改後 |
|------|--------|--------|
| 新增 import | 0 | 1 個 (LockedException) |
| userDetailsService() | 9 行 | 27 行 |
| Employee successHandler | 含無用的帳號檢查 | 移除無用檢查 |
| Member successHandler | 含無用的帳號檢查 | 移除無用檢查 |
| **總行數變化** | - | +18 行 |

---

## ✅ 修改效果

### 測試情境對比

| 情境 | 修改前 | 修改後 |
|------|--------|--------|
| 鎖定期間輸入錯誤密碼 | ❌ 驗證密碼 → 失敗 | ✅ 不驗證密碼 → 直接拒絕 |
| 鎖定期間輸入正確密碼 | ⚠️ 驗證密碼 → 成功 → 被踢回 | ✅ 不驗證密碼 → 直接拒絕 |
| 未鎖定輸入正確密碼 | ✅ 驗證密碼 → 成功登入 | ✅ 驗證密碼 → 成功登入 |
| 未鎖定輸入錯誤密碼 | ✅ 驗證密碼 → 失敗 | ✅ 驗證密碼 → 失敗 |

---

## 🎯 關鍵改進

1. **安全性提升** ✅
   - 被鎖定的帳號不會進行密碼驗證
   - 防止通過鎖定帳號測試密碼正確性

2. **邏輯清晰** ✅
   - 鎖定檢查集中在 `userDetailsService()`
   - `successHandler` 只處理成功後的邏輯

3. **效能提升** ✅
   - 被鎖定的帳號不查詢資料庫
   - 減少不必要的資料庫操作

4. **程式碼簡化** ✅
   - 移除 successHandler 中無用的檢查
   - 降低程式碼複雜度

---

## 📌 注意事項

1. 修改後，`LockedException` 會被 `failureHandler` 捕獲
2. 錯誤訊息會正確顯示鎖定時間
3. 會員和員工的鎖定狀態是分開檢查的
4. IP 鎖定檢查仍保留在 `successHandler` 中

---

**修改完成日期：** 2025-10-18
**修改者：** Cascade AI
**審核狀態：** ✅ 已完成

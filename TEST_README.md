# 電影院系統測試文檔

## 測試概述

本專案包含完整的測試單元，涵蓋服務層、控制器層和整合測試。

## 測試結構

目前測試分為三層：
- `src/test/java/com/example/cinema/service`：服務層單元測試
- `src/test/java/com/example/cinema/controller`：控制器測試
- `src/test/java/com/example/cinema/integration`：流程整合測試

可用下列指令查看當前測試檔案清單：
```bash
find src/test/java -name '*Test.java' -o -name '*IntegrationTest.java'
```

## 測試覆蓋範圍

### 1. MovieServiceTest (電影服務測試)
- ✅ 獲取所有電影列表
- ✅ 根據 ID 獲取特定電影
- ✅ 處理不存在的電影 ID
- ✅ 獲取電影場次信息
- ✅ 處理不存在的場次 ID
- ✅ 獲取座位佈局
- ✅ 獲取場次詳細信息
- ✅ 驗證座位佈局的一致性
- ✅ 驗證已預訂和可用座位

測試案例會隨功能迭代增加，請以 CI 執行結果為準。

### 2. SessionServiceTest (會話服務測試)
- ✅ 獲取訪客觀看清單鍵
- ✅ 驗證未認證會話
- ✅ 驗證已認證會話
- ✅ 不同領域的獨立認證
- ✅ 記錄登入嘗試
- ✅ 記錄多次登入嘗試
- ✅ 清除登入嘗試記錄
- ✅ 驗證鎖定時間
- ✅ 不同領域的獨立嘗試計數

測試案例會隨功能迭代增加，請以 CI 執行結果為準。

### 3. MemberApiControllerTest (會員 API 控制器測試)
- ✅ 添加電影到訪客觀看清單
- ✅ 獲取訪客觀看清單
- ✅ 處理空觀看清單
- ✅ 已認證會員獲取摘要信息
- ✅ 未認證用戶訪問限制
- ✅ 添加多部電影到觀看清單

測試案例會隨功能迭代增加，請以 CI 執行結果為準。

### 4. MovieIntegrationTest (電影功能整合測試)
- ✅ 獲取所有電影列表
- ✅ 完整訂票流程測試
- ✅ 驗證所有電影的場次信息
- ✅ 驗證座位佈局一致性
- ✅ 處理無效電影 ID
- ✅ 處理無效場次 ID
- ✅ 驗證海報 URL
- ✅ 驗證電影描述

測試案例會隨功能迭代增加，請以 CI 執行結果為準。

## 運行測試

### 運行所有測試
```bash
mvn test
```

### 選配：瀏覽器 E2E（Playwright）
預設不會啟用，避免每次本機測試都依賴瀏覽器執行環境。

```bash
mvn test -Dbrowser.e2e=true -Dtest=BrowserAuthE2EPlaywrightTest
```

### 選配：真 MySQL 整合測試（Testcontainers）
需要本機可用 Docker。預設不啟用。

```bash
mvn test -Dmysql.it=true -Dtest=RealMySqlContainerIntegrationTest
```

## Demo 帳號與資料庫操作 (MySQL)

### 環境變數 (建議)
正式環境不要把資料庫連線資訊寫死在專案內，建議用環境變數提供：

- `SPRING_PROFILES_ACTIVE=dev` (本機) 或 `SPRING_PROFILES_ACTIVE=prod` (上線)
- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`

範例可參考專案根目錄的 `.env.example`。

### 目前預設測試帳號 (members 與 employee 兩邊同步)
以下帳號在 `members` 與 `employee` 兩張表都存在，且密碼 = 帳號（bcrypt 雜湊存放）。

- `member01 / member01`
- `test123 / test123`
- `emp01 / emp01`
- `em01 / em01`

登入入口：
- 會員：`/member/login`
- 員工：`/employee/login`
- 統一入口（會轉發到正確的登入處理）：`/login?target=member`、`/login?target=employee`

### 查詢目前帳號清單與角色
```sql
SELECT 'members' AS tbl, id, nickname, LEFT(password, 24) AS password_prefix
FROM members
ORDER BY nickname;

SELECT 'employee' AS tbl, e.id, e.nickname, r.code AS role_code, r.level,
       LEFT(e.password, 24) AS password_prefix
FROM employee e
JOIN roles r ON r.id = e.role_id
ORDER BY e.nickname;
```

### 升級/降級員工角色（用來 demo IT / 主管 / 管理頁）
員工後台導向與權限取決於 `employee.role_id -> roles.code/level`。

如果你已經有 ADMIN 權限，也可以直接用後台頁面操作：
- `/employee/admin/roles`

將指定員工升級為 IT：
```sql
UPDATE employee
SET role_id = (SELECT id FROM roles WHERE code = 'IT' LIMIT 1)
WHERE nickname = 'member01';
```

升級為 MANAGER：
```sql
UPDATE employee
SET role_id = (SELECT id FROM roles WHERE code = 'MANAGER' LIMIT 1)
WHERE nickname = 'member01';
```

升級為 ADMIN：
```sql
UPDATE employee
SET role_id = (SELECT id FROM roles WHERE code = 'ADMIN' LIMIT 1)
WHERE nickname = 'member01';
```

降回一般員工 EMPLOYEE：
```sql
UPDATE employee
SET role_id = (SELECT id FROM roles WHERE code = 'EMPLOYEE' LIMIT 1)
WHERE nickname = 'member01';
```

### 運行特定測試類
```bash
# 運行電影服務測試
mvn test -Dtest=MovieServiceTest

# 運行會話服務測試
mvn test -Dtest=SessionServiceTest

# 運行會員 API 控制器測試
mvn test -Dtest=MemberApiControllerTest

# 運行整合測試
mvn test -Dtest=MovieIntegrationTest
```

### 查看測試摘要報告
```bash
ls target/surefire-reports
```

## 測試覆蓋率目標

- **服務層**: > 80%
- **控制器層**: > 70%
- **整體覆蓋率**: > 75%

## 測試最佳實踐

1. **命名規範**: 使用 `@DisplayName` 註解提供清晰的中文測試描述
2. **Given-When-Then**: 遵循 AAA (Arrange-Act-Assert) 模式
3. **獨立性**: 每個測試應該獨立運行，不依賴其他測試
4. **清理**: 使用 `@BeforeEach` 和 `@AfterEach` 進行測試前後的設置和清理
5. **斷言**: 使用有意義的斷言消息

## 持續整合

建議在 CI/CD 流程中自動運行測試：

```yaml
# GitHub Actions 範例
- name: Run tests
  run: mvn clean test

- name: Enforce test baseline
  run: |
    total_tests=$(grep -Rho 'tests="[0-9]\+"' target/surefire-reports/*.xml | grep -Eo '[0-9]+' | awk '{sum+=$1} END {print sum+0}')
    test "${total_tests}" -ge 50
```

## 測試數據

測試使用以下數據：
- **電影數量**: 10 部
- **座位配置**: 12 排 × 8 列 = 96 個座位
- **場次數量**: 每部電影 5-6 個場次

## 常見問題

### Q: 測試失敗怎麼辦？
A: 檢查測試日誌，確認是否有依賴問題或配置錯誤。

### Q: 如何跳過測試？
A: 使用 `mvn install -DskipTests` 或 `mvn install -Dmaven.test.skip=true`

### Q: 如何只運行單元測試（不包括整合測試）？
A: 使用 `mvn test -Dtest=*Test`

### Q: 如何只運行整合測試？
A: 使用 `mvn test -Dtest=*IntegrationTest`

## 測試維護

- 每次添加新功能時，應該同時添加相應的測試
- 定期檢查測試覆蓋率，確保關鍵業務邏輯都有測試
- 當測試失敗時，先修復測試再修復代碼
- 保持測試代碼的可讀性和可維護性

## 總結

本測試套件提供了全面的測試覆蓋，確保電影院系統的核心功能正常運作。通過這些測試，我們可以：

1. 及早發現 bug
2. 安全地重構代碼
3. 確保新功能不會破壞現有功能
4. 提供代碼使用範例
5. 作為文檔參考

**總測試數量**: 請以 `mvn test`/CI 輸出為準（避免文件數字過期）。

# 電影院系統測試文檔

最後修改日期：`2026-02-28`

## 1. 文件定位

- `README.md`：專案啟動、環境設定、功能規則總覽
- `TEST_README.md`（本文件）：測試分層、執行方式、測試資料與故障排查
- 最終以程式碼與 CI 實際執行結果為準

## 2. 測試分層與清單

目前測試主要分為三層：
- `src/test/java/com/example/cinema/service`：服務層單元測試
- `src/test/java/com/example/cinema/controller`：控制器測試
- `src/test/java/com/example/cinema/integration`：流程整合測試（含條件式瀏覽器 E2E）

查詢完整測試類清單：

```bash
find src/test/java -type f \( -name '*Test.java' -o -name '*IntegrationTest.java' -o -name '*IT.java' \) | sort
```

查詢目前總測試數（依最新 surefire 報告）：

```bash
total_tests=$(grep -Rho 'tests="[0-9]\+"' target/surefire-reports/*.xml | grep -Eo '[0-9]+' | awk '{sum+=$1} END {print sum+0}')
echo "$total_tests"
```

## 3. 覆蓋範圍（代表項目）

本節為代表性盤點，不是完整清單；完整內容請用上方命令即時查詢。

### 3.1 服務層

- `MovieServiceTest`
- `SessionServiceTest`
- `LoginAttemptServiceTest`
- `ApiRateLimitServiceTest`
- `UserServiceTest`

### 3.2 控制器層

- `MemberApiControllerTest`
- `MemberOrderControllerRateLimitTest`
- `MemberOrdersPageControllerTest`
- `MemberPasswordControllerTest`
- `EmployeeAdminControllerTest`
- `EmployeeShowtimeAdminControllerTest`
- `EmployeeMovieAdminControllerTest`

### 3.3 整合層

- `MovieIntegrationTest`
- `MemberOrderE2EIntegrationTest`
- `MemberOrderWebE2EIntegrationTest`
- `OperationsWorkflowIntegrationTest`
- `SecurityAccessIntegrationTest`
- `ShowtimeAdminIntegrationTest`
- `HomeUiContractTest`
- `BrowserAuthE2EPlaywrightTest`（條件式啟用）

## 4. 執行測試

### 4.1 基線測試（建議日常）

```bash
mvn clean test
```

說明：
- 會跑單元 + 控制器 + 整合測試。
- `BrowserAuthE2EPlaywrightTest` 預設不啟用（未帶 `-Dbrowser.e2e=true` 時會 `skipped`）。
- 基線測試走 `@ActiveProfiles("test")` + `src/test/resources/application-test.properties`（H2 in-memory），不需要本機 MySQL。

### 4.2 條件式瀏覽器 E2E（Playwright）

先一次性安裝 Chromium：

```bash
PLAYWRIGHT_BROWSERS_PATH=.playwright-browsers \
mvn -B -DskipTests test-compile dependency:build-classpath -Dmdep.outputFile=target/test.classpath

CP="target/test-classes:target/classes:$(cat target/test.classpath)"
PLAYWRIGHT_BROWSERS_PATH=.playwright-browsers \
java -cp "$CP" com.microsoft.playwright.CLI install chromium
```

執行瀏覽器 E2E：

```bash
PLAYWRIGHT_BROWSERS_PATH=.playwright-browsers \
PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1 \
mvn -Djacoco.skip=true -Dbrowser.e2e=true -Dtest=BrowserAuthE2EPlaywrightTest test
```

必要條件：
- 可連線 `cdn.playwright.dev`
- Linux 需具備 Chromium 執行所需動態函式庫
- `.playwright-browsers/` 已在 `.gitignore`，不會上傳 Git

### 4.3 只跑特定測試類（精準模式）

只跑單元/控制器代表測試：

```bash
mvn test -Dtest=MovieServiceTest,SessionServiceTest,ApiRateLimitServiceTest,MemberApiControllerTest,MemberOrdersPageControllerTest
```

只跑整合代表測試（不含瀏覽器 E2E）：

```bash
mvn test -Dtest=MovieIntegrationTest,OperationsWorkflowIntegrationTest,SecurityAccessIntegrationTest,MemberOrderE2EIntegrationTest,ShowtimeAdminIntegrationTest
```

說明：
- 專案目前未拆分成 surefire/failsafe 雙階段；若要自訂「只跑哪些測試」，建議用 `-Dtest=...` 明確列出類名。

### 4.4 測試報告位置

```bash
ls target/surefire-reports
```

## 5. 測試資料與帳號（dev seed）

資料來源：
- `src/main/resources/db/dev-migration/R__dev_seed.sql`

目前 seed 帳號（`members` 與 `employee` 兩邊同步存在）：
- `member01 / member01`
- `test123 / test123`
- `emp01 / emp01`
- `em01 / em01`

登入入口：
- 會員：`/member/login`
- 員工：`/employee/login`
- 統一入口：`/login?target=member`、`/login?target=employee`

查詢帳號與角色：

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

## 6. CI 與覆蓋率門檻

CI 設定：
- `.github/workflows/ci.yml`

目前門檻：
- 測試總數基線：至少 50（CI 會檢查）
- JaCoCo LINE 覆蓋率：`>= 50%`（`pom.xml` 設定）

CI 另有 `browser-e2e` job：
- 安裝 Playwright Chromium
- 執行 `BrowserAuthE2EPlaywrightTest`

## 7. 常見問題

### Q1：為什麼 `mvn clean test` 不需要本機 MySQL？

A：測試 profile 使用 H2 in-memory（`src/test/resources/application-test.properties`），不是走你本機 MySQL。

### Q2：看到 `Communications link failure` 代表什麼？

A：通常發生在你啟動應用程式（dev/prod）或手動用 MySQL 的流程，不是基線測試本身；請檢查 `.env` 的 `DB_URL/DB_USERNAME/DB_PASSWORD` 與本機 MySQL 狀態。

### Q3：如何跳過測試？

A：

```bash
mvn install -DskipTests
```

### Q4：如何只跑瀏覽器 E2E？

A：

```bash
PLAYWRIGHT_BROWSERS_PATH=.playwright-browsers \
PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1 \
mvn -Djacoco.skip=true -Dbrowser.e2e=true -Dtest=BrowserAuthE2EPlaywrightTest test
```

## 8. 維護原則

- 新功能必須附對應測試或補測計畫
- 若測試失敗，先確認是程式回歸還是測試本身過期
- 測試數量與覆蓋率以 CI 作為主準據

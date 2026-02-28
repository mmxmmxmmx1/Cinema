# 測試清單（Test Inventory）

本文件整理目前專案的自動化測試範圍與對應測試類別，方便快速盤點。

## 總覽

- 測試類別：`28`
- 測試方法（`@Test/@ParameterizedTest/@RepeatedTest`）：約 `150`
- 全量執行指令：`mvn clean test -Djacoco.skip=true`

## 一般單元與 Web 測試（預設會跑）

### Service

- `src/test/java/com/example/cinema/service/LoginAttemptServiceTest.java`
- `src/test/java/com/example/cinema/service/SessionServiceTest.java`
- `src/test/java/com/example/cinema/service/ApiRateLimitServiceTest.java`
- `src/test/java/com/example/cinema/service/MovieServiceTest.java`
- `src/test/java/com/example/cinema/service/UserServiceTest.java`

### Controller

- `src/test/java/com/example/cinema/controller/EmployeeAdminControllerTest.java`
- `src/test/java/com/example/cinema/controller/EmployeeChecklistControllerTest.java`
- `src/test/java/com/example/cinema/controller/EmployeeMaintenanceControllerTest.java`
- `src/test/java/com/example/cinema/controller/EmployeeMovieAdminControllerTest.java`
- `src/test/java/com/example/cinema/controller/EmployeeShowtimeAdminControllerTest.java`
- `src/test/java/com/example/cinema/controller/EmployeeTodoControllerTest.java`
- `src/test/java/com/example/cinema/controller/MemberApiControllerTest.java`
- `src/test/java/com/example/cinema/controller/MemberBookingControllerTest.java`
- `src/test/java/com/example/cinema/controller/MemberOrderControllerRateLimitTest.java`
- `src/test/java/com/example/cinema/controller/MemberOrdersPageControllerTest.java`
- `src/test/java/com/example/cinema/controller/MemberPasswordControllerTest.java`
- `src/test/java/com/example/cinema/controller/PageControllerLoginTest.java`
- `src/test/java/com/example/cinema/controller/PageControllerMemberPageTest.java`

### Integration（一般）

- `src/test/java/com/example/cinema/integration/HomeUiContractTest.java`
- `src/test/java/com/example/cinema/integration/MemberNotificationRetentionIntegrationTest.java`
- `src/test/java/com/example/cinema/integration/MemberOrderE2EIntegrationTest.java`
- `src/test/java/com/example/cinema/integration/MemberOrderWebE2EIntegrationTest.java`
- `src/test/java/com/example/cinema/integration/MovieIntegrationTest.java`
- `src/test/java/com/example/cinema/integration/OperationsWorkflowIntegrationTest.java`
- `src/test/java/com/example/cinema/integration/SecurityAccessIntegrationTest.java`
- `src/test/java/com/example/cinema/integration/ShowtimeAdminIntegrationTest.java`

## 條件式測試（需開關）

### Playwright 瀏覽器 E2E

- 類別：`src/test/java/com/example/cinema/integration/BrowserAuthE2EPlaywrightTest.java`
- 啟用方式：

```bash
PLAYWRIGHT_BROWSERS_PATH=.playwright-browsers \
PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1 \
mvn -Djacoco.skip=true -Dbrowser.e2e=true -Dtest=BrowserAuthE2EPlaywrightTest test
```

## 建議執行順序

1. 先跑基線：`mvn clean test`
2. 再跑瀏覽器 E2E（如需 UI 驗證）

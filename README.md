# Cinema (Spring Boot Side Project)

電影院訂票 side project，使用 Spring Boot + Thymeleaf + MySQL，包含：
- 會員登入、訂票、付款（mock）、訂單與歷史訂單
- 站內通知（保留 7 天）
- 員工後台（每日待辦、影廳檢查表、維修申請流程）
- 管理員功能（角色管理、場次管理 CRUD/停用）
- 會員點數（累積 + 兌換扣點）
- API 防刷限流 + Trace Id 錯誤追蹤

## 架構快覽

```text
Browser (Vue SPA + Thymeleaf)
        |
Spring MVC Controller
        |
Service (訂票/付款/通知/點數/營運)
        |
JdbcTemplate + MySQL
        |
Flyway Migration
```

補充文件：
- 架構說明：`docs/architecture.md`
- Demo 劇本：`docs/demo-script.md`

## 1. 環境需求

- Java 17
- Maven 3.9+
- MySQL 8+

## 2. 啟動方式

```bash
mvn spring-boot:run
```

預設首頁：
- `http://localhost:8080/`

登入入口：
- 會員：`/member/login`
- 員工：`/employee/login`
- 統一入口：`/login?target=member`、`/login?target=employee`

## 3. 資料庫設定

主要設定在 `src/main/resources/application.properties` 與 profile 檔：
- `src/main/resources/application-dev.properties`
- `src/main/resources/application-prod.properties`

建議使用環境變數（範例見 `.env.example`）：
- `SPRING_PROFILES_ACTIVE=dev`
- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `APP_TIME_ZONE`（預設 `Asia/Taipei`）

Flyway migration 位置：
- `src/main/resources/db/migration`

## Migration 規範（重要）

- 已上線（已套用）的 migration 檔案不要直接修改。
- 若要調整 schema，一律新增下一版（例如 `V12__...sql`）。
- 如果你已經改了舊 migration 且資料庫已套用，啟動時會出現 checksum mismatch。

處理方式（二選一）：
1. 還原舊 migration 檔案內容（推薦）。
2. 在確認沒有風險後執行 Flyway repair（僅更新 schema history checksum，不會重跑 SQL）。

CI 在 Pull Request 會檢查：
- `src/main/resources/db/migration/V*.sql` 只能新增，不能修改/刪除既有版本檔。

本專案常用排查：
- 查歷史：`SELECT * FROM flyway_schema_history ORDER BY installed_rank;`
- 找失敗：`... WHERE success = 0`

## 4. 核心規則

- 訂票時段：每日 `07:00` 到 `22:45`
- `22:40` 起顯示即將關閉警示
- 單筆訂單最多 4 張
- 6 小時內最多 4 張，且只能同一影廳
- 開演前 30 分鐘內不可取消已付款訂單
- 座位占用以「場次開始時間」為範圍，不影響下一場

## 5. 會員功能

- 點數累積：已付款訂單金額每 `10` 元累積 `1` 點
- 點數兌換：可在 `/member/points` 直接兌換並扣點
- 我的訂單：`/member/orders`
- 歷史訂單：會員專區中「歷史訂單」區塊

## 6. 員工與管理員功能

- 員工首頁：`/employee`
- 影廳檢查表：`/employee/checklist`
- 維修申請：`/employee/maintenance`
  - 狀態流程：`OPEN -> IN_PROGRESS -> RESOLVED -> CLOSED`
  - 可轉 `CANCELLED`
- 管理員角色管理：`/employee/admin/roles`
- 管理員場次管理：`/employee/admin/showtimes`
  - 可新增/更新場次
  - 可停用場次

## 7. 測試

執行全部測試：

```bash
mvn clean test
```

測試包含：
- Service / Controller 單元測試
- Integration 測試（電影流程與新功能流程）

## 8. CI

GitHub Actions：
- `.github/workflows/ci.yml`
- 觸發：push / pull request 到 `main` 或 `master`
- 任務：`mvn -B clean test`

## 9. 不接外部服務的完整度設計

本專案預設不依賴真實金流、Email、SMS，改採：

- `app.payment.provider=mock`：付款成功/失敗/逾時可切換
- `app.notification.provider=inapp`：站內通知替代外部推播
- 管理頁維護工具：可手動執行過期訂單/通知清理

## 10. Demo 資料重置

可使用腳本清空交易資料（保留帳號與角色）：

```bash
DB_URL='jdbc:mysql://localhost:3306/cinema?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Taipei&useUnicode=true&characterEncoding=utf8' \
DB_USERNAME='' \
DB_PASSWORD='' \
./scripts/reset-dev-demo-data.sh
```

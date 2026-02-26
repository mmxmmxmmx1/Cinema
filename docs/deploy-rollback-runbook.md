# 部署與回滾 Runbook

## 1. 部署前檢查

1. 確認 `master` 與遠端同步。
2. 執行：
   - `mvn clean verify`
3. 備份資料庫：
   - `DB_USERNAME=... DB_PASSWORD=... BACKUP_DIR=... ./scripts/mysql-backup.sh`
   - 備份/還原細節：`docs/database-backup-restore-runbook.md`
4. 確認必要環境變數：
   - `SPRING_PROFILES_ACTIVE=prod`
   - `DB_URL`
   - `DB_USERNAME`
   - `DB_PASSWORD`

## 2. 部署流程

1. 啟動新版：
   - `mvn -DskipTests spring-boot:run`
   - 或使用 jar 啟動方式。
2. 驗證健康狀態：
   - `APP_BASE_URL=http://localhost:8080 ./scripts/prod-smoke-check.sh`
3. 驗證核心功能：
   - 會員登入 -> 訂票 -> 付款 -> 訂單查詢
   - 取消規則（開演前 30 分鐘）

## 3. 回滾流程

1. 停用新版應用。
2. 切回前一版程式碼（tag 或 commit）。
3. 如需資料回滾，還原部署前備份：
   - `DB_USERNAME=... DB_PASSWORD=... CONFIRM_RESTORE=yes ./scripts/mysql-restore.sh backups/xxx.sql.gz`
4. 重新啟動前一版並跑 smoke check。

## 4. Flyway 例外處理

- 若發生 checksum mismatch：
1. 優先還原被修改的舊 `V*.sql`。
2. 僅在確認 SQL 內容等價時使用 `flyway repair`。

> 原則：不要修改已套用的 `V*.sql`，一律新增新版本或改 `R__*.sql`。

# 資料庫備份與還原 Runbook

## 1. 目的

- 每日固定產生可還原的 MySQL 備份。
- 保留最近 N 天備份（預設 14 天）。
- 還原前先做完整性檢查，避免使用毀損檔案。

## 2. 備份腳本

檔案：`scripts/mysql-backup.sh`

### 2.1 主要參數

- `DB_HOST`（預設 `localhost`）
- `DB_PORT`（預設 `3306`）
- `DB_NAME`（預設 `cinema`）
- `DB_USERNAME`（必填）
- `DB_PASSWORD`（必填）
- `BACKUP_DIR`（預設 `./backups`）
- `BACKUP_COMPRESS`（預設 `1`，輸出 `.sql.gz`）
- `BACKUP_RETENTION_DAYS`（預設 `14`）

### 2.2 手動執行

```bash
DB_HOST=127.0.0.1 \
DB_PORT=3306 \
DB_NAME=cinema \
DB_USERNAME= \
DB_PASSWORD= \
BACKUP_DIR=./backups \
BACKUP_RETENTION_DAYS=14 \
./scripts/mysql-backup.sh
```

成功後會產生：
- `cinema_<db>_<timestamp>.sql.gz`（或 `.sql`）
- `cinema_<db>_<timestamp>.sql.gz.sha256`

## 3. 排程建議（cron）

每天凌晨 02:30 備份：

```cron
30 2 * * * cd /home/mmx/桌面/Cinema && DB_HOST=127.0.0.1 DB_PORT=3306 DB_NAME=cinema DB_USERNAME= DB_PASSWORD= BACKUP_DIR=/home/mmx/backup/cinema BACKUP_RETENTION_DAYS=14 ./scripts/mysql-backup.sh >> /home/mmx/backup/cinema/backup.log 2>&1
```

## 4. 還原腳本

檔案：`scripts/mysql-restore.sh`

### 4.1 主要安全機制

- 需明確設定 `CONFIRM_RESTORE=yes` 才會執行。
- 若存在 `*.sha256`，會先驗證備份檔完整性。
- 支援 `.sql` 與 `.sql.gz`。

### 4.2 手動執行

```bash
DB_HOST=127.0.0.1 \
DB_PORT=3306 \
DB_NAME=cinema \
DB_USERNAME= \
DB_PASSWORD= \
CONFIRM_RESTORE=yes \
./scripts/mysql-restore.sh ./backups/cinema_cinema_20260225_023000.sql.gz
```

## 5. 演練與驗證

- 至少每月一次「還原演練」到獨立測試資料庫。
- 驗證項目：
  - 應用程式可啟動
  - `/api/health` 為 `UP`
  - 會員登入、下單、付款、查詢可正常完成
- 演練結果寫入 `docs/production-validation-checklist.md` 或內部維運紀錄。

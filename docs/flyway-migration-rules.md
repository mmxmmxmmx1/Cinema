# Flyway Migration 規範

本專案使用 Flyway 管理 schema，請遵守以下規則避免 `checksum mismatch`：

## 1. 不可修改已套用版本

- 任何已存在於 `src/main/resources/db/migration/V*.sql` 且已在環境執行過的版本檔，不可再修改內容。
- 需要調整 schema 或資料時，請新增下一版 migration（例如 `V15__...sql`）。

## 2. 允許的變更方式

- 新增 schema：`CREATE TABLE ...`
- 變更 schema：`ALTER TABLE ...`
- 資料修正：`UPDATE ... WHERE ...`
- 補 seed：`INSERT ... ON DUPLICATE KEY UPDATE ...`

### 2.1 內容型資料（海報/輪播）規則

- `movie_catalog` 的 `poster_url`、`carousel_image_url` 屬於「內容型資料」。
- 不要再新增 `V*.sql` 只為了改圖片網址。
- 請統一改 `src/main/resources/db/migration/R__movie_catalog_assets.sql`（repeatable migration）。
- CI 會拒絕在新 `V*.sql` 中直接修改 `poster_url` / `carousel_image_url`。

## 3. 發生 checksum mismatch 時

優先順序：

1. 還原被改動的舊 migration 內容（推薦）。
2. 確認風險後才執行 `flyway repair` 更新歷史 checksum。

> `repair` 不會重跑 SQL，只會修正 `flyway_schema_history`，請務必先確認資料庫狀態。

## 4. PR/CI 檢查

- PR 只允許「新增」 migration，不允許修改既有版本。
- Push 與 PR 都會由 CI 執行 `scripts/check-migration-immutability.sh` 檢查。
- 腳本參數可用「分支名稱」或「commit SHA」。

本機可手動檢查（範例）：

```bash
./scripts/check-migration-immutability.sh origin/master
./scripts/check-migration-immutability.sh HEAD~1
```

## 5. 命名建議

- 格式：`V{版本}__{snake_case_描述}.sql`
- 範例：`V15__admin_update_movie_poster_url.sql`

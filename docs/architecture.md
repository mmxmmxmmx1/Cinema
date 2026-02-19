# 系統架構（不依賴外部真實服務）

## 1. 架構分層

- `Controller`：處理頁面與 API 請求。
- `Service`：業務規則（訂票限制、付款流程、通知、點數、維修流程）。
- `DAO/JdbcTemplate`：讀寫 MySQL。
- `Flyway`：資料庫 schema 與資料結構演進。

## 2. 核心流程

1. 會員選位建立 `PENDING` 訂單（不先占座）。
2. 付款成功後才寫入 `member_tickets`（正式占座）。
3. 取消已付款訂單時刪除 `member_tickets`（釋放座位）。
4. 排程自動將逾時未付款訂單轉 `EXPIRED`。

## 3. 重要設計

- 場次座位唯一鍵：`(show_start_at, showtime_id, seat_id)`，防止同場同座重複售出。
- 付款冪等：`payment_idempotency` 防止重複扣款。
- 通知保留：站內通知預設保留 30 天並定時清理。
- 全站閒置登出：`server.servlet.session.timeout=10m`。
- API 防刷：會員下單/付款/取消與通知已讀具備每分鐘限流。

## 4. 可觀測性

- `audit_logs` 記錄關鍵操作（建立訂單、付款、取消、排程失效）。
- API 回應統一 `ApiError`，含 `traceId`（`X-Trace-Id`）。

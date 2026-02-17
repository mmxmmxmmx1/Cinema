# 真實環境驗證清單（MySQL + 瀏覽器）

> 自動化測試通過後，部署前仍建議執行本清單。

## 1. 資料庫與 migration

1. 確認環境變數：
   - `SPRING_PROFILES_ACTIVE=prod`
   - `DB_URL`
   - `DB_USERNAME`
   - `DB_PASSWORD`
2. 啟動應用程式，確認 Flyway 無 checksum 錯誤。
3. 檢查 migration 歷史：
   - `SELECT * FROM flyway_schema_history ORDER BY installed_rank;`
4. 確認 `R__movie_catalog_assets.sql` 已套用（description 以 `R__` 開頭）。

## 2. 會員訂票核心流程

1. 會員登入 -> 建立 1 筆訂單 -> 付款成功。
2. 重新整理/回上一頁，不應觸發重複扣款。
3. 同場次同座位不可重複購買。
4. 訂單逾時後座位需釋放，可重新購買。
5. 開演前 30 分鐘內取消應被拒絕。

## 3. 安全與權限

1. 未登入訪問 `/member/orders` 應導向會員登入。
2. 未登入訪問 `/employee/admin/**` 應導向員工登入。
3. MEMBER 身分訪問員工頁應 403。
4. EMPLOYEE 身分訪問會員 API 應 403。
5. 模擬缺失/過期 CSRF token，確認提示訊息可理解。

## 4. Session 與多分頁

1. 同一帳號開兩分頁操作付款，不可重複成交。
2. 閒置超時後重新操作，應要求重新登入。
3. 登出後直接返回上一頁，不可繼續操作受保護功能。

## 5. 前端可用性

1. 桌機（1920x1080）檢查首頁輪播與會員/員工入口不重疊。
2. 手機尺寸（390x844）檢查可點擊與排版。
3. `member/orders` 通知「保留 30 天」文字需可見。

## 6. 營運與資料一致性

1. 通知清理後，未讀數與列表一致。
2. 付款成功/失敗在管理頁統計可見。
3. 取消訂單後，紅利點數回滾一致。

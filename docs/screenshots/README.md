# Screenshots

本目錄保留專案 UI 展示截圖。

目前根目錄 `README.md` 已改為直接引用 `src/main/resources/static/images/` 內的現行本機電影素材，避免舊截圖中的電影名稱與海報跟目前程式資料不同步。

截圖來源：
- 環境：本機 `dev` profile
- 資料庫：MySQL `cinema`
- 會員流程：以臨時 demo 會員產生訂票與付款畫面，截圖後已清理 demo 會員資料
- 管理員流程：截圖期間短暫提升 `member01` 為 `ADMIN`，截圖後已還原為 `EMPLOYEE`

目前電影素材來源：
- 品牌圖：`src/main/resources/static/images/sleep.jpg`
- 電影直式海報與首頁輪播圖：`src/main/resources/static/images/*.png`
- 電影資料 fallback：`src/main/java/com/example/cinema/service/MovieService.java`
- DB 圖片對照：`src/main/resources/db/migration/R__movie_catalog_assets.sql`

檔案用途：
- `home.png`：首頁與電影輪播歷史截圖
- `booking-flow.png`：會員訂票/付款成功流程
- `seat-selection.png`：選位頁
- `member-orders.png`：會員訂單中心
- `employee-dashboard.png`：員工後台
- `admin-movies.png`：管理員電影管理
- `admin-showtimes.png`：管理員場次管理

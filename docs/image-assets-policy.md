# 圖片素材使用規範

## 目前策略

1. 影廳卡片 `poster_url` 與首頁輪播 `carousel_image_url` 分離。
2. 圖片對應集中管理在：
   - `src/main/resources/db/migration/R__movie_catalog_assets.sql`
3. 不可在新 `V*.sql` 直接改圖片 URL（CI 會擋）。

## 風險提醒

- 若作品公開展示或商用，需確認圖片來源授權與著作權條款。
- 建議逐步替換為：
  1. 官方授權素材
  2. 自製素材
  3. 可商用授權圖庫素材

## 維護流程

1. 修改 `R__movie_catalog_assets.sql`
2. 啟動應用讓 Flyway 套用 repeatable migration
3. 驗證首頁輪播與影廳卡片顯示

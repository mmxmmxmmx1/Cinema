# 專案交接清單

## 功能面

1. 會員：註冊/登入/訂票/付款/取消/通知/點數
2. 員工：登入/待辦/檢查表/維修流程
3. 管理：角色管理/場次管理/營運數據

## 環境面

1. Java 17 + Maven
2. MySQL 8+
3. `.env` 與 `application-prod.properties` 參數確認

## 部署面

1. 先跑 `mvn clean verify`
2. 部署前資料庫備份
3. 部署後跑 smoke check
4. 回滾流程可用

## 測試面

1. 自動化測試全綠
2. 手動驗收（訂票核心流程）完成
3. 權限與 CSRF 邊界測試完成

## 文件面

1. README
2. `docs/flyway-migration-rules.md`
3. `docs/deploy-rollback-runbook.md`
4. `docs/production-validation-checklist.md`
5. `docs/data-governance.md`
6. `docs/image-assets-policy.md`

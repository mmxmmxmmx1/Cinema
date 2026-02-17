# 瀏覽器流程自動化（目前方案）

本專案目前以 Spring Boot Web 層整合測試作為「瀏覽器流程自動化基線」：

- `MemberOrderWebE2EIntegrationTest`
  - 訂票 -> 付款 -> 取消 -> 釋位
  - 開演前 30 分鐘內取消限制
- `SecurityAccessIntegrationTest`
  - 會員/員工權限隔離
  - 未登入導向與 API 存取拒絕
- `HomeUiContractTest`
  - 首頁 Hero/Carousel UI 合約（包含輪播張數與標語位置）

## 執行方式

```bash
mvn -Dtest=MemberOrderWebE2EIntegrationTest,SecurityAccessIntegrationTest,HomeUiContractTest test
```

## 下一步（可選）

若要真正「真瀏覽器」(Chromium/Firefox/WebKit) 自動化，可再加 Playwright：

1. 新增 `e2e/` 測試專案。
2. 在 CI 補一個 `playwright` job。
3. 覆蓋多分頁、返回鍵、RWD 實際互動。

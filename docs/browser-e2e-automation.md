# 瀏覽器流程自動化

本專案目前有兩層：

1. Spring Boot Web 層整合測試（快速基線）
2. Playwright 真瀏覽器 E2E（登入/登出與深連結）

## 1) Web 層整合測試（快速基線）

- `MemberOrderWebE2EIntegrationTest`
  - 訂票 -> 付款 -> 取消 -> 釋位
  - 開演前 30 分鐘內取消限制
- `SecurityAccessIntegrationTest`
  - 會員/員工權限隔離
  - 未登入導向與 API 存取拒絕
- `HomeUiContractTest`
  - 首頁 Hero/Carousel UI 合約（包含輪播張數與標語位置）

執行方式：

```bash
mvn -Dtest=MemberOrderWebE2EIntegrationTest,SecurityAccessIntegrationTest,HomeUiContractTest test
```

## 2) Playwright 真瀏覽器 E2E

- `BrowserAuthE2EPlaywrightTest`
  - 會員登入/登出
  - 員工登入/登出
  - 匿名使用者直接開啟 `movies/checkout/orders` 深連結導回首頁

執行方式：

先一次性安裝 Chromium：

```bash
PLAYWRIGHT_BROWSERS_PATH=.playwright-browsers \
mvn -B -DskipTests test-compile dependency:build-classpath -Dmdep.outputFile=target/test.classpath

CP="target/test-classes:target/classes:$(cat target/test.classpath)"
PLAYWRIGHT_BROWSERS_PATH=.playwright-browsers \
java -cp "$CP" com.microsoft.playwright.CLI install chromium
```

再執行測試（避免測試期下載瀏覽器）：

```bash
PLAYWRIGHT_BROWSERS_PATH=.playwright-browsers \
PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1 \
mvn -Djacoco.skip=true -Dbrowser.e2e=true -Dtest=BrowserAuthE2EPlaywrightTest test
```

Linux 若出現缺少函式庫警告（如 `libicudata.so.74`、`libvpx.so.9`）可安裝：

```bash
sudo apt-get update
sudo apt-get install -y libicu74 libvpx9
```

若發行版版本不同，先用 `apt-cache policy libicu* libvpx*` 查可用套件名稱。

## 後續擴充（可選）

可在 `BrowserAuthE2EPlaywrightTest` 基礎上擴充：

1. 新增 `e2e/` 測試專案。
2. 在 CI 補完整 `playwright` job。
3. 覆蓋多分頁、返回鍵、RWD 實際互動。

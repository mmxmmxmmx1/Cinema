# 電影院系統測試文檔

## 測試概述

本專案包含完整的測試單元，涵蓋服務層、控制器層和整合測試。

## 測試結構

```
src/test/java/com/example/cinema/
├── service/
│   ├── MovieServiceTest.java          # 電影服務測試
│   └── SessionServiceTest.java        # 會話服務測試
├── controller/
│   └── MemberApiControllerTest.java   # 會員 API 控制器測試
└── integration/
    └── MovieIntegrationTest.java      # 電影功能整合測試
```

## 測試覆蓋範圍

### 1. MovieServiceTest (電影服務測試)
- ✅ 獲取所有電影列表
- ✅ 根據 ID 獲取特定電影
- ✅ 處理不存在的電影 ID
- ✅ 獲取電影場次信息
- ✅ 處理不存在的場次 ID
- ✅ 獲取座位佈局
- ✅ 獲取場次詳細信息
- ✅ 驗證座位佈局的一致性
- ✅ 驗證已預訂和可用座位

**測試數量**: 11 個測試

### 2. SessionServiceTest (會話服務測試)
- ✅ 獲取訪客觀看清單鍵
- ✅ 驗證未認證會話
- ✅ 驗證已認證會話
- ✅ 不同領域的獨立認證
- ✅ 記錄登入嘗試
- ✅ 記錄多次登入嘗試
- ✅ 清除登入嘗試記錄
- ✅ 驗證鎖定時間
- ✅ 不同領域的獨立嘗試計數

**測試數量**: 9 個測試

### 3. MemberApiControllerTest (會員 API 控制器測試)
- ✅ 添加電影到訪客觀看清單
- ✅ 獲取訪客觀看清單
- ✅ 處理空觀看清單
- ✅ 已認證會員獲取摘要信息
- ✅ 未認證用戶訪問限制
- ✅ 添加多部電影到觀看清單

**測試數量**: 6 個測試

### 4. MovieIntegrationTest (電影功能整合測試)
- ✅ 獲取所有電影列表
- ✅ 完整訂票流程測試
- ✅ 驗證所有電影的場次信息
- ✅ 驗證座位佈局一致性
- ✅ 處理無效電影 ID
- ✅ 處理無效場次 ID
- ✅ 驗證海報 URL
- ✅ 驗證電影描述

**測試數量**: 8 個測試

## 運行測試

### 運行所有測試
```bash
mvn test
```

### 運行特定測試類
```bash
# 運行電影服務測試
mvn test -Dtest=MovieServiceTest

# 運行會話服務測試
mvn test -Dtest=SessionServiceTest

# 運行會員 API 控制器測試
mvn test -Dtest=MemberApiControllerTest

# 運行整合測試
mvn test -Dtest=MovieIntegrationTest
```

### 運行測試並生成報告
```bash
mvn test jacoco:report
```

報告將生成在 `target/site/jacoco/index.html`

## 測試覆蓋率目標

- **服務層**: > 80%
- **控制器層**: > 70%
- **整體覆蓋率**: > 75%

## 測試最佳實踐

1. **命名規範**: 使用 `@DisplayName` 註解提供清晰的中文測試描述
2. **Given-When-Then**: 遵循 AAA (Arrange-Act-Assert) 模式
3. **獨立性**: 每個測試應該獨立運行，不依賴其他測試
4. **清理**: 使用 `@BeforeEach` 和 `@AfterEach` 進行測試前後的設置和清理
5. **斷言**: 使用有意義的斷言消息

## 持續整合

建議在 CI/CD 流程中自動運行測試：

```yaml
# GitHub Actions 範例
- name: Run tests
  run: mvn test

- name: Generate test report
  run: mvn jacoco:report
```

## 測試數據

測試使用以下數據：
- **電影數量**: 10 部
- **座位配置**: 12 排 × 8 列 = 96 個座位
- **場次數量**: 每部電影 5-6 個場次

## 常見問題

### Q: 測試失敗怎麼辦？
A: 檢查測試日誌，確認是否有依賴問題或配置錯誤。

### Q: 如何跳過測試？
A: 使用 `mvn install -DskipTests` 或 `mvn install -Dmaven.test.skip=true`

### Q: 如何只運行單元測試（不包括整合測試）？
A: 使用 `mvn test -Dtest=*Test`

### Q: 如何只運行整合測試？
A: 使用 `mvn test -Dtest=*IntegrationTest`

## 測試維護

- 每次添加新功能時，應該同時添加相應的測試
- 定期檢查測試覆蓋率，確保關鍵業務邏輯都有測試
- 當測試失敗時，先修復測試再修復代碼
- 保持測試代碼的可讀性和可維護性

## 總結

本測試套件提供了全面的測試覆蓋，確保電影院系統的核心功能正常運作。通過這些測試，我們可以：

1. 及早發現 bug
2. 安全地重構代碼
3. 確保新功能不會破壞現有功能
4. 提供代碼使用範例
5. 作為文檔參考

**總測試數量**: 34 個測試

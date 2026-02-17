# 壓力與併發測試計畫

## 目標

1. 驗證同座位搶購不超賣。
2. 驗證高併發付款下訂單狀態一致性。
3. 驗證限流機制可保護 API。

## 已有自動化覆蓋

- `MemberOrderE2EIntegrationTest`
  - 同座位並發付款僅一筆成功
  - 逾時未付款會失效並釋位
  - idempotency key 防重複扣款

## 壓測建議（本機/壓測環境）

1. k6/JMeter 針對以下 API：
   - `POST /member/api/orders`
   - `POST /member/api/orders/{id}/pay`
   - `POST /member/api/orders/{id}/cancel`
2. 併發數：50 / 100 / 200。
3. 指標：
   - 成功率
   - P95/P99 latency
   - 4xx/5xx 比例
   - 座位重複售出數（應為 0）

## 驗收標準

- 無超賣（同 show_start_at + showtime_id + seat_id 只存在 1 張票）。
- 支付重試不重複扣款。
- 系統在壓力下無大量 500 錯誤。

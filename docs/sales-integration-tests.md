# Integration test API bán hàng (Sales / Inter 2)

Doanh giữ `OrderApiIntegrationTest` (create/list/pay/cancel…). Cường viết IT riêng cho SalesRequest, complete-at-counter, hold-expire, allocation — profile **`it-external`** (không Docker, Postgres từ `.env`).

**Nhánh:** `feature/api-sales-create-order-rules` (Doanh) / `feature/api-sales-cuong-tests` (Cường).

**Lưu ý seed:** IT tạo đơn chọn variant còn `qtyAvailable` (không hard-code `TOM-SU-05`).

---

## Map tiêu chí tracking (task 9) → IT

| Tiêu chí | Test |
|----------|------|
| Đủ hàng (lifecycle create/pay) | `OrderApiIntegrationTest.orderLifecycle_createGetUpdatePayTransitionCancelAndReceipt` |
| Thiếu hàng | `OrderAllocationApiIntegrationTest.secondOrder_whenStockShort_returns409` |
| Yêu cầu bị từ chối | `SalesRequestApiIntegrationTest.reject_setsRejectedWithReason` |
| 2 đơn tranh lô / short stock | `OrderAllocationApiIntegrationTest.secondOrder_whenStockShort_returns409` (+ unit race) |
| Hủy trả hàng giữ | `OrderApiIntegrationTest` cancel trong lifecycle |
| Hoàn tất chỉ trừ kho 1 lần | `CounterFulfillmentApiIntegrationTest.completeAtCounter_twice_isIdempotent` |

---

## Phần Doanh — đã làm

File: `hbsisms-api/src/test/java/com/hbsisms/feature/sales/OrderApiIntegrationTest.java`

```powershell
cd hbsisms-api
.\mvnw.cmd test "-Dtest=OrderApiIntegrationTest" "-DargLine=-Duser.timezone=UTC"
```

| Test method | Hành vi | Kết quả |
|-------------|---------|---------|
| `orderLifecycle_createGetUpdatePayTransitionCancelAndReceipt` | POST → get/list/put → pay → receipt → transition → cancel | pass (khi còn tồn) |
| `update_cancelledOrder_returns409` | sửa đơn đã hủy → 409 | pass |
| `list_filtersByAllocationStatus_fullExcludesIncoming` | filter `allocationStatus=full` | pass |

---

## Báo cáo Cường — đã làm

**Lệnh chạy phần Cường**

```powershell
cd hbsisms-api
.\mvnw.cmd test "-Dtest=SalesRequestApiIntegrationTest,CounterFulfillmentApiIntegrationTest,HoldExpireApiIntegrationTest,OrderAllocationApiIntegrationTest" "-DargLine=-Duser.timezone=UTC"
```

Kết quả máy local (2026-11-12): **7 tests, 0 failures** (BUILD SUCCESS) — SalesRequest **3** + Counter **1** + HoldExpire **1** + Allocation **2**. Profile `it-external`.

### SalesRequestApiIntegrationTest

| Test method | Hành vi | Kết quả |
|-------------|---------|---------|
| `flow_createListGet` | POST → list → GET | pass |
| `reject_setsRejectedWithReason` | reject + reason | pass |
| `feasibility_customerAccepted_createsOrder` | feasible + accept → ORDERED + order | pass |

### CounterFulfillmentApiIntegrationTest

| Test method | Hành vi | Kết quả |
|-------------|---------|---------|
| `completeAtCounter_twice_isIdempotent` | Paid → complete 2 lần → COMPLETED | pass |

### HoldExpireApiIntegrationTest

| Test method | Hành vi | Kết quả |
|-------------|---------|---------|
| `run_expiresUnpaidPastHold_cancelsOrder` | holdExpiresAt quá khứ → cancelled | pass |

### OrderAllocationApiIntegrationTest

| Test method | Hành vi | Kết quả |
|-------------|---------|---------|
| `secondOrder_whenStockShort_returns409` | qty quá lớn → 409 | pass |
| `allocateOutstandingAfterReceipt_holdsMoreForPaid` | Paid outstanding → allocate | pass |

---

## Task 10 — Khớp contract FE + `VITE_API_HTTP_PATHS`

Đối chiếu `hbsisms-web-internal` (`features/orders`, `requests`, `pos`, `payments`, `pricing`) với BE:

| FE | BE | Ghi chú |
|----|----|---------|
| `ordersApi` → `/orders`… | `OrderController` | khớp; path const `API_PATHS.orders` |
| `requestsApi` → `/sales-requests`… | `SalesRequestController` | khớp |
| `paymentsApi` → `/payments`… | `PaymentController` | khớp |
| `pricingApi` → `/pricing/resolve` | `PricingController` | khớp |
| `posApi` → `/pos/catalog`, `/pos/checkout` | **chưa có** | giữ mock; không gắn HTTP |

Cấu hình hybrid (`.env.local`):

```
VITE_API_MODE=mock
VITE_API_BASE_URL=http://localhost:8081/api/v1
VITE_API_HTTP_PATHS=/auth,/orders,/sales-requests,/payments,/pricing,/customers
```

Code: `src/shared/api/httpPaths.ts`, `createHybridTransport` trong `client.ts`, bootstrap `app/main.tsx`.

# Integration test API bán hàng (Sales / Inter 2)

Doanh giữ `OrderApiIntegrationTest` (create/list/pay/cancel…). Cường viết IT riêng cho SalesRequest, complete-at-counter, hold-expire, allocation — profile **`it-external`** (không Docker, Postgres từ `.env`).

**Lệnh chạy phần Cường**

```powershell
cd hbsisms-api
.\mvnw.cmd test "-Dtest=SalesRequestApiIntegrationTest,CounterFulfillmentApiIntegrationTest,HoldExpireApiIntegrationTest,OrderAllocationApiIntegrationTest" "-DargLine=-Duser.timezone=UTC"
```

Kết quả máy local (2026-11-12): **7 tests, 0 failures** (BUILD SUCCESS) — SalesRequest **3** + Counter **1** + HoldExpire **1** + Allocation **2**. Profile `it-external`.

Nhánh: `feature/api-sales-cuong-tests`.

**Lưu ý seed:** IT tạo đơn chọn variant còn `qtyAvailable` (không hard-code `TOM-SU-05`).

---

## Phần Doanh — không sửa

File: `hbsisms-api/src/test/java/com/hbsisms/feature/sales/OrderApiIntegrationTest.java`

---

## Báo cáo Cường — đã làm

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

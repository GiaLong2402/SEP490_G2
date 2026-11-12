# Unit test bán hàng (Sales / Inter 2)

File bàn giao test unit phần Sales. Doanh giữ lifecycle đơn (`SalesOrderServiceTest`, …). Cường cover FEFO hold, SalesRequest, hoàn tất quầy, hold-expire — **file class riêng**, không sửa file Doanh.

**Lệnh chạy phần Cường**

```powershell
cd hbsisms-api
.\mvnw.cmd test "-Dtest=OrderAllocationServiceTest,SalesRequestServiceTest,CounterFulfillmentServiceTest,HoldExpireServiceTest"
```

Kết quả gần nhất (2026-11-12): **BUILD SUCCESS** — OrderAllocation **9** + SalesRequest **8** + CounterFulfillment **3** + HoldExpire **2** = **22 tests, 0 failures**.

Nhánh gợi ý: `feature/api-sales-cuong-tests`.

---

## Phần Doanh (Orders lifecycle) — tham chiếu

Không đụng các file sau (tránh conflict git):

- `SalesOrderServiceTest`
- `SalesOrderStockGuardTest`
- `QuoteServiceTest` (nếu thuộc Doanh)

---

## Báo cáo Cường (Sales tạo đơn) — đã làm

### OrderAllocationServiceTest

File: `hbsisms-api/src/test/java/com/hbsisms/feature/sales/OrderAllocationServiceTest.java`

| Test method | Hành vi | Kết quả |
|-------------|---------|---------|
| `holdForOrder_fefoHoldsAvailableQtyAndSetsOutstanding` | FEFO hold + outstanding + 15m expiry | pass |
| `holdForOrder_disallowOutstanding_whenShort_throws409` | thiếu hàng → 409 + RELEASE | pass |
| `holdForOrder_contentionSkipsConflictedLot` | tranh lô → skip | pass |
| `twoOrders_onlyOneWinsLastTray` | 2 đơn 1 khay → 1 win | pass |
| `releaseForOrder_releasesNetHeld` | hủy → RELEASE | pass |
| `issueOutHeldForOrder_isIdempotent` | OUT lần 2 = 0 | pass |
| `issueOutHeldForOrder_outsHeldLots` | OUT đúng hold | pass |
| `allocateOutstandingAfterReceipt_holdsPaidOutstanding` | Paid + outstanding → hold thêm | pass |
| `releaseExpiredHolds_unpaidReleases_paidSkips` | unpaid hết hạn RELEASE; paid skip | pass |

### SalesRequestServiceTest

File: `hbsisms-api/src/test/java/com/hbsisms/feature/sales/SalesRequestServiceTest.java`

| Test method | Hành vi | Kết quả |
|-------------|---------|---------|
| `create_persistsNewRequest` | tạo YC | pass |
| `reject_requiresReasonAndSetsRejected` | từ chối + lý do | pass |
| `reject_blankReasonFails` | lý do trống → 400 | pass |
| `feasibility_customerAcceptedCreatesOrder` | khả thi + đồng ý → SO | pass |
| `feasibility_acceptedWithoutOrderFails` | đồng ý thiếu order → 400 | pass |
| `feasibility_notAccepted_doesNotCreateOrder` | khả thi không đồng ý → không SO | pass |
| `search_filtersByStatus_returnsPage` | search theo status | pass |
| `get_returnsDetail` | get by id | pass |

### CounterFulfillmentServiceTest

File: `hbsisms-api/src/test/java/com/hbsisms/feature/sales/CounterFulfillmentServiceTest.java`

| Test method | Hành vi | Kết quả |
|-------------|---------|---------|
| `completeAtCounter_issuesOutAndCompletes` | Paid → OUT + COMPLETED + save | pass |
| `completeAtCounter_secondCallIsIdempotent` | đã COMPLETED → không OUT thêm | pass |
| `completeAtCounter_unpaidFails` | chưa Paid → 409 | pass |

### HoldExpireServiceTest

File: `hbsisms-api/src/test/java/com/hbsisms/feature/sales/HoldExpireServiceTest.java`

| Test method | Hành vi | Kết quả |
|-------------|---------|---------|
| `run_processesEachExpiredOrderViaHandler` | job gọi handler từng đơn hết hạn | pass |
| `run_countsOnlyResolvedOrders` | chỉ đếm order handler trả true | pass |

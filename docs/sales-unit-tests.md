# Unit test bán hàng (Sales / Inter 2)

File bàn giao test unit phần Sales. Doanh giữ lifecycle đơn (`SalesOrderServiceTest`, …). Cường cover FEFO hold, SalesRequest, hoàn tất quầy, hold-expire — **file class riêng**, không sửa file Doanh.

**Nhánh gợi ý:** `feature/api-sales-create-order-rules` (Doanh) / `feature/api-sales-cuong-tests` (Cường).

---

## Map tiêu chí tracking (task 9) → unit

| Tiêu chí | Cover bởi |
|----------|-----------|
| Đủ hàng | `SalesOrderServiceTest.create_persistsAndConfirmsWhenWithinLimit` + `OrderAllocationServiceTest.holdForOrder_fefoHoldsAvailableQtyAndSetsOutstanding` |
| Thiếu hàng | `SalesOrderStockGuardTest.create_rejectsWhenRequestedQtyExceedsAvailable` + `OrderAllocationServiceTest.holdForOrder_disallowOutstanding_whenShort_throws409` |
| Yêu cầu bị từ chối | `SalesRequestServiceTest.reject_requiresReasonAndSetsRejected` / `reject_blankReasonFails` |
| 2 đơn tranh lô cuối | `OrderAllocationServiceTest.twoOrders_onlyOneWinsLastTray` |
| Hủy trả hàng giữ | `SalesOrderServiceTest.cancel_releasesHold` + `OrderAllocationServiceTest.releaseForOrder_releasesNetHeld` |
| Hoàn tất chỉ trừ kho 1 lần | `CounterFulfillmentServiceTest.completeAtCounter_secondCallIsIdempotent` + `OrderAllocationServiceTest.issueOutHeldForOrder_isIdempotent` |

---

## Phần Doanh (Orders lifecycle) — đã làm

**Lệnh chạy**

```powershell
cd hbsisms-api
.\mvnw.cmd test "-Dtest=SalesOrderServiceTest,SalesOrderStockGuardTest,QuoteServiceTest"
```

Kết quả gần nhất (2026-11-13): **BUILD SUCCESS** — SalesOrderService **15** + StockGuard **1** + Quote **5** (chạy cùng suite sales unit).

Không đụng các file Cường (tránh conflict git):

- `OrderAllocationServiceTest`
- `SalesRequestServiceTest`
- `CounterFulfillmentServiceTest`
- `HoldExpireServiceTest`

### SalesOrderServiceTest

File: `hbsisms-api/src/test/java/com/hbsisms/feature/sales/SalesOrderServiceTest.java`

| Test method | Hành vi | Kết quả |
|-------------|---------|---------|
| `create_persistsAndConfirmsWhenWithinLimit` | đủ hàng → CONFIRMED + hold | pass |
| `create_lockedCustomer_returns409` | KH khóa → 409 | pass |
| `create_rejectsCreditNet` | không bán nợ | pass |
| `create_walkIn_withoutCustomerId` | vãng lai → find-or-create B2C | pass |
| `createFromRequest_allowsOutstandingHold` | từ YC khả thi → allow outstanding | pass |
| `transition_invalidTarget_returns409` | chuyển sai → 409 | pass |
| `transition_preparingWhenUnpaid_returns409` | chưa Paid → 409 | pass |
| `transition_preparingWhenDebt_returns409` | DEBT → 409 | pass |
| `transition_preparingWhenNotFullyAllocated_returns409` | chưa FULL → 409 | pass |
| `transition_preparingWhenPaidAndFull_succeeds` | Paid + FULL → PREPARING | pass |
| `cancel_releasesHold` | hủy → release hold | pass |
| `cancel_paidOrder_marksRefundPending` | hủy đã trả → REFUND_PENDING | pass |
| `cancel_blankReason_returns400` | thiếu lý do → 400 | pass |
| `receipt_unpaid_returns409` | biên lai chưa Paid → 409 | pass |
| `receipt_paid_returnsSnapshotAndAuditsReprint` | Paid → snapshot + audit | pass |

### SalesOrderStockGuardTest

File: `hbsisms-api/src/test/java/com/hbsisms/feature/sales/SalesOrderStockGuardTest.java`

| Test method | Hành vi | Kết quả |
|-------------|---------|---------|
| `create_rejectsWhenRequestedQtyExceedsAvailable` | thiếu hàng → 409, không save | pass |

---

## Báo cáo Cường (Sales tạo đơn) — đã làm

**Lệnh chạy phần Cường**

```powershell
cd hbsisms-api
.\mvnw.cmd test "-Dtest=OrderAllocationServiceTest,SalesRequestServiceTest,CounterFulfillmentServiceTest,HoldExpireServiceTest"
```

Kết quả gần nhất (2026-11-12): **BUILD SUCCESS** — OrderAllocation **9** + SalesRequest **8** + CounterFulfillment **3** + HoldExpire **2** = **22 tests, 0 failures**.

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

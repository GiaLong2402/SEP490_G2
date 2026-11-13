# Unit test mua hàng (Inter 2 — task 31)

File bàn giao / báo cáo unit test luồng **mua hàng B2C**: giỏ → checkout → đơn của tôi (hủy / ownership).

**Nhánh:** `feature/api-mua-hang-tests`

**Tiêu chí tracking:** xem hàng → giỏ → checkout → TT; 409 hết hàng; double checkout 1 đơn; không xem đơn người khác.

---

## Phần Duy — đã làm + báo cáo

**Lệnh chạy**

```powershell
cd hbsisms-api
.\mvnw.cmd test "-Dtest=CartServiceTest,CheckoutServiceTest,MeOrderServiceGetMineTest,MeOrderServiceCancelTest"
```

Kết quả gần nhất: **15 tests, 0 failures** (BUILD SUCCESS) — Cart **4** + Checkout **6** + GetMine **2** + Cancel **3**.

### CartServiceTest

File: `hbsisms-api/src/test/java/com/hbsisms/feature/storefront/CartServiceTest.java`

| Test method | Hành vi | Kết quả |
|-------------|---------|---------|
| `addItem_createsLine_andResolvesListPrice` | thêm dòng → giá LIST + totalQty | pass |
| `addItem_unpublishedProduct_rejects` | SP chưa publish → 400 | pass |
| `updateItemQty_belowOne_rejects` | qty &lt; 1 → 400 | pass |
| `getOrCreate_newSession_persistsEmptyCart` | session mới → lưu giỏ trống (F5 persist) | pass |

### CheckoutServiceTest

File: `hbsisms-api/src/test/java/com/hbsisms/feature/storefront/CheckoutServiceTest.java`

| Test method | Hành vi | Kết quả |
|-------------|---------|---------|
| `checkout_missingIdempotencyKey_rejects` | thiếu Idempotency-Key → 400 | pass |
| `checkout_insufficientStock_conflictsWithShortages` | thiếu tồn → 409 + shortages | pass |
| `checkout_sameIdempotencyKey_returnsExistingWithoutCreating` | key trùng → cùng đơn, không tạo mới | pass |
| `checkout_success_holdsStock_createsPayos_andSavesIdempotency` | đủ hàng → hold FEFO + PayOS + lưu idem | pass |
| `checkout_lockedCustomer_conflicts` | KH khóa → 409 | pass |
| `checkout_pastRequestedDate_rejects` | ngày nhận quá khứ → 400 | pass |

### MeOrderServiceGetMineTest

File: `hbsisms-api/src/test/java/com/hbsisms/feature/storefront/MeOrderServiceGetMineTest.java`

| Test method | Hành vi | Kết quả |
|-------------|---------|---------|
| `getMine_foreignOrderCode_returns404` | mã đơn không thuộc KH → 404 | pass |
| `getMine_ownedOrder_returnsDetail` | đơn của mình → detail | pass |

### MeOrderServiceCancelTest

File: `hbsisms-api/src/test/java/com/hbsisms/feature/storefront/MeOrderServiceCancelTest.java`

| Test method | Hành vi | Kết quả |
|-------------|---------|---------|
| `cancelMine_pendingPayos_callsCancelLink` | hủy UNPAID → release hold + PayOS CANCELLED + paymentStatus UNPAID | pass |
| `cancelMine_payosCancelFails_stillCancelsLocally` | PayOS cancel fail vẫn hủy local | pass |
| `cancelMine_nonPayosPending_doesNotCallPayos` | payment CASH không gọi PayOS | pass |

---

## Map tiêu chí → unit

| Tiêu chí | Cover bởi |
|----------|-----------|
| Giỏ persist / add | `CartServiceTest` |
| Checkout hết hàng 409 | `checkout_insufficientStock_conflictsWithShortages` |
| Double checkout 1 đơn | `checkout_sameIdempotencyKey_returnsExistingWithoutCreating` |
| Không xem đơn người khác | `getMine_foreignOrderCode_returns404` |
| Hủy chờ TT | `MeOrderServiceCancelTest` |

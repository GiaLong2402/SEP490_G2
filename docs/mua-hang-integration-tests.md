# Integration test mua hàng (Inter 2 — task 31)

File bàn giao IT luồng **mua hàng B2C** (catalog → giỏ → checkout → `/me/orders`).

**Nhánh:** `feature/api-mua-hang-tests`

**Yêu cầu chạy:** Postgres từ `.env` (profile `it-external`). PayOS HTTP **mock** (`@MockitoBean PayosClient`) — không gọi sandbox thật.

---

## Phần Duy — đã làm + báo cáo

### StorefrontPurchaseApiIntegrationTest (mới)

File: `hbsisms-api/src/test/java/com/hbsisms/feature/storefront/StorefrontPurchaseApiIntegrationTest.java`

```powershell
cd hbsisms-api
.\mvnw.cmd test "-Dtest=StorefrontPurchaseApiIntegrationTest"
```

Kết quả gần nhất: **4 tests, 0 failures** (BUILD SUCCESS).

| Test method | Hành vi | Kết quả |
|-------------|---------|---------|
| `purchaseFlow_catalogCartCheckout_andListMine` | GET `/products` → add giỏ → POST `/checkout` → GET `/me/orders` + detail | pass |
| `checkout_insufficientStock_returns409WithShortages` | qty vượt tồn → 409 + `shortages[]` | pass |
| `checkout_sameIdempotencyKey_returnsSameOrderOnlyOnce` | cùng Idempotency-Key 2 lần → 1 đơn (count không tăng) | pass |
| `meOrders_foreignCode_returns404` | KH khác xem mã đơn → 404 | pass |

### CartApiIntegrationTest (đã có — guest giỏ)

File: `hbsisms-api/src/test/java/com/hbsisms/feature/storefront/CartApiIntegrationTest.java`

```powershell
cd hbsisms-api
.\mvnw.cmd test "-Dtest=CartApiIntegrationTest"
```

| Test method | Hành vi | Kết quả |
|-------------|---------|---------|
| `guestCart_addUpdateRemove_withPriceResolver` | add / update qty / remove; giá qua PriceResolver | pass (khi có seed TOM-SU-500) |
| `guestCart_qtyZero_returns400` | qty 0 → 400 | pass |

---

## Map tiêu chí tracking → IT

| Tiêu chí | Test |
|----------|------|
| Xem hàng → giỏ → checkout → đơn | `purchaseFlow_catalogCartCheckout_andListMine` |
| 409 hết hàng | `checkout_insufficientStock_returns409WithShortages` |
| Double checkout 1 đơn | `checkout_sameIdempotencyKey_returnsSameOrderOnlyOnce` |
| Không xem đơn người khác | `meOrders_foreignCode_returns404` |

---

## Đóng tracking

Đủ **2 báo cáo** (unit + IT) mới đánh dấu xong task 31 trên Excel:

1. `docs/mua-hang-unit-tests.md` ✅  
2. `docs/mua-hang-integration-tests.md` ✅  

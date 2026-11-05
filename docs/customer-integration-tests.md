# Integration test API khách hàng (task 24)

File bàn giao IT module khách. **Khôi** (address + giá/PriceResolver) điền trước; **Duy** (CRUD/B2C nếu có IT) append bảng riêng vào file này, không tạo file docs khác.

**Yêu cầu chạy:** Docker Desktop đang bật (Testcontainers Postgres).

---

## Phần Khôi — đã làm (address + giá B2B + PriceResolver)

File: `hbsisms-api/src/test/java/com/hbsisms/feature/customer/CustomerAddressPricingApiIntegrationTest.java`

```powershell
cd hbsisms-api
.\mvnw.cmd test "-Dtest=CustomerAddressPricingApiIntegrationTest"
```

Kết quả gần nhất: **11 tests, 0 failures** (BUILD SUCCESS).

| Test method | Hành vi | Kết quả |
|-------------|---------|---------|
| `address_createDefault_clearsPreviousAndMarksInRadius` | tạo default mới → clear default cũ; ≤8km → inRadius | pass |
| `address_create_outsideRadius_setsFlagFalse` | distance &gt; 8 → inDeliveryRadius=false | pass |
| `address_updateAndDelete_roundTrip` | PUT rồi DELETE; list không còn id | pass |
| `address_unknownCustomer_returns404` | customer không tồn tại → 404 | pass |
| `pricing_guest_usesListPrice` | guest → priceBasis=list | pass |
| `pricing_withVolumeTier_usesTierWhenNoSpecial` | có bậc SL khớp, không giá riêng → tier | pass |
| `price_proposeB2c_returns400` | B2C đề xuất giá → 400 | pass |
| `price_proposeApprove_resolveUsesCustomerPrice` | propose pending vẫn list; approve → customer | pass |
| `price_reject_keepsListPrice` | reject đề xuất lần đầu → list | pass |
| `price_effectiveWindow_outsideDoesNotApply` | effectiveTo quá khứ / effectiveFrom tương lai → không CUSTOMER | pass |
| `price_propose_invalidWindowOrDuplicatePending_returns4xx` | to &lt; from → 400; đề xuất trùng pending → 409 | pass |

---

## Checklist + báo cáo Duy — tự làm rồi append vào đây

**Việc gợi ý:** IT CRUD khách B2B/B2C (create/lock, credit…) nếu còn gap ngoài unit task 23.

| Test method | Hành vi | Pass/Fail |
|-------------|---------|-----------|
| *(Duy điền)* | | |

---

## Đóng tracking

Đủ báo cáo Khôi (+ Duy nếu sheet yêu cầu) trong file này mới đánh dấu xong IT khách trên Excel.

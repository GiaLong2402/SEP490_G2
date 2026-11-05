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

## Checklist + báo cáo Duy — đã làm (CRUD / B2C)

**Phạm vi:** IT `GET/POST/PUT /customers` + `PATCH /customers/{id}/active` (create B2B/B2C, credit rules, lock). Address/giá nằm phần Khôi.

Nhánh: `feature/api-customer-integration-tests`.

File: `hbsisms-api/src/test/java/com/hbsisms/feature/customer/CustomerApiIntegrationTest.java`

```powershell
cd hbsisms-api
.\mvnw.cmd test "-Dtest=CustomerApiIntegrationTest"
```

Kết quả gần nhất: **9 tests, 0 failures** (BUILD SUCCESS).

| Test method | Hành vi | Kết quả |
|-------------|---------|---------|
| `create_b2c_assignsKhCodeAndZeroDebt` | POST B2C → 201; mã `KH-…`; debt = 0 | pass |
| `create_b2b_keepsCreditTaxNetDays` | POST B2B → 201; credit/netDays/tax + mã `KH-B…` | pass |
| `create_b2c_withCreditLimit_returns400` | B2C + creditLimit → 400 | pass |
| `update_b2bToB2c_withDebt_returns409` | B2B còn nợ (repo) → PUT B2C → 409 | pass |
| `create_invalidPhone_returns400` | phone sai → 400 | pass |
| `create_b2b_invalidTaxCode_returns400` | taxCode sai → 400 | pass |
| `setActive_lockAndUnlock` | PATCH lock rồi unlock; GET khớp | pass |
| `get_unknown_returns404` | GET id không có → 404 | pass |
| `list_filtersByActive` | GET `?q&isActive=false` sau lock | pass |

---

## Đóng tracking

Đủ **2 báo cáo** trong file này mới đánh dấu xong IT khách (task 24) trên Excel:

1. Khôi — mục trong file này ✅  
2. Duy — mục trong file này ✅  

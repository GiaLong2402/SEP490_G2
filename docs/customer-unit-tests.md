# Unit test service khách hàng (task 23)

File bàn giao / báo cáo unit test module khách hàng. Sheet tracking: **Duy** (CRUD/B2C) + **Khôi** (address / PriceResolver). Tách class test theo owner.

**Nhánh gợi ý:** `feature/api-customer-unit-tests`

---

## Phần Khôi (address + PriceResolver) — đã làm + báo cáo

**Mục tiêu:** địa chỉ mặc định (clear default), bán kính giao; thứ tự ưu tiên giá trong PriceResolver (giá riêng → bậc SL → niêm yết). Cover đủ nhánh của `CustomerAddressService` / `PriceResolver`.

**Lệnh chạy phần Khôi**

```powershell
cd hbsisms-api
.\mvnw.cmd test -Dtest=CustomerAddressServiceTest,PriceResolverTest
```

Kết quả gần nhất: **27 tests, 0 failures** (BUILD SUCCESS) — Address **17** + PriceResolver **10**.

### CustomerAddressServiceTest

File: `hbsisms-api/src/test/java/com/hbsisms/feature/customer/CustomerAddressServiceTest.java`

| Test method | Hành vi | Kết quả |
|-------------|---------|---------|
| `list_returnsAddressesOrderedByRepository` | list theo thứ tự repo (default trước) | pass |
| `list_returnsEmptyWhenCustomerHasNoAddresses` | KH không có địa chỉ → [] | pass |
| `list_rejectsUnknownCustomer` | customer không có → 404 | pass |
| `create_clearsPreviousDefaultWhenMarkingDefault` | tạo default → clearDefault + inRadius ≤ 8km | pass |
| `create_doesNotClearDefaultWhenNotDefault` | không default → không clear; > 8km → ngoài bán kính | pass |
| `create_defaultsKindToShippingWhenNull` | kind null → SHIPPING; trim line1 | pass |
| `create_persistsBillingKindAndTrimsBlankOptionalFields` | BILLING; blank → null; lat/lng null; distance 0 trong bán kính | pass |
| `create_rejectsUnknownCustomer` | create KH không có → 404 | pass |
| `update_clearsDefaultOnlyWhenSwitchingToDefault` | đổi sang default → clearDefault | pass |
| `update_skipsClearDefaultWhenAlreadyDefault` | đã default → không clear lại | pass |
| `update_canUnsetDefaultWithoutClearingOthers` | bỏ default → không clearDefault | pass |
| `update_rejectsUnknownAddress` | address không thuộc KH → 404 | pass |
| `update_rejectsUnknownCustomer` | update KH không có → 404 | pass |
| `delete_removesAddress` | xóa địa chỉ | pass |
| `delete_rejectsUnknownAddress` | xóa id sai → 404 | pass |
| `delete_rejectsUnknownCustomer` | delete KH không có → 404 | pass |
| `isInDeliveryRadius_boundaryAtEightKm` | 0 / 8 / 8.01 / null | pass |

### PriceResolverTest

File: `hbsisms-api/src/test/java/com/hbsisms/feature/customer/pricing/PriceResolverTest.java`

| Test method | Hành vi | Kết quả |
|-------------|---------|---------|
| `prefersApprovedCustomerPriceWhenInEffect` | có giá riêng hiệu lực → CUSTOMER, bỏ tier | pass |
| `usesHighestMatchingVolumeTierWhenNoSpecial` | không giá riêng → TIER cao nhất khớp SL | pass |
| `fallsBackToListPrice` | guest không tier → LIST | pass |
| `guestWithMatchingTier_usesTierWithoutLookingUpSpecial` | guest có tier → TIER, không gọi special | pass |
| `customerWithoutSpecial_fallsBackToHighestTier` | có KH nhưng không special → TIER | pass |
| `customerWithoutSpecialOrTier_fallsBackToList` | có KH, không special/tier → LIST | pass |
| `rejectsUnknownVariant` | variant không có → 400 | pass |
| `rejectsNonPositiveQty` | qty = 0 → 400 | pass |
| `rejectsNegativeQty` | qty &lt; 0 → 400 | pass |
| `rejectsNullVariantId` | variantId null → 400 | pass |

**Ngoài scope unit PriceResolver:** cửa sổ `effectiveFrom` / `effectiveTo` nằm trong query `CustomerPriceRepository.findEffectiveSpecial` (IT/repo), service chỉ nhận Optional đã lọc.

---

## Checklist Duy (CRUD / B2C) — tự làm + báo cáo riêng

**Mục tiêu tracking:** quy tắc B2B/B2C (credit, mã KH, khóa/mở…).

**File đã có sẵn (có thể mở rộng):**

- `CustomerServiceTest.java`
- `CustomerTypeTest.java`
- `CustomerCodeGeneratorTest.java`

**Case tối thiểu cần xác nhận / bổ sung**

1. Create B2C gán mã `KH-…`, debt = 0  
2. Create B2B giữ creditLimit / netDays / tax  
3. B2C không được set creditLimit → 400  
4. Đổi B2B → B2C khi còn nợ → 409  
5. Phone / taxCode không hợp lệ → 400  
6. setActive lock/unlock  

**Chạy test (ví dụ)**

```powershell
cd hbsisms-api
.\mvnw.cmd test -Dtest=CustomerServiceTest,CustomerTypeTest,CustomerCodeGeneratorTest
```

### Báo cáo Duy — (chưa điền)

| Test method | Hành vi | Kết quả |
|-------------|---------|---------|
| … | … | … |

---

## Đóng việc trên tracking

Đủ **2 báo cáo** mới đánh dấu xong task 23 trên Excel:

1. Khôi — mục trong file này ✅  
2. Duy — báo cáo riêng (checklist phía trên)  

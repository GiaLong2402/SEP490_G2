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

## Checklist Duy (CRUD / B2C) — đã làm + báo cáo

**Mục tiêu tracking (#23):** quy tắc B2B/B2C (credit, mã KH, khóa/mở…). Cover `CustomerService` + helper `CustomerType` / `CustomerCodeGenerator`.

Nhánh: `feature/api-customer-unit-tests`.

**Case tối thiểu → test method (Pass)**

| # | Case | Test method | Kết quả |
|---|------|-------------|---------|
| 1 | Create B2C gán mã `KH-…`, debt = 0 | `createB2cAssignsCodeAndZeroDebt` | pass |
| 2 | Create B2B giữ creditLimit / netDays / tax | `createB2bKeepsCreditTerms` | pass |
| 3 | B2C không được set creditLimit → 400 | `createB2cRejectsCreditLimit` (+ tax/netDays) | pass |
| 4 | Đổi B2B → B2C khi còn nợ → 409 | `updateB2bToB2cWithDebtConflicts` | pass |
| 5 | Phone / taxCode không hợp lệ → 400 | `createRejectsInvalidPhone`, `createB2bRejectsInvalidTaxCode` | pass |
| 6 | setActive lock/unlock | `setActiveLocksCustomer`, `setActiveUnlocksCustomer` | pass |

**Chạy test phần Duy**

```powershell
cd hbsisms-api
.\mvnw.cmd test "-Dtest=CustomerServiceTest,CustomerTypeTest,CustomerCodeGeneratorTest"
```

Kết quả gần nhất: **25 tests, 0 failures** (BUILD SUCCESS) — Service **19** + Type **3** + CodeGenerator **3**.

### CustomerServiceTest

File: `hbsisms-api/src/test/java/com/hbsisms/feature/customer/CustomerServiceTest.java`

| Test method | Hành vi | Kết quả |
|-------------|---------|---------|
| `searchMapsPageAndOrderCountZero` | search map page + sort name | pass |
| `getDetailReturnsAddressesAndEmptyRecentOrders` | detail + addresses/prices rỗng | pass |
| `getDetailNotFound` | id không có → 404 | pass |
| `createB2cAssignsCodeAndZeroDebt` | B2C mã `KH-…`, debt = 0 | pass |
| `createB2bKeepsCreditTerms` | B2B giữ credit / netDays / tax + mã `KH-B…` | pass |
| `createB2cRejectsCreditLimit` | B2C + creditLimit → 400 | pass |
| `createB2cRejectsTaxCode` | B2C + MST → 400 | pass |
| `createB2cRejectsNetDays` | B2C + netDays ≠ 0 → 400 | pass |
| `createRejectsBlankName` | tên trống → 400 | pass |
| `createRejectsNullCustomerType` | type null → 400 | pass |
| `updateB2bToB2cWithDebtConflicts` | B2B→B2C còn nợ → 409 | pass |
| `updateB2bToB2cWithZeroDebtSucceeds` | B2B→B2C hết nợ → clear credit/tax | pass |
| `createRejectsInvalidPhone` | phone sai → 400 | pass |
| `createNormalizesPlus84Phone` | `+84…` → `0…` | pass |
| `createB2bRejectsInvalidTaxCode` | taxCode không phải 10–13 số → 400 | pass |
| `createB2bRejectsNegativeCreditLimit` | creditLimit âm → 400 | pass |
| `setActiveLocksCustomer` | lock `isActive=false` | pass |
| `setActiveUnlocksCustomer` | unlock `isActive=true` | pass |
| `setActiveNotFound` | lock id không có → 404 | pass |

### CustomerTypeTest

File: `hbsisms-api/src/test/java/com/hbsisms/feature/customer/CustomerTypeTest.java`

| Test method | Hành vi | Kết quả |
|-------------|---------|---------|
| `codeIsLowercase` | `b2c` / `b2b` | pass |
| `fromCodeAcceptsLowercase` | parse case-insensitive | pass |
| `fromCodeRejectsBlankAndUnknown` | null / blank / unknown → lỗi | pass |

### CustomerCodeGeneratorTest

File: `hbsisms-api/src/test/java/com/hbsisms/feature/customer/CustomerCodeGeneratorTest.java`

| Test method | Hành vi | Kết quả |
|-------------|---------|---------|
| `nextB2cStartsAt001` | rỗng → `KH-001` | pass |
| `nextB2cIgnoresKhoiAndB2bCodes` | bỏ mã KHOI / B2B khi sinh B2C | pass |
| `nextB2bUsesKhBPrefix` | B2B → `KH-B…` | pass |

---

## Đóng việc trên tracking

Đủ **2 báo cáo** mới đánh dấu xong task 23 trên Excel:

1. Khôi — mục trong file này ✅  
2. Duy — mục trong file này ✅  

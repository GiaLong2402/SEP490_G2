# Integration test API danh mục sản phẩm

File bàn giao sau khi Doanh hoàn thành IT phần Product write + Variant/Tier. Cường đọc checklist bên dưới, tự viết IT Category + Product list/search (file class riêng), rồi append bảng kết quả vào cùng file này.

**Yêu cầu chạy:** Docker Desktop đang bật (Testcontainers Postgres). Nên dùng JDK 17 nếu máy ít RAM (JDK 25 dễ OOM khi start Spring + container).

```powershell
cd hbsisms-api
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"   # nếu cần
.\mvnw.cmd test "-Dtest=ProductApiIntegrationTest,VariantApiIntegrationTest"
```

Kết quả Doanh gần nhất: **21 tests, 0 failures** (BUILD SUCCESS) — ProductApiIntegrationTest 10 + VariantApiIntegrationTest 11.

Nhánh gợi ý: `feature/api-catalog-it`.

---

## Phần Doanh (Product write / Variant / Tier) — đã làm

### ProductApiIntegrationTest

File: `hbsisms-api/src/test/java/com/hbsisms/feature/catalog/ProductApiIntegrationTest.java`

| Test method | Hành vi | Kết quả |
|-------------|---------|---------|
| `flow_createGetUpdate` | POST create → GET → PUT (code/name/image/gallery) → GET xác nhận; giữ slug | pass |
| `publish_withoutActiveVariant_returns400` | publish khi chưa có variant active → 400 | pass |
| `publish_withActiveVariant_succeeds` | tạo variant active rồi publish → 200 | pass |
| `create_duplicateCode_returns409` | trùng mã (ignore case) → 409 | pass |
| `create_withoutToken_returns401` | không Bearer → 401 | pass |
| `create_withoutProductsPermission_returns403` | user thiếu quyền `products` → 403 | pass |
| `update_unknownId_returns404` | PUT id không tồn tại → 404 | pass |
| `create_unknownCategory_returns404` | categoryId sai → 404 | pass |
| `create_blankCode_returns400` | code trống → 400 | pass |
| `create_blankName_returns400` | name trống → 400 | pass |

### VariantApiIntegrationTest

File: `hbsisms-api/src/test/java/com/hbsisms/feature/catalog/VariantApiIntegrationTest.java`

| Test method | Hành vi | Kết quả |
|-------------|---------|---------|
| `create_withTiers_visibleOnProductGet` | POST variant + tiers → GET product thấy SKU/tier đã sort | pass |
| `update_replacesTiersAndFields` | PUT đổi SKU/giá/active + replace tiers | pass |
| `create_duplicateSku_returns409` | SKU trùng (ignore case) → 409 | pass |
| `create_unknownProduct_returns404` | productId sai → 404 | pass |
| `update_unknownVariant_returns404` | variantId sai → 404 | pass |
| `create_invalidListPrice_returns400` | listPrice = 0 → 400 | pass |
| `create_tierPriceAboveList_returns400` | giá bậc > niêm yết → 400 | pass |
| `create_duplicateTierMinQty_returns400` | trùng minQty → 400 | pass |
| `create_increasingTierPrices_returns400` | giá bậc sau tăng → 400 | pass |
| `create_withoutToken_returns401` | không Bearer → 401 | pass |
| `create_withoutProductsPermission_returns403` | thiếu `products` → 403 | pass |

Doanh **không** làm IT Category CRUD / Product list-search của Cường.

---

## Checklist Cường (Category / Product read) — tự làm + append báo cáo

**Mục tiêu:** MockMvc + Testcontainers cho category và product read.

### File gợi ý

| File | Việc |
|------|------|
| `CategoryApiIntegrationTest.java` (**mới**) | CRUD category: tạo → list → sửa → 409 trùng code → 403 thiếu `products` → cycle parent |
| Product list/search IT (class riêng của Cường) | `GET /products` filter `q` / `categoryId` (kèm descendants) / paging |

Không sửa `ProductApiIntegrationTest` / `VariantApiIntegrationTest` của Doanh.

### Case tối thiểu Category

1. POST create → GET list thấy parentName / productCount
2. PUT update name/sort/active
3. 409 trùng code
4. 409 parent = self / descendant
5. 404 parent / category thiếu
6. 401 / 403 giống Doanh

### Case tối thiểu Product read

1. GET list không filter
2. GET list `q` search code/name
3. GET list `categoryId` gồm descendants
4. GET detail `/products/{id}` (có thể assert trên seed demo)

### Chạy test (ví dụ)

```powershell
cd hbsisms-api
.\mvnw.cmd test "-Dtest=CategoryApiIntegrationTest"
```

### Format báo cáo Cường (điền sau khi pass)

| Test method | Hành vi | Pass/Fail |
|-------------|---------|-----------|
| … | … | … |

---

## Đóng việc

Đủ **2 phần báo cáo** trong file này:

1. Doanh — mục trên (21 pass)
2. Cường — bảng Category + Product read

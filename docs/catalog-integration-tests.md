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

**Mục tiêu:** MockMvc IT Category + Product read **không Docker** — profile `it-external` + Postgres từ `.env` (`ddl-auto=update`, không `create-drop`).

Không sửa `ProductApiIntegrationTest` / `VariantApiIntegrationTest` của Doanh.

### Báo cáo Cường (Category / Product read) — đã làm

**Lệnh chạy**

```powershell
cd hbsisms-api
.\mvnw.cmd test "-Dtest=CategoryApiIntegrationTest,ProductReadApiIntegrationTest" "-DargLine=-Duser.timezone=UTC"
```

Nhánh: `feature/api-catalog-cuong-tests`.

**Kết quả máy local (2026-11-06):** **13 tests, 0 failures** (BUILD SUCCESS) — Category **8** + Product read **5**. Profile `it-external`, DB remote từ `.env`.

#### CategoryApiIntegrationTest

File: `hbsisms-api/src/test/java/com/hbsisms/feature/catalog/CategoryApiIntegrationTest.java`

| Test method | Hành vi | Pass/Fail |
|-------------|---------|-----------|
| `flow_createListUpdate_showsParentNameAndProductCount` | POST create → GET list parentName/productCount → PUT update | pass |
| `create_duplicateCode_returns409` | trùng mã (ignore case) → 409 | pass |
| `update_selfAsParent_returns409` | parent = self → 409 | pass |
| `update_descendantAsParent_returns409` | parent = con → 409 | pass |
| `create_unknownParent_returns404` | parentId sai → 404 | pass |
| `update_unknownCategory_returns404` | id sai → 404 | pass |
| `create_withoutToken_returns401` | không Bearer → 401 | pass |
| `list_withoutProductsPermission_returns403` | thiếu `products` → 403 | pass |

#### ProductReadApiIntegrationTest

File: `hbsisms-api/src/test/java/com/hbsisms/feature/catalog/ProductReadApiIntegrationTest.java`

| Test method | Hành vi | Pass/Fail |
|-------------|---------|-----------|
| `list_withoutFilter_returnsPage` | GET `/products` page=1 size=15 | pass |
| `list_q_filtersByCodeOrName` | GET list `q` theo code/name | pass |
| `list_categoryId_includesDescendants` | `categoryId` gồm self + descendants | pass |
| `get_detail_returnsProduct` | GET `/products/{id}` | pass |
| `get_unknownId_returns404` | id không có → 404 | pass |

---

## Đóng việc

Đủ **2 phần báo cáo** trong file này:

1. Doanh — mục trên (21 pass, Testcontainers)
2. Cường — Category + Product read (**13 pass**, profile `it-external`, không Docker)

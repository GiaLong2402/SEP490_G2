# Unit test danh mục sản phẩm (Product / Variant / Category)

File bàn giao sau khi Doanh hoàn thành unit test phần Product write + Variant/Tier. Cường đọc checklist bên dưới, tự viết/bổ sung test phần mình (Category + Product read), rồi điền bảng kết quả vào mục Cường.

Phân công Excel (`380-Tracking-Task-Phan-cong.xlsx`): **Danh mục = Cường + Doanh**.

**Lệnh chạy phần Doanh**

```powershell
cd hbsisms-api
.\mvnw.cmd test "-Dtest=ProductServiceTest,VariantServiceTest"
```

Kết quả gần nhất: **42 tests, 0 failures** (BUILD SUCCESS) — ProductServiceTest 22 + VariantServiceTest 20.

---

## Phần Doanh (Product write / Variant / Tier) — đã làm

### ProductServiceTest

File: `hbsisms-api/src/test/java/com/hbsisms/feature/catalog/ProductServiceTest.java`

Chỉ cover `create` / `update` (publish, slug, image/gallery). **Không** cover `list` / `get` — thuộc Cường.

| Test method | Hành vi | Kết quả |
|-------------|---------|---------|
| `create_trimsAndPersistsWithCategoryAndGallery` | trim field, gắn category, slugify, gallery bỏ blank | pass |
| `create_rejectsBlankCode` | code trống → 400, không save | pass |
| `create_rejectsBlankName` | name trống → 400 | pass |
| `create_rejectsDuplicateCode` | trùng mã → 409 | pass |
| `create_rejectsPublishWithoutActiveVariant` | publish khi tạo (chưa có variant) → 400 | pass |
| `create_rejectsMissingCategory` | categoryId không tồn tại → 404 | pass |
| `create_allowsNullCategory` | categoryId=null OK | pass |
| `create_rejectsImageUrlTooLong` | URL ảnh > 500 → 400 | pass |
| `create_rejectsGalleryUrlTooLong` | URL gallery > 500 → 400 | pass |
| `create_appendsSuffixWhenSlugTaken` | slug trùng → `tom-2`, `tom-3`… | pass |
| `create_rejectsWhenSlugifyFails` | tên không slugify được → 400 | pass |
| `create_persistsInactiveWhenRequested` | `isActive=false` được lưu | pass |
| `update_notFoundWhenProductMissing` | id không có → 404 | pass |
| `update_keepsExistingSlug` | có slug sẵn → không regenerate | pass |
| `update_generatesSlugWhenMissing` | slug trống → tạo mới | pass |
| `update_rejectsDuplicateCodeOnOtherId` | mã trùng sản phẩm khác → 409 | pass |
| `update_allowsSameCodeAsSelf` | giữ nguyên mã của chính nó → OK | pass |
| `update_rejectsPublishWithoutActiveVariant` | publish khi chưa có variant active → 400 | pass |
| `update_allowsPublishWhenActiveVariantExists` | có variant active → publish OK | pass |
| `update_nullGalleryKeepsExisting` | gallery=null → giữ gallery cũ | pass |
| `update_emptyGalleryClearsExisting` | gallery=[] → xóa gallery | pass |
| `update_rejectsMissingCategory` | categoryId không tồn tại → 404 | pass |

### VariantServiceTest

File: `hbsisms-api/src/test/java/com/hbsisms/feature/catalog/VariantServiceTest.java`

| Test method | Hành vi | Kết quả |
|-------------|---------|---------|
| `create_notFoundWhenProductMissing` | product không có → 404 | pass |
| `create_persistsVariantAndReplacesTiers` | lưu SKU/giá/pack; sort tier; delete→flush→save | pass |
| `create_rejectsBlankSku` | SKU trống → 400 | pass |
| `create_rejectsBlankPackLabel` | packLabel trống → 400 | pass |
| `create_rejectsInvalidListPrice` | null / ≤0 → 400 | pass |
| `create_rejectsInvalidPackPerCase` | null / <1 / > Short.MAX → 400 | pass |
| `create_rejectsDuplicateSku` | SKU trùng → 409 | pass |
| `create_rejectsNegativeWeight` | unitWeightG < 0 → 400 | pass |
| `create_allowsNullWeightAndEmptyTiers` | weight null OK; tiers null → chỉ delete+flush | pass |
| `update_notFoundWhenVariantMissing` | variant không có → 404 | pass |
| `update_persistsAndReplacesTiers` | cập nhật field + replace tiers | pass |
| `update_rejectsDuplicateSkuOnOtherId` | SKU trùng variant khác → 409 | pass |
| `update_allowsSameSkuAsSelf` | giữ SKU của chính nó → OK | pass |
| `replaceTiers_rejectsNonPositiveMinQty` | minQty null/≤0 → 400 | pass |
| `replaceTiers_rejectsDuplicateMinQty` | trùng số khay → 400 | pass |
| `replaceTiers_rejectsInvalidUnitPrice` | unitPrice null/≤0 → 400 | pass |
| `replaceTiers_rejectsPriceAboveListPrice` | giá bậc > listPrice → 400 | pass |
| `replaceTiers_rejectsIncreasingPricesAcrossTiers` | giá bậc sau tăng → 400 | pass |
| `replaceTiers_allowsEqualConsecutivePrices` | giá bằng bậc trước → OK | pass |
| `replaceTiers_emptyListOnlyDeletes` | tiers=[] → delete+flush, không insert | pass |

Doanh **không** làm unit test Category / Product list-get của Cường.

---

## Checklist Cường (Category / Product read) — tự làm + cập nhật báo cáo

**Mục tiêu tracking:** Category CRUD đủ rule; Product list/search/detail đúng filter category tree + search LIKE.

Cường **không đụng** `ProductServiceTest` / `VariantServiceTest` của Doanh (tránh conflict git).

### File gợi ý

| File | Việc |
|------|------|
| `CategoryServiceTest.java` | Mở rộng case còn thiếu (đã có create/update cycle/list cơ bản) |
| `ProductServiceReadTest.java` (**mới**) | Unit test `ProductService.list` / `get` |
| `CategoryDtoJacksonTest.java` | Giữ nguyên — đã cover `isActive` Jackson |

### Case tối thiểu Category (bổ sung vào CategoryServiceTest)

1. create: blank code → 400 `"Vui lòng nhập mã loại"`
2. create: blank name → 400
3. create: happy với parent hợp lệ (resolve parent + set)
4. create: `isActive=false` được lưu
5. update: category không tồn tại → 404
6. update: duplicate code khác id → 409; cùng code self → OK
7. update: blank code/name → 400
8. cycle sâu hơn (grandchild làm parent của root) nếu chưa cover đủ

### Case tối thiểu Product read (ProductServiceReadTest)

1. `list` không filter → gọi repo với sort `name` asc, `id` asc
2. `list` `q` blank/whitespace → bỏ qua predicate search
3. `list` `q` có ký tự LIKE → escape (`Strings.escapeLike`)
4. `list` `categoryId` → gồm **self + descendants** (cây lồng root→child→grandchild)
5. `get` found → `ProductResponse` + variants/tiers/available qty (stub repo)
6. `get` missing → 404 `"Không tìm thấy sản phẩm"`
7. `get` / `list` product không variant → `variants` rỗng

Pattern: `@ExtendWith(MockitoExtension.class)`, AssertJ + `ApiException`, giống `CategoryServiceTest` / `StockServiceTest`.

### Chạy test (ví dụ)

```powershell
cd hbsisms-api
.\mvnw.cmd test "-Dtest=CategoryServiceTest,ProductServiceReadTest,CategoryDtoJacksonTest"
```

### Format báo cáo Cường (điền vào bảng dưới sau khi pass)

| Test method | Hành vi | Pass/Fail |
|-------------|---------|-----------|
| … | … | … |

*(Cường thay dòng `…` bằng danh sách method thật + kết quả Maven.)*

Nhánh gợi ý: theo GIT.md (`feature/api-catalog-…` / test catalog).

---

## Đóng việc trên tracking

Đủ **2 phần báo cáo** mới đánh dấu xong unit test danh mục trên Excel:

1. Doanh — mục trong file này (ProductServiceTest + VariantServiceTest, 42 pass)
2. Cường — bảng kết quả Category + Product read ở trên

Task IT catalog (nếu có trên Tracking) làm sau, không nằm trong file này.

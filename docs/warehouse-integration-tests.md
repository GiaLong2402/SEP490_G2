# Integration test API kho

File bàn giao phần IT kho. **Long** điền kết quả trước; **Duy** / **Khôi** sau khi làm IT phần mình thì **append bảng báo cáo vào cùng file này** (PR riêng), không tạo file docs khác.

**Yêu cầu chạy:** Docker Desktop đang bật (Testcontainers Postgres).

```powershell
cd hbsisms-api
.\mvnw.cmd test -Dtest=WarehouseApiIntegrationTest
```

Kết quả Long gần nhất: **10 tests, 0 failures** (BUILD SUCCESS).

---

## Phần Long — đã làm

File: `hbsisms-api/src/test/java/com/hbsisms/feature/inventory/WarehouseApiIntegrationTest.java`

| Test method | Hành vi | Kết quả |
|-------------|---------|---------|
| `flow_receiveViewMoveAdjust` | receive → GET lots → move → adjust; ledger/DB khớp | pass |
| `view_lots_afterReceive_showsQty` | GET `/inventory/lots` sau receive đúng qty/SKU | pass |
| `concurrent_twoAdjusts_neverGoNegative` | 2× adjust −N song song; 1×204 + 1×409; qty cuối = 0 | pass |
| `concurrent_twoReceives_respectCapacity` | 2× receive 8 vào vị trí capacity 10; used ≤ 10 | pass |
| `receive_exceedsLocationCapacity_returns409` | vượt sức chứa vị trí → 409 | pass |
| `receive_exceedsWarehouseCapacity_returns409` | vượt sức chứa kho → 409 | pass |
| `receive_invalidQty_returns400` | qty = 0 → 400 | pass |
| `move_insufficientQty_returns409` | move quá tồn → 409 | pass |
| `move_sameLocation_returns400` | đích = nguồn → 400 | pass |
| `move_unknownStock_returns404` | stockId không tồn tại → 404 | pass |

**Fix kèm theo (để concurrent receive đúng):** khóa bi quan `Location` / `Warehouse` khi check capacity trong `StockService` (`findByIdForUpdate`), tránh 2 request cùng vượt sức chứa.

Adjust validation chi tiết (note trống, delta 0, audit…) giữ ở `InventoryAdjustApiIntegrationTest` (Duy).

---

## Checklist + báo cáo Duy — đã làm

**Phạm vi:** IT `POST /inventory/moves` + validation/audit `POST /inventory/adjusts`. Concurrent adjust/receive nằm ở phần Long (`WarehouseApiIntegrationTest`). Không sửa logic atomic trong `StockService`.

Nhánh: `feature/api-warehouse-integration-tests`.

```powershell
cd hbsisms-api
.\mvnw.cmd test "-Dtest=InventoryAdjustApiIntegrationTest,InventoryMoveApiIntegrationTest"
```

Kết quả gần nhất: **10 tests, 0 failures** (BUILD SUCCESS) — Adjust **6** + Move **4**.

### InventoryAdjustApiIntegrationTest

File: `hbsisms-api/src/test/java/com/hbsisms/feature/inventory/InventoryAdjustApiIntegrationTest.java`

| Test method | Hành vi | Kết quả |
|-------------|---------|---------|
| `adjust_decreasesStockWritesLedgerAndAudit` | giảm tồn + ledger ADJUST + audit | pass |
| `adjust_increasesStock` | tăng tồn thành công | pass |
| `adjust_blankNote_returns400` | note trống → 400 | pass |
| `adjust_zeroDelta_returns400` | delta = 0 → 400 | pass |
| `adjust_unknownStock_returns404` | stockId không có → 404 | pass |
| `adjust_exceedsAvailable_returns409` | trừ vượt available → 409 | pass |

### InventoryMoveApiIntegrationTest

File: `hbsisms-api/src/test/java/com/hbsisms/feature/inventory/InventoryMoveApiIntegrationTest.java`

| Test method | Hành vi | Kết quả |
|-------------|---------|---------|
| `move_updatesSourceTargetAndWritesLedger` | chuyển thành công + ledger MOVE | pass |
| `move_unknownStock_returns404` | stockId không có → 404 | pass |
| `move_sameLocation_returns400` | đích = nguồn → 400 | pass |
| `move_qtyExceedsAvailable_returns409` | qty > available → 409 | pass |

---

## Phần Khôi — đã làm (task 40)

File: `hbsisms-api/src/test/java/com/hbsisms/feature/inventory/StocktakeDamageApiIntegrationTest.java`

```powershell
cd hbsisms-api
.\mvnw.cmd test "-Dtest=StocktakeDamageApiIntegrationTest"
```

Kết quả gần nhất: **14 tests, 0 failures** (BUILD SUCCESS).

| Test method | Hành vi | Kết quả |
|-------------|---------|---------|
| `stocktake_noDiff_completesDoneWithoutApproval` | count đúng hệ thống → complete DONE, không tạo approval | pass |
| `stocktake_withDiff_approve_adjustsStock` | chênh lệch → PENDING_APPROVAL; approve → DONE + điều chỉnh tồn | pass |
| `stocktake_withDiff_reject_keepsCountingWithoutAdjust` | reject → về COUNTING, tồn không đổi | pass |
| `stocktake_cancel_marksCancelled` | cancel phiên COUNTING → CANCELLED | pass |
| `stocktake_create_emptyZone_returns409` | zone không có hàng → 409 | pass |
| `stocktake_complete_uncountedLines_returns409` | còn dòng chưa đếm mà complete → 409 | pass |
| `stocktake_saveCounts_negativeQty_returns400` | countedQty &lt; 0 → 400 | pass |
| `stocktake_saveCounts_whilePendingApproval_returns409` | đang chờ duyệt mà sửa số đếm → 409 | pass |
| `damage_createDoesNotDeduct_approveDoes` | tạo damage không trừ; approve mới trừ on-hand | pass |
| `damage_rejectDoesNotTouchStock` | reject damage → tồn giữ nguyên | pass |
| `damage_create_rejectsWhenQtyExceedsOnHand` | qty hỏng > on-hand → 409 | pass |
| `damage_create_blankReason_returns400` | lý do trống/whitespace → 400 | pass |
| `damage_create_lotVariantMismatch_returns400` | lot không thuộc variant → 400 | pass |
| `damage_decideAgain_returns409` | duyệt lại task đã xử lý → 409; tồn không đổi thêm | pass |

---

## Đóng tracking

Đủ **3 phần báo cáo trong file này** (Long đã có + Duy + Khôi) mới đánh dấu xong IT kho trên Excel.

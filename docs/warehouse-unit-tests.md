# Unit test kho (StockService & nghiệp vụ)

File bàn giao sau khi Long hoàn thành phần unit test `StockService`. Duy và Khôi đọc checklist bên dưới, tự viết test phần mình, rồi báo cáo riêng.

**Lệnh chạy phần Long**

```powershell
cd hbsisms-api
.\mvnw.cmd test -Dtest=StockServiceTest
```

Kết quả gần nhất: **39 tests, 0 failures** (BUILD SUCCESS).

---

## Phần Long (StockServiceTest) — đã làm

File: `hbsisms-api/src/test/java/com/hbsisms/feature/inventory/StockServiceTest.java`

Trước: 11 test · Sau: **39 test** · Tất cả **pass**.

| Test method | Hành vi | Kết quả |
|-------------|---------|---------|
| `receiveIn_rejectsNonPositiveQty` | qty ≤ 0 → 400 | pass |
| `issueOut_rejectsNonPositiveQty` | qty ≤ 0 → 400 | pass |
| `hold_rejectsNonPositiveQty` | qty ≤ 0 → 400 | pass |
| `release_rejectsNonPositiveQty` | qty ≤ 0 → 400 | pass |
| `move_rejectsNonPositiveQty` | qty ≤ 0 → 400 | pass |
| `damage_rejectsNonPositiveQty` | qty ≤ 0 → 400 | pass |
| `expire_rejectsNonPositiveQty` | qty ≤ 0 → 400 | pass |
| `receiveIn_createsStockAndWritesLedger` | tạo stock mới + ledger IN | pass |
| `receiveIn_addsToExistingStock` | cộng vào stock có sẵn + ledger IN | pass |
| `receiveIn_conflictsWhenExceedsLocationCapacity` | vượt sức chứa vị trí → 409, không ghi ledger | pass |
| `receiveIn_conflictsWhenExceedsWarehouseCapacity` | vượt sức chứa kho → 409 | pass |
| `issueOut_writesLedgerWhenSuccessful` | xuất thành công + ledger OUT | pass |
| `issueOut_conflictsWhenAtomicUpdateReturnsZero` | không đủ tồn → 409, không ghi ledger | pass |
| `issueOut_notFoundWhenStockMissing` | thiếu dòng tồn → 404 | pass |
| `hold_reducesAvailableAndIncreasesHeld` | giữ hàng + ledger HOLD | pass |
| `hold_conflictsWhenAvailableInsufficient` | thiếu khả dụng → 409 | pass |
| `release_returnsHeldToAvailableAndWritesLedger` | nhả giữ + ledger RELEASE | pass |
| `release_conflictsWhenHeldInsufficient` | thiếu tồn đang giữ → 409 | pass |
| `move_requiresToLocationId` | thiếu vị trí đích → 400 | pass |
| `move_rejectsSameLocation` | nguồn = đích → 400 | pass |
| `move_notFoundWhenTargetLocationMissing` | đích không tồn tại → 404 | pass |
| `move_conflictsWhenTargetLocationCapacityExceeded` | vượt capacity vị trí đích → 409 | pass |
| `move_sameWarehouse_doesNotCheckWarehouseCapacityAgain` | cùng kho: không gọi sumQtyByWarehouseId | pass |
| `move_crossWarehouse_conflictsWhenTargetWarehouseFull` | khác kho, vượt capacity kho đích → 409 | pass |
| `move_conflictsWhenSourceLacksQty` | nguồn thiếu tồn → 409 | pass |
| `move_updatesSourceAndTarget` | chuyển thành công + ledger MOVE | pass |
| `moveByStockId_notFound` | stock id không có → 404 | pass |
| `moveByStockId_delegatesToMove` | ủy quyền move + note mặc định | pass |
| `adjust_requiresNote` | note trống → 400 | pass |
| `adjust_rejectsZeroDelta` | delta = 0 → 400 | pass |
| `adjust_positiveChecksCapacityAndWritesLedger` | tăng tồn + check capacity + ADJUST | pass |
| `adjust_negativeConflictsWhenInsufficient` | giảm vượt tồn → 409 | pass |
| `adjustByStockId_notFound` | 404 | pass |
| `adjustByStockId_zeroDelta_rejected` | 400 | pass |
| `adjustByStockId_decreasesAndWritesLedger` | giảm + ADJUST | pass |
| `damage_deductsAcrossBucketsAndWritesLedger` | trừ quarantine rồi available + DAMAGE | pass |
| `damage_conflictsWhenOnHandInsufficient` | on-hand không đủ → 409 | pass |
| `expire_movesAvailableToExpiredAndWritesLedger` | available → expired + EXPIRE | pass |
| `expire_conflictsWhenAvailableInsufficient` | thiếu available → 409 | pass |

Long **không** làm unit test API move/adjust của Duy, cũng không làm stocktake/damage service của Khôi (ngoài core `StockService.damage` / `expire` ở trên).

---

## Checklist Duy (move / adjust) — tự làm + báo cáo riêng

**Mục tiêu tracking:** chuyển vị trí, điều chỉnh tồn (API / audit). Không sửa logic ledger trong `StockService` trừ khi phát hiện bug (báo Long).

**File gợi ý**

- IT mẫu: `InventoryAdjustApiIntegrationTest.java`
- Có thể bổ sung unit/IT quanh `POST /inventory/moves`, `POST /inventory/adjusts`, audit log

**Case tối thiểu cần có / xác nhận**

1. Move: vị trí đích trùng / không hợp lệ → 400
2. Move: qty > available → 409
3. Adjust: note bắt buộc → 400
4. Adjust: delta = 0 → 400
5. Adjust: vượt available → 409
6. Adjust tăng tồn thành công + ghi audit (nếu module audit thuộc phần Duy)

**Chạy test (ví dụ)**

```powershell
cd hbsisms-api
.\mvnw.cmd test -Dtest=InventoryAdjustApiIntegrationTest
# hoặc -Dtest=TênClassTestCủaBạn
```

**Format báo cáo riêng (copy vào PR / tracking)**

| Test method | Hành vi | Pass/Fail |
|-------------|---------|-----------|
| … | … | … |

Nhánh gợi ý: theo GIT.md (`feature/api-stock-move` / adjust).

---

## Checklist Khôi (stocktake / damage) — đã làm + báo cáo

**Mục tiêu tracking:** kiểm kê chốt đúng chênh lệch; báo hỏng chỉ trừ khi duyệt. Cover đủ nhánh chính của `StocktakeService` / `DamageReportService` (happy path + validation + conflict).

**Lệnh chạy phần Khôi**

```powershell
cd hbsisms-api
.\mvnw.cmd test -Dtest=StocktakeServiceTest,DamageReportServiceTest
```

Kết quả gần nhất: **34 tests, 0 failures** (BUILD SUCCESS) — Stocktake **22** + Damage **12**.

Nhánh: `feature/api-stocktake-damage-unit-tests`.

### StocktakeServiceTest

File: `hbsisms-api/src/test/java/com/hbsisms/feature/inventory/StocktakeServiceTest.java`

Trước: 1 test · Sau: **22 test** · Tất cả **pass**.

| Test method | Hành vi | Kết quả |
|-------------|---------|---------|
| `create_rejectsWhenNoStockInScope` | scope không có hàng → 409 | pass |
| `create_rejectsWhenZoneHasNoLocations` | zone không có vị trí → 409 | pass |
| `create_snapshotsSystemQtyFromAvailablePlusHeld` | create có stock → snapshot = available + held | pass |
| `create_wholeWarehouse_setsScopeToanKho` | zone null → scope "Toàn kho" | pass |
| `create_rejectsWhenActorMissing` | actor null → 403 | pass |
| `create_rejectsWhenUserNotFound` | user không tồn tại → 404 | pass |
| `saveCounts_persistsCountedQty` | lưu số đếm thành công | pass |
| `saveCounts_rejectsNegativeCountedQty` | số đếm âm → 400 | pass |
| `saveCounts_rejectsWhenPendingApproval` | đang chờ duyệt → 409 | pass |
| `complete_whenNoDiff_marksDoneWithoutApprovalOrAdjust` | không lệch → DONE, không duyệt / không adjust | pass |
| `complete_whenDiff_opensApprovalWithoutAdjustingYet` | có lệch → mở approval, chưa adjust | pass |
| `complete_rejectsWhenUncountedLinesRemain` | còn dòng chưa đếm → 409 | pass |
| `complete_rejectsWhenPendingApproval` | đang chờ duyệt → 409 | pass |
| `complete_rejectsWhenApprovalAlreadyOpen` | gửi duyệt lần 2 → 409 | pass |
| `applyApprovalDecision_approved_adjustsByCountedMinusSystem` | approve → adjust đúng delta | pass |
| `applyApprovalDecision_approved_skipsZeroDiffLines` | diff = 0 không adjust thừa | pass |
| `applyApprovalDecision_approved_notFoundWhenStockMissing` | thiếu dòng tồn → 404 | pass |
| `applyApprovalDecision_rejected_keepsCountingWithoutAdjust` | reject → COUNTING, không adjust | pass |
| `applyApprovalDecision_rejectsWhenNotCounting` | status ≠ COUNTING → 409 | pass |
| `cancel_marksCancelled` | cancel → CANCELLED | pass |
| `cancel_rejectsWhenNotCounting` | cancel khi DONE → 409 | pass |
| `get_notFound` | id không có → 404 | pass |

### DamageReportServiceTest

File: `hbsisms-api/src/test/java/com/hbsisms/feature/inventory/DamageReportServiceTest.java`

Trước: 4 test · Sau: **12 test** · Tất cả **pass**.

| Test method | Hành vi | Kết quả |
|-------------|---------|---------|
| `create_opensPendingApprovalWithoutDeductingStock` | create pending + mở duyệt, **không** trừ tồn | pass |
| `create_rejectsWhenQtyExceedsOnHand` | qty > on-hand → 409 | pass |
| `create_rejectsWhenLotNotBelongToVariant` | lô ≠ variant → 400 | pass |
| `create_rejectsWhenStockMissingAtLocation` | không có tồn tại vị trí → 400 | pass |
| `create_rejectsWhenReasonBlank` | lý do trống → 400 | pass |
| `create_rejectsWhenVariantNotFound` | variant không có → 404 | pass |
| `applyDecision_approved_callsStockDamage` | approve → `damage` đúng qty | pass |
| `applyDecision_rejected_doesNotTouchStock` | reject → không đụng tồn | pass |
| `applyDecision_whenAlreadyDecided_rejectsWithoutTouchingStock` | duyệt lần 2 → 409 | pass |
| `applyDecision_approved_propagatesWhenStockDamageFails` | thiếu tồn lúc approve → lỗi; damage 1 lần | pass |
| `applyDecision_approved_rejectsWhenLocationMissing` | thiếu location → 409, không damage | pass |
| `applyDecision_notFound` | report id không có → 404 | pass |

---

## Đóng việc trên tracking

Đủ **3 báo cáo** mới đánh dấu xong phần unit test kho trên Excel:

1. Long — mục trong file này ✅  
2. Duy — báo cáo riêng (checklist phía trên)  
3. Khôi — mục trong file này ✅ (đủ nhánh service, không chỉ case tối thiểu)  

Task IT concurrent (tracking IT kho) làm sau, không nằm trong file này.

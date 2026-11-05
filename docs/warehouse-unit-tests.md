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

## Checklist Khôi (stocktake / damage) — tự làm + báo cáo riêng

**Mục tiêu tracking:** kiểm kê chốt đúng chênh lệch; báo hỏng chỉ trừ khi duyệt.

**File**

- `StocktakeServiceTest.java` (hiện còn mỏng — cần mở rộng)
- `DamageReportServiceTest.java` (đã có create / approve / reject cơ bản — bổ sung nếu thiếu)

**Case tối thiểu Stocktake**

1. Create trong scope có stock → snapshot đúng qty hệ thống
2. Save counts → complete: gọi `StockService.adjust` đúng delta (counted − system)
3. Complete khi không lệch → không gọi adjust thừa
4. Cancel / reject path nếu service hỗ trợ

**Case tối thiểu Damage**

1. Create pending: **không** trừ tồn
2. Approve: gọi `StockService.damage` đúng qty
3. Reject: không đụng tồn
4. Approve khi không còn đủ on-hand → lỗi; không trừ hai lần

**Chạy test (ví dụ)**

```powershell
cd hbsisms-api
.\mvnw.cmd test -Dtest=StocktakeServiceTest,DamageReportServiceTest
```

**Format báo cáo:** giống bảng của Duy.

---

## Đóng việc trên tracking

Đủ **3 báo cáo** mới đánh dấu xong phần unit test kho trên Excel:

1. Long — mục trong file này  
2. Duy — báo cáo riêng  
3. Khôi — báo cáo riêng  

Task IT concurrent (tracking IT kho) làm sau, không nằm trong file này.

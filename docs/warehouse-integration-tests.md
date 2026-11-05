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

## Checklist + báo cáo Duy — tự làm rồi append vào đây

**Việc gợi ý**

- IT move edge / audit adjust nếu còn gap ngoài `InventoryAdjustApiIntegrationTest`
- Không sửa logic atomic trừ tồn trong `StockService` trừ khi phát hiện bug

**Sau khi xong:** thêm bảng dưới (PR riêng vào cùng file):

| Test method | Hành vi | Pass/Fail |
|-------------|---------|-----------|
| *(Duy điền)* | | |

Lệnh ví dụ: `.\mvnw.cmd test -Dtest=InventoryAdjustApiIntegrationTest` (và class IT mới nếu có).

---

## Checklist + báo cáo Khôi — tự làm rồi append vào đây

**Việc gợi ý**

- IT API stocktake: create → count → complete (adjust đúng chênh lệch)
- IT damage-reports: create không trừ; approve trừ; reject không trừ

**Sau khi xong:** thêm bảng:

| Test method | Hành vi | Pass/Fail |
|-------------|---------|-----------|
| *(Khôi điền)* | | |

---

## Đóng tracking

Đủ **3 phần báo cáo trong file này** (Long đã có + Duy + Khôi) mới đánh dấu xong IT kho trên Excel.

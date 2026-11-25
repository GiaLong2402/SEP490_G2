# Danh sách API frontend đang gọi

Tổng cộng 97 endpoint, sinh từ `src/features/*/mock.ts`. Kiểu dữ liệu của từng endpoint nằm ở `src/features/<feature>/types.ts` và `api.ts`.


## admin

| Phương thức | Đường dẫn |
|---|---|
| GET | `/admin/users` | `position` = `"Giao hàng"` → người giao |
| POST | `/admin/users` | body `position`: `null` (Không) hoặc `"Giao hàng"` (dropdown Chức vụ) |
| PUT | `/admin/users/:id` | body `position` như POST; BE chuẩn hóa "giao hang"/"GIAO HÀNG" → `"Giao hàng"` |
| GET | `/admin/roles` |
| PUT | `/admin/roles/:id` |
| GET | `/admin/config` |
| PUT | `/admin/config/:key` |
| GET | `/admin/audit-log` |

## approvals

| Phương thức | Đường dẫn |
|---|---|
| GET | `/approvals` |
| POST | `/approvals/:id/decision` |

## auth

| Phương thức | Đường dẫn |
|---|---|
| POST | `/auth/login` |
| GET | `/auth/me` |
| POST | `/auth/logout` |

## customers

| Phương thức | Đường dẫn |
|---|---|
| GET | `/customers` |
| GET | `/customers/:id` |
| POST | `/customers` |
| PUT | `/customers/:id` |
| PATCH | `/customers/:id/active` |
| GET | `/customers/:id/addresses` |
| POST | `/customers/:id/addresses` |
| POST | `/customers/:id/prices` |

## damage

| Phương thức | Đường dẫn |
|---|---|
| GET | `/damage-reports` |
| POST | `/damage-reports` |

## dashboard

| Phương thức | Đường dẫn |
|---|---|
| GET | `/dashboard/summary` |

## debt

| Phương thức | Đường dẫn |
|---|---|
| GET | `/debt/aging` |
| GET | `/debt/:customerId/ledger` |

## deliveries

Contract FE: `features/deliveries/api.ts`. **Không có người giao nội bộ** — cách giao chỉ là nhãn `fulfillmentMethod`: `shop_delivery` (Shop tự giao: người trong tiệm / gửi nhà xe khi xa), `pickup` (Khách đến lấy), `partner_delivery` (Đối tác giao: book đơn vị vận chuyển; tích hợp API để sau). **Cường (Inter 3):** OUT theo đợt khi `PATCH` → `delivered`. **Long:** list / create / bắt đầu giao.

| Phương thức | Đường dẫn | Ghi chú |
|---|---|---|
| GET | `/deliveries` | Long — lọc `q`, `state`, `date`, `timeWindow`, `fulfillmentMethod`; sort `asc`; trả `startedAt`, `deliveredAt`, tổng khay |
| POST | `/deliveries` | Long — **201** + `DeliveryListItem`. Đơn Chờ giao / Giao một phần; ngày ≥ hôm nay; `timeWindow` ∈ 4 khung (`08:00-10:00`…`16:00-18:00`); giao tận nơi cần địa chỉ thuộc khách; không gán người giao; một đơn giao một lần, trọn đơn: đã có chuyến chưa thất bại → 409, thất bại thì tạo lại được (giao lại); `items` phải đủ khay mọi dòng (bỏ trống = BE tự lấy đủ). BE tự đặt `legLabel` = "Trọn đơn", mã `DL-<năm>-<số>` (dãy chung), khoảng cách |
| PATCH | `/deliveries/:id` | Body `{ state?, note? }`. Khóa đợt giao khi cập nhật. `ready → going` (Bắt đầu giao, không cần người giao): lưu `startedAt`, sự kiện "Bắt đầu giao … · <cách giao>" cho khách thấy; lấy tại quầy không có bước này (409). Đơn phải Chờ giao / Giao một phần. **DELIVERED → OUT theo đợt** (`refType=delivery`) |

## inventory

| Phương thức | Đường dẫn |
|---|---|
| GET | `/inventory/summary` |
| GET | `/inventory/variants` |
| GET | `/inventory/lots` |
| GET | `/inventory/near-expiry` |
| GET | `/inventory/ledger` | query: `q`, `entryType`, `from`, `to` (yyyy-MM-dd), page |
| GET | `/locations` | Chỉ vị trí kho **thành phẩm** (bỏ khu của kho nguyên liệu) |
| POST | `/inventory/moves` | body: `{ stockId, toLocationId, qty }` → 204; ledger `move` |
| POST | `/inventory/adjusts` | body: `{ stockId, qtyDelta, note }` → 204; ledger `adjust` + audit |

## marketing

| Phương thức | Đường dẫn |
|---|---|
| GET | `/vouchers` |
| POST | `/vouchers` |
| PUT | `/vouchers/:id` |
| GET | `/reviews` |
| PATCH | `/reviews/:id/status` |

## notifications

JWT bất kỳ role (staff + customer). Chỉ thấy / đánh dấu thông báo của chính mình.

| Phương thức | Đường dẫn | Ghi chú |
|---|---|---|
| GET | `/notifications` | Tối đa 30 mới nhất; `{ id, userId, type, title, body, link, isRead, createdAt }` |
| POST | `/notifications/read-all` | 204 |
| POST | `/notifications/:id/read` | 204; không phải của mình → 404 |

`quote-sent`: khi `PATCH /quotes/:id/status` → `sent`, gửi cho `customer.accountUser` (portal).

## orders

Contract FE (Doanh): `features/orders/api.ts` + `types.ts`; path const `shared/api/httpPaths.ts` (`pickingSlip`, `pickingSlipDaily`). UI soạn hàng: `features/picking/*` (nav **Soạn hàng**, in phiếu theo đơn trên OrderDetail). Hybrid: thêm prefix vào `VITE_API_HTTP_PATHS`.

| Phương thức | Đường dẫn | Ghi chú contract |
|---|---|---|
| GET | `/orders` | filter Inter 2 + Inter 3 dòng 5: `placedBy`, `fulfillmentMethod`, `deliveryFrom`, `deliveryTo`, `timeWindow`, `group`. Preset **Cần chuẩn bị**: `state=confirmed&paymentStatus=paid&allocationStatus=full` |
| GET | `/orders/counts` | Đếm 9 tab (`awaitingPayment`…`cancelled`); cùng filter list trừ `state`/`group`/`page` |
| GET | `/orders/owner-inbox` | Inter 3 dòng 11 + FE dòng 13 sóng B — việc cần Chủ; quyền **`approve`**; badge + drawer trên `/orders` |
| GET | `/orders/export` | Inter 3 dòng 12 + FE dòng 13 sóng B — xlsx theo filter; nút **Xuất Excel** trên `/orders`; **400** nếu > 5000 dòng |
| GET | `/orders/picking-slip-daily` | Cường — `?date=yyyy-MM-dd` bản gộp theo vị trí (`DailyPickingSlipResponse`) |
| GET | `/orders/:id` | `OrderDetail` — Inter 3 dòng 6 + FE dòng 13 sóng C: giao / trả / thu-hoàn / events / `auditTrail[]` |
| GET | `/orders/:id/picking-slip` | Cường — phiếu soạn từ HOLD (FEFO) (`PickingSlipResponse`) |
| POST | `/orders` | **201** + `OrderDetail`; body `OrderInput` (walk-in: `customerId` null + `phone`) |
| PUT | `/orders/:id` | **200** + `OrderDetail` |
| GET | `/orders/:id/receipt` | `Receipt` — chỉ đơn đã thanh toán đủ |
| POST | `/orders/:id/transition` | **204**; body `{ state }`. `to_ship` re-check HOLD + ghi `preparedBy`/`preparedAt` |
| POST | `/orders/:id/cancel` | **204**; body `{ reason }` |
| POST | `/orders/:id/complete-at-counter` | Chỉ `pickup` + `to_ship` + Paid + FULL + HOLD OK; OUT idempotent |
| POST | `/sales/hold-expire` | job hết hạn hold |

## payments

| Phương thức | Đường dẫn | Quyền (Inter 3 dòng 8) |
|---|---|---|
| GET | `/payments` | `debt` \| `orders` \| `approve` |
| POST | `/payments` | `orders` \| `debt` — **409** nếu đơn đang có PayOS `pending` |
| POST | `/payments/refund` | `approve` (Chủ) |
| PATCH | `/payments/:id/reconcile` | `approve` (Chủ) |

- `GET /payments?sort=asc` → cũ nhất trước; mặc định (bỏ trống) → giao dịch tạo gần nhất lên đầu.
- Mỗi dòng có thêm `excessAmount` (tiền khách chuyển thừa, khi `reconcileFlag=over`) và `payerName` / `payerAccount` / `payerBank` (tài khoản người chuyển do PayOS gửi, có thể `null`) — form hoàn tiền dùng.

## pos

Contract FE đã đăng ký path (`API_PATHS.pos`). **Chưa có BE** — giữ mock; **không** thêm `/pos` vào `VITE_API_HTTP_PATHS`.

| Phương thức | Đường dẫn |
|---|---|
| GET | `/pos/catalog` |
| POST | `/pos/checkout` |

## pricing

| Phương thức | Đường dẫn |
|---|---|
| POST | `/pricing/resolve` |

## products

Contract FE (Doanh — form/variant): body/response khớp `features/products/types.ts`.
Jackson khóa tên field `isPublished` / `isActive` (không `published` / `active`).

| Phương thức | Đường dẫn | Ghi chú contract |
|---|---|---|
| GET | `/categories` | Cường (read) |
| POST | `/categories` | Cường |
| PUT | `/categories/:id` | Cường |
| GET | `/variants` | options: `{ id, sku, packLabel, productName, listPrice, packPerCase }` (chỉ `isActive`) |
| GET | `/products` | Cường (list/search) |
| GET | `/products/:id` | `ProductDetail` (+ `variants[].tiers`, `available`) — dùng assert sau write |
| POST | `/products` | **201** + `ProductDetail`; body `ProductInput` |
| PUT | `/products/:id` | **200** + `ProductDetail`; `gallery: null` giữ, `[]` xóa |
| POST | `/products/:id/variants` | **204**; body `VariantInput` (+ `tiers[]`) |
| PUT | `/variants/:id` | **204**; body `VariantInput` |
| GET | `/products/:id/bom` | `BomDetail` — định mức NVL (Task BOM) |
| PUT | `/products/:id/bom` | **200** + `BomDetail`; body `{ lines: BomLineInput[] }` full replace; chặn chọn chính SP làm NVL của nó |

## production

Quyền `inventory`. **Role (sơ đồ Inter):** `warehouse` tạo/sửa lệnh + báo hoàn thành + nhập TP; `admin`/`owner` (chung) duyệt lệnh + xác nhận xuất NVL + sửa catalog/BOM. `sales` không vào module.

| Phương thức | Đường dẫn | Ghi chú |
|---|---|---|
| GET | `/production-orders` | Lọc `q`, `status` (`draft`/`approved`/`in_progress`/`awaiting_receipt`/`completed`/`cancelled`) |
| POST | `/production-orders` | **201** — bản nháp + explode BOM; `plannedQty` phải là số khay nguyên > 0 (400 nếu lẻ, cả khi sửa) |
| GET | `/production-orders/:id` | Chi tiết + `materials[]`; sau complete có `actualQty`, `actualMaterialCost`, `finishedDate`, `defectQty`/`defectReason`, `leftovers[]`, `finishedReceiptId/code`. Khi `completed`: thêm `unitCost`, `markupPercent` (app_config `sell_price_markup_percent`, mặc định 30), `suggestedSellPrice` = round_1000(unitCost × (1 + markup/100)) cho **khay 1 kg**. **Nhập TP xong** ghi `listPrice`: khay 1 kg = giá đó; khay 0,5 kg = round_1000(nửa); SP chỉ có 0,5 kg thì gán nguyên giá tính được. Khi `completed` còn có `finishedPutAway` = `{ lotCode, expDate, locationCode, locationZone, warehouseName }` (lô / HSD từ dòng phiếu TP, vị trí từ dòng sổ kho IN của phiếu) |
| PUT | `/production-orders/:id` | Chỉ `draft` |
| POST | `/production-orders/:id/approve` | Duy — đủ NVL → `approved` + phiếu xuất `pending` |
| POST | `/production-orders/:id/cancel` | `draft` / `approved` (chưa xuất) |
| POST | `/production-orders/:id/complete` | **Cường** — body `{ actualQty, actualProductionCost?, variantId?, finishedDate?, defectQty?, defectReason?, leftovers: [{ rawMaterialId, qty, expDate }] }`; chỉ `in_progress`; leftover → lô `<lô gốc>-TL` (đã có thì `-TL2`, `-TL3`…; lô xuất ra là lô -TL thì vẫn lấy mã gốc) + trừ `actualMaterialCost`; tạo PN FINISHED **pending** kèm dòng variant+qty; PO → `awaiting_receipt` (idempotent nếu đã có PN). Vượt KH chỉ cảnh báo FE. |
| GET | `/material-issues/:id` | Phiếu xuất NVL |
| POST | `/material-issues/:id/confirm` | **Cường** — body `{ note? }`; luôn FEFO (bỏ lô hết hạn, chỉ tồn dùng được); lưu `note`; trừ tồn lô + ledger `OUT` `refType=material_issue`; PO → `in_progress`; idempotent nếu đã `completed` |

## quotes

| Phương thức | Đường dẫn |
|---|---|
| GET | `/quotes` |
| GET | `/quotes/:id` |
| POST | `/quotes` |
| PATCH | `/quotes/:id/status` |
| POST | `/quotes/:id/convert` |

## receipts

Contract FE: `features/receipts/api.ts`. **Long (Inter 1 dòng 46–47):** phiếu nguyên liệu nhập 2 bước — tạo phiếu **Chờ nhập** (chưa cộng tồn) → thủ kho **xác nhận nhập kho** (SL thực nhận, lô, NSX/HSD, đơn giá) → cộng tồn **theo lô NVL** (`raw_material_lot`), ghi sổ kho NVL (`raw_material_ledger`), phiếu **Hoàn thành**. **Dòng 52:** phiếu thành phẩm từ lệnh SX cũng **Chờ nhập** → `POST …/complete` body `{ lotCode?, mfgDate?, expDate, locationId, qty, note? }` → cộng tồn TP, lưu giá vốn/khay = (NVL thật + chi phí SX) ÷ qty, lệnh → `completed`. Phiếu TP tạo tay vẫn nhập ngay. Tổng `qtyOnHand` của NVL luôn = tổng `qtyRemaining` các lô (tồn cũ chưa có lô → lô `TON-DAU` lúc khởi động). Chưa có module đơn mua hàng nên phiếu tạo tay.

| Phương thức | Đường dẫn | Ghi chú |
|---|---|---|
| GET | `/raw-materials` | Lọc `q`, `activeOnly`, `lowStock`, `storageLocationId` (khu cất). Trả thêm `storageLocationId`, `storageLocationCode`, `storageLocationName` |
| POST | `/raw-materials` | `storageLocationId` **bắt buộc**: khu cất thuộc kho nguyên liệu (Khu lạnh / Khu khô) |
| PUT | `/raw-materials/:id` | `storageLocationId` bỏ trống = giữ khu cũ |
| GET | `/raw-materials/:id/lots` | Lô NVL theo FEFO (HSD gần nhất trước, lô không HSD cuối). `availableOnly=true` chỉ lô còn tồn. Mỗi lô: mã lô, NSX, HSD, **đơn giá nhập**, SL nhập / còn, phiếu nhập. Dòng 50 (xuất NVL) dùng thứ tự này |
| GET | `/goods-receipts` | Lọc `q`, `receiptType` (`finished`/`raw`), `status` (`pending`/`completed`/`cancelled`; phiếu cũ không có trạng thái = `completed`), `sort` (`asc` = mã phiếu tăng dần; mặc định giảm dần). Trả thêm `status`, `expectedDate`, `completedAt`, `completerName`, `totalAmount`, `lines` |
| GET | `/goods-receipts/:id` | Chi tiết + `lines` (NVL: dự kiến / thực nhận / lô / HSD / đơn giá / thành tiền) |
| POST | `/goods-receipts` | **201.** `finished` → nhập ngay (như cũ). `raw` → **Chờ nhập**, `rawLines[].qty` = SL dự kiến, `expectedDate` tùy chọn; không cộng tồn. Mỗi NVL 1 dòng; NVL ngừng dùng → 409 |
| POST | `/goods-receipts/:id/complete` | **RAW:** body `{ lines: [{ lineId, qtyReceived, lotCode?, mfgDate?, expDate?, unitPrice }], note? }` — đủ mọi dòng; `qtyReceived` ≥ 0; > 0 thì đơn giá > 0; HSD không trước NSX / không quá khứ; mã lô trống → `NVL-yyMMdd-NN`. **FINISHED (từ LSX):** body `{ lotCode?, mfgDate?, expDate, locationId, qty, note? }` — cộng tồn, giá vốn/khay từ lệnh, cập nhật `listPrice` khay 1 kg + khay 0,5 kg (= nửa), PO → `completed`. Idempotent nếu đã hoàn thành. Khóa phiếu: đã hủy → 409 |
| POST | `/goods-receipts/:id/cancel` | Body `{ reason? }`. Chỉ phiếu Chờ nhập (không đổi tồn); đã nhập / đã hủy → 409 |

## reports

| Phương thức | Đường dẫn |
|---|---|
| GET | `/reports/sales` |

## requests

Contract FE: `features/requests/api.ts` → `/sales-requests` (path const `API_PATHS.salesRequests`).

| Phương thức | Đường dẫn |
|---|---|
| GET | `/sales-requests` |
| GET | `/sales-requests/:id` |
| POST | `/sales-requests` |
| POST | `/sales-requests/:id/feasibility` |
| POST | `/sales-requests/:id/reject` |
| PATCH | `/sales-requests/:id` |

## returns

| Phương thức | Đường dẫn |
|---|---|
| GET | `/returns/returnable-orders` |
| GET | `/returns` |
| POST | `/returns` |
| PATCH | `/returns/:id/status` |

## stocktakes

| Phương thức | Đường dẫn |
|---|---|
| GET | `/stocktakes` |
| GET | `/stocktakes/:id` |
| POST | `/stocktakes` |
| PUT | `/stocktakes/:id/lines` |
| POST | `/stocktakes/:id/complete` |
| POST | `/stocktakes/:id/cancel` |

## suppliers

| Phương thức | Đường dẫn |
|---|---|
| GET | `/suppliers` |
| POST | `/suppliers` |
| PUT | `/suppliers/:id` |

## warehouses

Hai loại kho (`kind`): **`finished`** — kho thành phẩm, vị trí chứa tồn theo lô, có sức chứa (khay); **`raw`** — kho nguyên liệu, vị trí là **khu cất** (`LANH` Khu lạnh / `KHO` Khu khô) gắn với từng nguyên liệu, không chứa tồn theo lô. Mọi luồng cất thành phẩm (nhập, chuyển vị trí, trả hàng, kiểm kê) từ chối vị trí của kho `raw`. Lúc bật API, `RawWarehouseSeeder` đặt `KHO-NL` là kho nguyên liệu, tạo 2 khu nếu chưa có, gắn khu cho nguyên liệu chưa có khu (đơn vị kg → Khu lạnh, còn lại → Khu khô).

| Phương thức | Đường dẫn | Ghi chú |
|---|---|---|
| GET | `/warehouses` | Kho thành phẩm trước, kho nguyên liệu sau. Mỗi kho có `kind`; mỗi vị trí có `materialCount` (số nguyên liệu cất ở khu, kho `raw`) |
| POST | `/warehouses` | body thêm `kind` (`finished` mặc định / `raw`) |
| PUT | `/warehouses/:id` | `kind` chỉ đổi được khi kho chưa có vị trí |
| POST | `/warehouses/:id/locations` | |
| PUT | `/locations/:id` | |
| DELETE | `/locations/:id` | Chặn khi còn tồn, hoặc khi khu đang là khu cất của nguyên liệu |

# Danh sách API frontend đang gọi

Tổng cộng 97 endpoint, sinh từ `src/features/*/mock.ts`. Kiểu dữ liệu của từng endpoint nằm ở `src/features/<feature>/types.ts` và `api.ts`.


## admin

| Phương thức | Đường dẫn |
|---|---|
| GET | `/admin/users` | response có `isCourier` |
| POST | `/admin/users` | body có `isCourier` (mặc định false) |
| PUT | `/admin/users/:id` | body có `isCourier` |
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

Contract FE: `features/deliveries/api.ts`. **Cường (Inter 3):** `GET /deliveries/couriers` + OUT theo đợt khi `PATCH` → `delivered`. **Long:** list/create đợt giao.

| Phương thức | Đường dẫn | Ghi chú |
|---|---|---|
| GET | `/deliveries/couriers` | Cường — `{ id, fullName, position }[]`; user `isCourier=true` + active |
| GET | `/deliveries` | Long — chưa BE |
| POST | `/deliveries` | Long — chưa BE |
| PATCH | `/deliveries/:id` | state/assign; **DELIVERED → OUT theo đợt** (`refType=delivery`) |

## inventory

| Phương thức | Đường dẫn |
|---|---|
| GET | `/inventory/summary` |
| GET | `/inventory/variants` |
| GET | `/inventory/lots` |
| GET | `/inventory/near-expiry` |
| GET | `/inventory/ledger` | query: `q`, `entryType`, `from`, `to` (yyyy-MM-dd), page |
| GET | `/locations` |
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

| Phương thức | Đường dẫn |
|---|---|
| GET | `/notifications` |
| POST | `/notifications/read-all` |
| POST | `/notifications/:id/read` |

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

## quotes

| Phương thức | Đường dẫn |
|---|---|
| GET | `/quotes` |
| GET | `/quotes/:id` |
| POST | `/quotes` |
| PATCH | `/quotes/:id/status` |
| POST | `/quotes/:id/convert` |

## receipts

| Phương thức | Đường dẫn |
|---|---|
| GET | `/raw-materials` |
| POST | `/raw-materials` |
| PUT | `/raw-materials/:id` |
| GET | `/goods-receipts` |
| POST | `/goods-receipts` |

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

| Phương thức | Đường dẫn |
|---|---|
| GET | `/warehouses` |
| POST | `/warehouses` |
| PUT | `/warehouses/:id` |
| POST | `/warehouses/:id/locations` |
| PUT | `/locations/:id` |
| DELETE | `/locations/:id` |

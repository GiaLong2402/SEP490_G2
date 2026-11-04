# Danh sách API frontend đang gọi

Tổng cộng 97 endpoint, sinh từ `src/features/*/mock.ts`. Kiểu dữ liệu của từng endpoint nằm ở `src/features/<feature>/types.ts` và `api.ts`.


## admin

| Phương thức | Đường dẫn |
|---|---|
| GET | `/admin/users` |
| POST | `/admin/users` |
| PUT | `/admin/users/:id` |
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

| Phương thức | Đường dẫn |
|---|---|
| GET | `/deliveries/couriers` |
| GET | `/deliveries` |
| POST | `/deliveries` |
| PATCH | `/deliveries/:id` |

## inventory

| Phương thức | Đường dẫn |
|---|---|
| GET | `/inventory/summary` |
| GET | `/inventory/variants` |
| GET | `/inventory/lots` |
| GET | `/inventory/near-expiry` |
| GET | `/inventory/ledger` |
| GET | `/locations` |
| POST | `/inventory/moves` |

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

| Phương thức | Đường dẫn |
|---|---|
| GET | `/orders` |
| GET | `/orders/:id` |
| POST | `/orders` |
| POST | `/orders/:id/transition` |
| POST | `/orders/:id/cancel` |

## payments

| Phương thức | Đường dẫn |
|---|---|
| GET | `/payments` |
| POST | `/payments` |

## pos

| Phương thức | Đường dẫn |
|---|---|
| GET | `/pos/catalog` |
| POST | `/pos/checkout` |

## pricing

| Phương thức | Đường dẫn |
|---|---|
| POST | `/pricing/resolve` |

## products

| Phương thức | Đường dẫn |
|---|---|
| GET | `/categories` |
| POST | `/categories` |
| PUT | `/categories/:id` |
| GET | `/variants` |
| GET | `/products` |
| GET | `/products/:id` |
| POST | `/products` |
| PUT | `/products/:id` |
| POST | `/products/:id/variants` |
| PUT | `/variants/:id` |

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

| Phương thức | Đường dẫn |
|---|---|
| GET | `/sales-requests` |
| POST | `/sales-requests` |
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

# Hợp đồng API phía khách — web external

Tài liệu cho người làm backend các task **Khách mua hàng** (Duy) và **Thanh toán PayOS** (Long), Inter 2.
Frontend đã dựng xong giao diện; mỗi endpoint dưới đây đã có hàm gọi sẵn trong `src/api/` và có **mock** để chạy thử.

> Base URL: `/api/v1`. Mọi endpoint `/me/**`, `/checkout`, `/payments/payos/**` (trừ webhook) cần JWT của khách (role `customer`).
> Hiện role `customer` chưa có quyền nào (`DefaultRoles`), nên cần mở các endpoint này cho khách và **luôn lọc theo khách đang đăng nhập** (đơn của người khác → 404).

## Cách frontend tổ chức (để nối nhanh)

```
Trang  →  src/hooks/useAsync.js  →  src/api/<domain>.js  →  fetch thật  (hoặc mock khi VITE_USE_MOCK=true)
                                          ↓
                              src/api/mappers/<domain>.js   (DTO backend → dữ liệu giao diện)
```

| Muốn đổi… | Sửa ở đâu |
|---|---|
| URL / method / body gửi đi | `src/api/<domain>.js` |
| Tên field backend trả về | `src/api/mappers/<domain>.js` (trang không cần sửa) |
| Nhãn / màu trạng thái | `src/constants/orderStatus.js` |
| Dữ liệu giả để chạy thử | `src/api/mock/data.js`, `src/api/mock/index.js` |

**Bật mock khi dev:** tạo `frontend/.env.development.local` (không commit) với `VITE_USE_MOCK=true`, chạy lại `npm run dev`.
Mock chỉ chặn các endpoint trong tài liệu này; `/products`, `/auth/**` vẫn gọi backend thật. Bản build không chứa mock.
Kịch bản mock qua URL: `?mock=shortage | pay-pending | pay-cancelled | pay-failed | pay-expired | empty`.

## Checklist khi làm xong 1 endpoint (người làm backend tự kiểm)

1. **URL + method** đúng bảng ở mục tương ứng. Khác thì sửa đúng 1 dòng trong `src/api/<domain>.js` và cập nhật tài liệu này.
2. **Tên field JSON** đúng như tài liệu (camelCase, enum viết HOA như backend: `PENDING`, `SHOP_DELIVERY`…). Khác thì sửa trong `src/api/mappers/<domain>.js`.
3. **Quyền:** gọi được bằng token của tài khoản `customer`; dữ liệu chỉ của chính khách đó (thử mã đơn của khách khác → 404).
4. **Tắt mock** (`VITE_USE_MOCK` không bật), chạy backend + `npm run dev`, đăng nhập khách, mở màn liên quan:
   - Mở **Console** của trình duyệt (F12). Nếu backend trả thiếu field giao diện cần, sẽ có dòng
     `[API] … thiếu field: … — xem API_CONTRACT.md § …` → bổ sung field hoặc sửa mapper. Không còn dòng `[API]` là khớp.
   - Màn hiện dữ liệu thật, không còn ô "Chưa tải được dữ liệu".
5. **Lỗi nghiệp vụ** trả đúng mã + `message` tiếng Việt (409 thiếu hàng có `shortages`, 400 validate có `errors`).
6. Đổi cột **Trạng thái nối** ở cuối tài liệu thành "Đã nối".

| Màn cần mở để kiểm | Endpoint |
|---|---|
| Tài khoản (form hồ sơ) | § 1a Hồ sơ / đổi SĐT |
| Tài khoản → Sổ địa chỉ; Thanh toán bước 1 | § 1 Địa chỉ |
| Đơn & Yêu cầu (tab Đơn hàng) → Chi tiết đơn → Hủy đơn; Biên lai | § 2 Đơn hàng |
| Đơn & Yêu cầu (tab Yêu cầu) → Chi tiết yêu cầu | § 3 Yêu cầu |
| Giỏ hàng → Thanh toán → "Đặt hàng & thanh toán" | § 4 Checkout |
| Màn quét QR → "Mở trang thanh toán PayOS" → trang Kết quả thanh toán | § 5 PayOS |

**Lỗi:** frontend đọc đúng `ErrorResponse` hiện có `{ status, message, errors }` — `message` hiện cho khách, `errors` (field → message) hiện dưới từng ô của form.

---

## 1a. Hồ sơ — task "Hồ sơ & địa chỉ của tôi" (Duy)

File FE: `src/api/profile.js`. Dùng ở trang Tài khoản.

| Method | URL | Body | Trả về |
|---|---|---|---|
| PATCH | `/me/profile` | `{ fullName, email? }` | `CurrentUserResponse` (có `phone`) |
| POST | `/me/phone` | `{ idToken }` | `CurrentUserResponse` sau khi đổi SĐT |

- Đổi SĐT bắt buộc OTP Firebase/stub (`idToken`); SĐT trùng tài khoản khác → 409.
- `GET /auth/me` và mọi `CurrentUserResponse` trả thêm field `phone`.
- Toggle thông báo email trên UI **không** persist (chưa có API).

## 1. Địa chỉ — task "Hồ sơ & địa chỉ của tôi" (Duy)

File FE: `src/api/addresses.js`, mapper `mappers/address.js`. Dùng ở Sổ địa chỉ + bước 1 Thanh toán.

| Method | URL | Body | Trả về |
|---|---|---|---|
| GET | `/me/addresses` | — | `AddressResponse[]` (mảng hoặc `{ items }` đều được) |
| POST | `/me/addresses` | `AddressRequest` | `AddressResponse` |
| PUT | `/me/addresses/{id}` | `AddressRequest` | `AddressResponse` |
| DELETE | `/me/addresses/{id}` | — | 204 |

- Dùng lại `AddressRequest` / `AddressResponse` đã có (`feature/customer/dto`). FE gửi `kind: "SHIPPING"`.
- "Đặt làm mặc định" = `PUT` với `isDefault: true` → backend bỏ mặc định các địa chỉ khác (1 mặc định / khách).
- Địa chỉ đầu tiên luôn là mặc định. Xóa địa chỉ mặc định khi còn địa chỉ khác → 409 kèm `message`.

## 2. Đơn hàng — task "Đơn của tôi" + "Khách hủy đơn chưa thanh toán" (Duy)

File FE: `src/api/orders.js`, mapper `mappers/order.js`. Dùng ở Đơn & Yêu cầu, Chi tiết đơn, Biên lai.

| Method | URL | Trả về |
|---|---|---|
| GET | `/me/orders?page=1&pageSize=100` | `PageResponse<…>` — mỗi item như `OrderDetailResponse` (cần `lines` để vẽ thẻ đơn) |
| GET | `/me/orders/{code}` | `OrderDetailResponse` (theo **mã đơn**, không theo id) |
| POST | `/me/orders/{code}/cancel` | `OrderDetailResponse` sau khi hủy; không hủy được → 409 |

Field FE đang đọc (đã có trong `OrderDetailResponse`): `id, code, state, paymentStatus, fulfillmentMethod, subtotal, discountTotal, shippingFee, total, paidAmount, placedAt, holdExpiresAt, note, canCancel, customer{name,phone}, address (AddressResponse), deliveries[0]{scheduledDate,timeWindow}, payments[]{method,direction,status,paidAt,checkoutUrl,payosOrderCode}, events[]{id,eventType,description,createdAt}`.

**Đề xuất bổ sung cho phía khách:**
- `lines[].imageUrl` — ảnh sản phẩm (OrderLineViewResponse chưa có). Thiếu thì FE dùng ảnh mặc định.
- List `/me/orders` trả kèm `lines` (OrderListItemResponse hiện không có dòng hàng).

Trạng thái hiển thị (`constants/orderStatus.js` → `orderUiStatus`):

| OrderState + PaymentStatus | Khách thấy |
|---|---|
| `PENDING` + chưa `PAID` | Chờ thanh toán |
| `PENDING` đã trả, `CONFIRMED`, `PREPARING` | Đang chuẩn bị |
| `TO_SHIP`, `PARTIAL` | Đang giao / sẵn sàng lấy |
| `DELIVERED`, `COMPLETED` | Hoàn thành |
| `CANCELLED` | Đã hủy |

Hủy đơn: chỉ khi `canCancel` (đơn chờ thanh toán) → giải phóng hàng giữ + hủy link PayOS (task Long).

## 3. Yêu cầu mua vượt tồn — task "Gửi yêu cầu mua vượt tồn" (Duy)

File FE: `src/api/salesRequests.js`, mapper `mappers/salesRequest.js`. Dùng ở tab Yêu cầu, Chi tiết yêu cầu, hộp thoại thiếu hàng.

**Đã nối:** `GET/POST /me/sales-requests`, `GET /me/sales-requests/{code}` (JWT + `MeCustomerResolver`). Status trên `/me` là **UPPERCASE** (`NEW`…); staff `/sales-requests` vẫn lowercase. `items` trong body bị bỏ qua (chỉ lưu summary + qty tổng). Seed demo: `YCM-DEMO-001` cho KH-001.

| Method | URL | Body / Trả về |
|---|---|---|
| GET | `/me/sales-requests?page=1&pageSize=100` | `PageResponse<MeSalesRequestResponse>` |
| GET | `/me/sales-requests/{code}` | `MeSalesRequestResponse` |
| POST | `/me/sales-requests` | body dưới → `MeSalesRequestResponse` (status `NEW`) |

```json
{
  "channel": "WEB",
  "summary": "Chả Cá · Khay 500 g × 3 khay (còn 1)",
  "neededDate": "2026-11-13",
  "requestedQty": 3,
  "availableQty": 1,
  "items": [{ "variantId": 12, "requestedQty": 3, "availableQty": 1 }]
}
```

- Body lưu: `summary`, `neededDate`, `requestedQty`, `availableQty` (channel portal cố định `WEB`). `items` chưa persist.
- Response: `code, summary, status, receivedAt, neededDate, requestedQty, availableQty, feasible, fulfillableQty, shortageQty, expectedDate, rejectReason, note, orderCode` (`orderCode` khi Sales chốt `ORDERED`).
- Nhãn: `NEW` Đang xem xét · `QUOTING` Đang báo giá · `ORDERED` Đã chốt · `REJECTED` Không đáp ứng được · `CLOSED` Đã đóng.

## 4. Checkout — task "Checkout" (Duy)

File FE: `src/api/checkout.js`. Dùng ở bước "Đặt hàng & thanh toán".

`POST /checkout` — header **`Idempotency-Key: <uuid>`** (FE tạo 1 key cho 1 lần đặt; bấm lại cùng key → trả lại đơn cũ, không tạo đơn mới). FE cũng gửi **`X-Cart-Session`** để backend xóa giỏ server sau khi tạo đơn.

**Đã nối (#27):** backend tạo `ONLINE_B2C` `PENDING` + `WAITING`, hold FEFO 15 phút, snapshot giá. `checkoutUrl` / `qrCode` hiện là **stub** (trỏ trang kết quả FE) — Long thay bằng PayOS thật (#33).

```json
{
  "fulfillmentMethod": "SHOP_DELIVERY",
  "shipAddressId": 1,
  "requestedDate": "2026-11-13",
  "timeWindow": "10:00-12:00",
  "note": "Gọi trước khi giao",
  "paymentMethod": "PAYOS",
  "lines": [{ "variantId": 12, "qtyKhay": 2 }]
}
```

`fulfillmentMethod`: `SHOP_DELIVERY` | `PICKUP` (khi `PICKUP`, `shipAddressId = null`).
`timeWindow`: một trong `08:00-10:00`, `10:00-12:00`, `14:00-16:00`, `16:00-18:00`.
FE gửi kèm `unitPrice, productName, packLabel, imageUrl` trong `lines` chỉ để mock hiển thị — **backend bỏ qua, tự tính giá bằng PriceResolver**.

**201**
```json
{
  "order": { "...": "OrderDetailResponse (state PENDING, paymentStatus WAITING, holdExpiresAt)" },
  "payment": {
    "payosOrderCode": 131000,
    "amount": 260000,
    "checkoutUrl": "https://pay.payos.vn/web/…",
    "qrCode": "000201010212…",
    "expiresAt": "2026-11-10T07:15:00Z"
  }
}
```

**409 thiếu hàng** — FE mở hộp thoại thiếu hàng:
```json
{ "status": 409, "message": "Một số sản phẩm không đủ hàng",
  "shortages": [{ "variantId": 12, "requestedQty": 3, "availableQty": 1 }] }
```

Đơn tạo xong: backend xóa giỏ theo `X-Cart-Session`; FE cũng `clearCart()` trên client.

## 5. PayOS — task "Trạng thái khi khách quay về" + tạo lại link (Long)

File FE: `src/api/payments.js`. Dùng ở màn quét QR (bước 4) và trang kết quả `/thanh-toan/ket-qua`.

| Method | URL | Trả về |
|---|---|---|
| GET | `/payments/payos/return?orderCode={payosOrderCode}` | `{ payosOrderCode, status, amount, paidAt, order: OrderDetailResponse }` |
| POST | `/payments/payos/{payosOrderCode}/link` | `{ payosOrderCode, amount, checkoutUrl, qrCode, expiresAt, order }` |

- `status`: `PENDING` | `PAID` | `CANCELLED` | `FAILED` | `EXPIRED`.
- **returnUrl / cancelUrl** gửi PayOS = `https://<web-external>/thanh-toan/ket-qua` (PayOS tự gắn `?code=&id=&cancel=&status=&orderCode=`).
  Trang này **không tin `status` trên URL** — chỉ đọc `orderCode` rồi gọi API trên; payment còn `PENDING` thì backend hỏi lại PayOS để đối soát.
- FE hỏi lại: trang kết quả mỗi 2 giây (tối đa 5 lần, sau đó có nút "Kiểm tra lại"); màn quét QR mỗi 5 giây (khách quét bằng app ngân hàng) → khác `PENDING` thì chuyển sang trang kết quả.
- "Thanh toán lại" (khi `CANCELLED`/`FAILED`, đơn còn giữ hàng) gọi `POST …/link`: link cũ còn hạn → trả link cũ (đúng mô tả task).
- `qrCode`: FE hiện đang vẽ **QR giả** (`DemoQr` trong `PaymentStep.jsx`). Khi có chuỗi thật cần thêm thư viện vẽ QR (vd. `qrcode.react`) và thay `DemoQr`.
- Đồng hồ đếm ngược dùng `expiresAt` (= `holdExpiresAt`), không cố định 15 phút.

---

## Trạng thái nối

| Mục | FE | Backend |
|---|---|---|
| Hồ sơ + đổi SĐT | Đã nối | `/me/profile`, `/me/phone` + `phone` trên `/auth/me` |
| Địa chỉ | Đã nối | `/me/addresses` |
| Đơn của tôi + hủy | Xong (mock) | Chưa có `/me/orders` (đã có `/orders` cho nhân viên) |
| Yêu cầu | Đã nối | `/me/sales-requests` (+ staff `/sales-requests`) |
| Checkout | Đã nối | `POST /checkout` (+ Idempotency-Key, hold FEFO, stub PayOS) |
| PayOS return / link | Xong (mock) | Chưa có (Long #33); checkout trả stub `checkoutUrl` |

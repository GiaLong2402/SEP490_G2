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

**Đã nối (#29 list/detail):** `GET /me/orders`, `GET /me/orders/{code}` (JWT + `MeCustomerResolver`). Enums trên `/me` là **UPPERCASE**. List trả kèm `lines` (+ `imageUrl` nếu có). Seed: `DH-DEMO-001` / `DH-DEMO-C{id}`.

**Đã nối (#30 hủy chưa thanh toán):** `POST /me/orders/{code}/cancel`. `canCancel` = `PENDING` và chưa `PAID`. Hủy → `state=CANCELLED`, `paymentStatus=UNPAID`, giải phóng hold, gọi hủy link PayOS (#38) rồi payment `PENDING` → `CANCELLED` + clear `checkoutUrl`. Hủy lần 2 / không đủ điều kiện → 409.

| Method | URL | Trả về |
|---|---|---|
| GET | `/me/orders?page=1&pageSize=100` | `PageResponse<MeOrderDetailResponse>` (có `lines`) |
| GET | `/me/orders/{code}` | `MeOrderDetailResponse` (theo **mã đơn**, không theo id) |
| POST | `/me/orders/{code}/cancel` | `MeOrderDetailResponse` sau khi hủy; không hủy được → 409 |

Field FE đọc: `id, code, state, paymentStatus, fulfillmentMethod, subtotal, discountTotal, shippingFee, total, paidAmount, placedAt, holdExpiresAt, note, canCancel, customer{name,phone}, address, deliveries[0]{scheduledDate,timeWindow}, payments[]{method,direction,status,paidAt,checkoutUrl,payosOrderCode}, events[]{id,eventType,description,createdAt}, lines[]{productName,qtyKhay,unitPrice,lineTotal,imageUrl?}`.

Trạng thái hiển thị (`constants/orderStatus.js` → `orderUiStatus`):

| OrderState + PaymentStatus | Khách thấy |
|---|---|
| `PENDING` + chưa `PAID` | Chờ thanh toán |
| `PENDING` đã trả, `CONFIRMED`, `PREPARING` | Đang chuẩn bị |
| `TO_SHIP`, `PARTIAL` | Đang giao / sẵn sàng lấy |
| `DELIVERED`, `COMPLETED` | Hoàn thành |
| `CANCELLED` | Đã hủy |

Hủy đơn (#30+#38): chỉ khi `canCancel` (đơn chờ thanh toán) → giải phóng hàng giữ + hủy link PayOS thật. UI: `CancelOrderDialog` trên chi tiết đơn và bước QR checkout.

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

- `status`:
  - `PAID` — đã thanh toán, đơn còn hiệu lực.
  - `PENDING` — chưa có tiền, còn trong giờ giữ hàng hoặc 2 phút ân hạn → FE hỏi tiếp.
  - `EXPIRED` — hết giờ + ân hạn mà chưa có tiền, hoặc job đã hủy đơn vì hết hạn (event `expired`).
  - `CANCELLED` — đơn bị khách / cửa hàng hủy (`order.state = CANCELLED`), hoặc link bị hủy trên PayOS khi đơn còn.
  - `REFUND` — đã nhận tiền nhưng đơn đã hủy (tiền về muộn) → cửa hàng hoàn tiền.
  - `FAILED` — giao dịch lỗi.
- **returnUrl / cancelUrl** gửi PayOS = `https://<web-external>/thanh-toan/ket-qua` (PayOS tự gắn `?code=&id=&cancel=&status=&orderCode=`).
  Trang này **không tin `status` trên URL** — chỉ đọc `orderCode` rồi gọi API trên; payment còn `PENDING` thì backend **luôn hỏi PayOS trước** khi kết luận.
- **Mốc thời gian QR** (giờ giữ hàng `payment_hold_minutes` = 15, ân hạn `payment_grace_minutes` = 2; FE: `constants/orderStatus.js`):
  - còn 1 phút: màn QR nhắc sắp hết giờ;
  - còn 10 giây: **che QR** (không vẽ mã, ẩn nội dung chuyển khoản, ẩn nút hủy), hiện "Mã QR đã đóng, đang kiểm tra giao dịch" với hai trường hợp đã / chưa chuyển khoản; thẻ đơn hiện "Đang kiểm tra thanh toán" (vẫn ở chip Chờ thanh toán);
  - link PayOS hết hạn ở giờ giữ hàng **+ 2 phút**; job chỉ hủy đơn sau mốc này (vẫn hỏi PayOS trước) → khách quét QR sát giờ vẫn kịp trả;
  - FE coi là hết hạn sau giờ giữ hàng + 3 phút (2 phút ân hạn + 1 nhịp job).
- FE hỏi lại: màn QR mỗi 5 giây (mỗi 3 giây khi QR đã đóng) → khác `PENDING` thì sang trang kết quả; trang kết quả mỗi 2 giây (tối đa 5 lần, hoặc tới khi hết thời gian kiểm tra).
- "Thanh toán lại" (khi `CANCELLED`/`FAILED` mà đơn còn) gọi `POST …/link`: link cũ còn hạn → trả link cũ.
- `qrCode`: FE vẽ QR thật bằng `qrcode.react` trong `PaymentStep.jsx` (chuỗi VietQR/EMV hoặc URL ảnh từ PayOS).
- Đồng hồ đếm ngược dùng `expiresAt` (= `holdExpiresAt`, mốc 15 phút khách thấy), không cố định 15 phút.
- **Job hết hạn (#37):** mỗi phút quét đơn quá `holdExpiresAt` + ân hạn → hỏi PayOS; đã `PAID` thì ghi nhận; chưa trả thì hủy link, nhả hold, hủy đơn, ghi event `expired`. Ops: `POST /sales/hold-expire`.
- **Cửa hàng hủy đơn (#38):** `POST /orders/{id}/cancel` khóa luôn link PayOS còn chờ (giống khách tự hủy).
- **Tiền về muộn (#35):** payment đã hủy (hoặc đơn đã hủy) mà PayOS báo đã trả → ghi nhận tiền, không bao giờ tự giao:
  - đơn hủy vì **hết hạn** → thử giữ lại đủ hàng; đủ → đơn chạy tiếp (event `late_paid_reheld`); thiếu → nhả phần giữ dở, đơn vẫn hủy;
  - đơn bị **khách / cửa hàng hủy** → không cứu đơn;
  - trường hợp phải hoàn: đơn `REFUND_PENDING`, payment `rawPayload.needsRefund`, event `needs_refund`, thông báo cho Chủ (bảng `notification`); Chủ hoàn qua "Ghi nhận hoàn tiền" (#40). Khách thấy "Đã hủy" + "cửa hàng sẽ liên hệ hoàn tiền".
- **Lệch số tiền (#34):** khách có thể sửa số tiền trong app ngân hàng. Backend so tiền thực nhận (webhook `amount`; khi hỏi PayOS thì `amountPaid`, không có thì cộng `transactions[].amount`) với tiền đơn:
  - **thừa** → vẫn tính đã thanh toán; payment ghi đúng tiền thực nhận, `reconcileFlag=OVER`, ghi chú "thừa Xđ, cần hoàn lại", báo Chủ. Webhook tới sau khi đã xác nhận vẫn được soi lại (và lưu vào `rawPayload.webhook` làm bằng chứng);
  - **thiếu** → không tính đã thanh toán, `reconcileFlag=SHORT`, báo Chủ.
  - Chi tiết đơn: `paidAmount > total` → khách thấy "Bạn đã chuyển …, nhiều hơn tiền đơn …, cửa hàng sẽ liên hệ hoàn lại phần thừa" (tự ẩn khi đã hoàn).
- **Ghi nhận hoàn (#40):** nội bộ `POST /payments/refund` — tạo phiếu `direction=refund` (TM/CK), clear `needsRefund`, cập nhật `paymentStatus` đơn (`refunded` khi net=0). Không gọi PayOS refund API; không tự hủy đơn.

---

## Trạng thái nối

| Mục | FE | Backend |
|---|---|---|
| Hồ sơ + đổi SĐT | Đã nối | `/me/profile`, `/me/phone` + `phone` trên `/auth/me` |
| Địa chỉ | Đã nối | `/me/addresses` |
| Đơn của tôi (#29) | Đã nối | `/me/orders` list + detail |
| Hủy đơn chưa TT (#30+#38) | Đã nối | `POST /me/orders/{code}/cancel` (+ hủy link PayOS) |
| Yêu cầu | Đã nối | `/me/sales-requests` (+ staff `/sales-requests`) |
| Checkout | Đã nối | `POST /checkout` (+ Idempotency-Key, hold FEFO, stub PayOS) |
| PayOS create link (#33) | Đã nối | `POST /checkout` + `POST /payments/payos/{orderCode}/link` |
| PayOS webhook (#34+#35) | — | `POST /payments/payos/webhook` (muộn sau hủy: re-hold hoặc `needs_refund`) |
| PayOS return (#36) | Đã nối | `GET /payments/payos/return?orderCode=` (JWT; PENDING → hỏi lại PayOS) |
| PayOS hold expire (#37) | — | Job + `POST /sales/hold-expire` (hỏi PayOS → hủy link / hủy đơn) |
| Ghi nhận hoàn (#40) | Internal PaymentsPage | `POST /payments/refund` (clear `needsRefund`, `paymentStatus`) |

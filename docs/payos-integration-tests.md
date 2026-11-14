# Integration test thanh toán PayOS (Inter 2)

Tài liệu bàn giao integration test (IT) phần **Thanh toán PayOS** của Long, ứng với task tracking *"Test thanh toán: Unit + Integration"*. Unit test xem `docs/payos-unit-tests.md`.

IT gọi API thật qua MockMvc, chạy trên Postgres online (profile **`it-external`**, đọc `.env`, không cần Docker):
- **PayOS được giả lập** (`@MockitoBean PayosClient`): không gọi PayOS thật, không tốn tiền.
- **Webhook ký thật** bằng checksum key giả (`it-checksum-key`), đi qua đúng kiểm tra chữ ký.
- **Không để rác trên DB dùng chung:** class đặt `@Transactional`, nên mọi dữ liệu test (user, khách, lô, tồn, đơn, payment, event, notification) tự **rollback** sau mỗi test. Đã đối chiếu số dòng 10 bảng và tổng tồn kho trước / sau khi chạy: **không đổi**.
- **Token:** cấp thẳng bằng `TokenService`. Lý do: `/auth/login` chạy ngoài transaction nên không thấy user test chưa commit. Bước kiểm tra JWT vẫn đi qua filter thật.

**Nhánh:** `feature/external-toast-shipping-fee`

---

## Map tiêu chí tracking → IT

| Tiêu chí | Test |
|----------|------|
| Chữ ký sai → bỏ qua | `webhook_badSignature_returns401_noChange`, `webhook_tamperedAmount_returns401` |
| Webhook gửi 2 lần → 1 lần ghi | `webhook_valid_marksPaid_andResendIsIdempotent`, `webhook_lessAmount_flagsShort_notPaid` (báo Chủ 1 lần) |
| Số tiền lệch → mismatch | `webhook_lessAmount_flagsShort_notPaid`, `webhook_moreAmount_paidAndFlagsOver`, `return_paidThenOverAmount_viaReturn` |
| Webhook đến sau 15 phút | `holdExpire_thenLateWebhook_reholdsAndPaid`, `holdExpire_lateWebhook_noStock_needsRefund`, `customerCancel_locksLink_thenLateWebhook_needsRefund` |
| Chạy thử sandbox (ngrok) | `PayosSandboxCreateIT` + mục **Chạy thử sandbox + ngrok** bên dưới |

---

## Lệnh chạy

```powershell
cd hbsisms-api
.\mvnw.cmd test "-Dtest=PayosPaymentApiIntegrationTest,PayosSandboxCreateIT" "-DargLine=-Duser.timezone=UTC"
```

File: `hbsisms-api/src/test/java/com/hbsisms/feature/payment/payos/PayosPaymentApiIntegrationTest.java`

Kết quả gần nhất (2026-11-14): **BUILD SUCCESS — PayosPaymentApiIntegrationTest 17 + PayosSandboxCreateIT 1 = 18 tests, 0 failures.** Số dòng 10 bảng và tổng tồn kho trên DB online trước / sau khi chạy: không đổi.

### PayosPaymentApiIntegrationTest

| # | Test method | Hành vi | Kết quả |
|---|-------------|---------|---------|
| 1 | `checkout_createsPendingPayos_andRelinkReusesLink` | `POST /checkout` → payment PENDING + link; `POST /payments/payos/{code}/link` trả link cũ, PayOS tạo link 1 lần; hạn link = giờ giữ hàng + ân hạn | pass |
| 2 | `relink_foreignCustomer404_unauthenticatedBlocked` | khách khác → 404; chưa đăng nhập → bị chặn | pass |
| 3 | `webhook_valid_marksPaid_andResendIsIdempotent` | webhook đúng → SUCCESS, đơn PAID, lưu mã ngân hàng; gửi lại → vẫn 1 event `paid` | pass |
| 4 | `webhook_badSignature_returns401_noChange` | chữ ký sai → 401, payment vẫn PENDING | pass |
| 5 | `webhook_tamperedAmount_returns401` | sửa số tiền, giữ chữ ký cũ → 401 | pass |
| 6 | `webhook_unknownOrderOrFailCode_acksNoChange` | orderCode lạ / mã ≠ 00 → 200, không đổi | pass |
| 7 | `webhook_lessAmount_flagsShort_notPaid` | chuyển thiếu → không PAID, gắn "Thiếu", báo Chủ 1 lần | pass |
| 8 | `webhook_moreAmount_paidAndFlagsOver` | chuyển thừa → PAID, ghi đúng tiền nhận, gắn "Thừa"; `GET /payments` trả `excessAmount` | pass |
| 9 | `return_payosPaid_marksPaid_andUrlStatusIgnored` | URL gắn `status=PAID` mà PayOS chưa trả → vẫn PENDING; PayOS PAID → PAID | pass |
| 10 | `return_paidThenOverAmount_viaReturn` | web hỏi PayOS thấy `amountPaid` thừa → PAID + "Thừa"; webhook tới sau không báo trùng, lưu mã ngân hàng | pass |
| 11 | `holdExpire_thenLateWebhook_reholdsAndPaid` | quá 15 + 2 phút → `POST /sales/hold-expire` hủy đơn, khóa link; tiền về muộn, còn hàng → giữ lại, PAID | pass |
| 12 | `holdExpire_withinGrace_notCancelled` | quá 15 phút nhưng còn ân hạn → job chưa hủy | pass |
| 13 | `holdExpire_lateWebhook_noStock_needsRefund` | hết hạn + hết hàng, tiền về muộn → đơn vẫn hủy, chờ hoàn, báo Chủ | pass (sau khi sửa DB) |
| 14 | `customerCancel_locksLink_thenLateWebhook_needsRefund` | khách hủy → khóa link; vẫn lỡ chuyển → không cứu đơn, chờ hoàn | pass (sau khi sửa DB) |
| 15 | `staffCancel_locksPayosLink` | `POST /orders/{id}/cancel` → khóa link, payment CANCELLED | pass |
| 16 | `refund_evidenceRules_partialThenSort` | thiếu bằng chứng → 400; hoàn phần thừa → 201, đơn vẫn PAID; hoàn vượt → 409; sắp xếp mới / cũ nhất | pass |
| 17 | `payments_reconcileFlagPatchAndFilter` | gắn tay "Trùng", lọc theo nhãn; nhãn sai → 400; khách không vào được trang Thanh toán | pass |

### PayosSandboxCreateIT (đã có)

| Test method | Hành vi | Kết quả |
|-------------|---------|---------|
| `createPaymentRequest_sandboxReturnsCheckoutUrl` | gọi **PayOS sandbox thật** tạo link 2.000đ (không thanh toán, không tốn tiền); tự bỏ qua khi `.env` không có khóa | pass |

---

## Lỗi phát hiện khi chạy IT (đã sửa 2026-11-14)

**DB online: cột `sales_order.payment_status` không nhận giá trị `REFUND_PENDING` ("chờ hoàn tiền").**
- Ràng buộc kiểm tra (`sales_order_payment_status_check`) trên DB chỉ cho: UNPAID, WAITING, PAID, PARTIAL, DEBT, DIFF, REFUNDED.
- Code đã thêm `REFUND_PENDING` (commit `cc4ea30`), nhưng `ddl-auto: update` **không cập nhật ràng buộc cũ**.

Ảnh hưởng trên DB online:
- **Tiền về muộn khi đơn đã hủy** (webhook PayOS): lưu đơn báo lỗi → cả việc ghi nhận tiền bị hủy → API trả 500 cho PayOS. Tiền đã vào tài khoản nhưng hệ thống không ghi, không báo Chủ.
- **Cửa hàng hủy đơn đã thanh toán** (`SalesOrderService.cancel` → `REFUND_PENDING`): cũng báo lỗi tương tự.

Đã sửa trên DB online ngày 2026-11-14 (Long đồng ý). Chỉ đổi ràng buộc, không đụng dữ liệu; đã kiểm tra dữ liệu hiện có hợp lệ trước khi đổi. **Ai dùng DB riêng (local) cần chạy lại câu này:**

```sql
ALTER TABLE hbsisms.sales_order DROP CONSTRAINT sales_order_payment_status_check;
ALTER TABLE hbsisms.sales_order ADD CONSTRAINT sales_order_payment_status_check
  CHECK (payment_status IN ('UNPAID','WAITING','PAID','PARTIAL','DEBT','DIFF','REFUND_PENDING','REFUNDED'));
```

---

## Chạy thử sandbox + ngrok (thủ công)

Cách dựng (API, ngrok, URL webhook, xem tin báo ở `http://127.0.0.1:4040`): xem `docs/payos-local-sandbox.md`.

### Đã chạy thật

| Ngày | Đơn | Tình huống | Kết quả |
|------|-----|-----------|---------|
| 2026-11-14 | SO-2026-0080 | đơn 2.000đ, quét QR trả | PAID; webhook về 200 |
| 2026-11-14 | SO-2026-0081 | đơn 2.000đ, sửa số tiền chuyển 10.000đ | PAID, tự gắn "Thừa", ghi đúng 10.000đ; hoàn 8.000đ (phiếu TT-0009, mã FT26318W9T9Z) |

### Bài thử tay còn lại (điền kết quả khi chạy)

| # | Thử gì | Cách làm | Đạt khi | Kết quả |
|---|--------|----------|---------|---------|
| 1 | PayOS gửi lại tin | ngrok → chọn webhook cũ → **Replay** | 200, không thêm giao dịch / event | **Đạt** (2026-11-14): replay webhook thật của SO-2026-0081 (orderCode 130059, 10.000đ) → 200; DB trước/sau giống hệt (3 event, 2 giao dịch, 1 thông báo, vẫn "Thừa") |
| 2 | Chữ ký sai | ngrok → **Replay with modifications**, sửa 1 ký tự `signature` | 401, DB không đổi | **Đạt** (2026-11-14): cùng tin trên, đổi 1 ký tự chữ ký, gửi vào `localhost:8080` (đích ngrok chuyển tới) → 401 "Chữ ký PayOS không hợp lệ"; DB không đổi |
| 3 | Không tin URL | đơn chưa trả, mở `/thanh-toan/ket-qua?orderCode=…&status=PAID` | trang vẫn báo đang chờ | **Đạt** (2026-11-14): SO-2026-0087 (130065), link có `status=PAID` → sau hơn 15 giây trang vẫn "Đang xác nhận thanh toán"; nội bộ vẫn "Đang xử lý"; DB: payment PENDING, đơn WAITING, chỉ có event `created` |
| 4 | Hủy trên trang PayOS | mở link PayOS → Hủy | trang kết quả báo đã hủy, có "Thanh toán lại" | **Không áp dụng**: web đã bỏ nút "Mở trang PayOS", khách chỉ hủy bằng nút "Hủy đơn hàng" trên màn QR (bài 5). Nhánh PayOS báo CANCELLED vẫn có unit test `getReturn_remoteCancelledOrNotFound_mapsByHold` |
| 5 | Khách hủy rồi quét lại | hủy ở web khách → quét QR cũ | ngân hàng báo mã không hợp lệ | **Đạt — trường hợp B** (2026-11-14): SO-2026-0089. Quét QR trước, khách hủy 15:30:48 (link PayOS bị khóa, nhả hàng), rồi mới bấm chuyển → **ngân hàng vẫn cho chuyển**, PayOS vẫn gửi webhook 15:31:31. Hệ thống ghi nhận tiền (SUCCESS, FT26318L5Z2R), đơn giữ **Đã hủy** + `REFUND_PENDING`, event `needs_refund`, payment "Cần hoàn", báo Chủ (`payment_refund` → owner1). Form hoàn điền sẵn 2.000đ. **Kết luận: khóa link PayOS không chặn được lần chuyển đã quét sẵn — luồng tiền về muộn là bắt buộc.** |
| 6 | Cửa hàng hủy rồi quét lại | hủy ở web nội bộ → quét QR cũ | như bài 5 | **Đạt — trường hợp B** (2026-11-14): SO-2026-0090 (130068). Quét QR trước; cửa hàng hủy 15:46:58 ("Khách yêu cầu hủy đơn") → màn QR khách tự chuyển "Đơn hàng đã bị hủy", nhả hàng; bấm chuyển → ngân hàng vẫn cho, webhook 15:47:43 trả 200 → SUCCESS (FT263185DTY8), đơn **Đã hủy** + `REFUND_PENDING`, event `needs_refund`, "Cần hoàn", báo Chủ; trang khách "Đã nhận tiền, chờ hoàn tiền" |
| 7 | Để hết hạn | đặt đơn, để yên ~18 phút | còn 1 phút nhắc; 10 giây che QR; sang "Hết hạn", nhả hàng | **Đạt** (2026-11-14): SO-2026-0087, hết giờ giữ hàng 14:54:09. Còn 00:58 → nhắc "Còn dưới 1 phút"; còn 10 giây → QR che, "Đang kiểm tra giao dịch" đếm 02:09; ~14:56 (hết ân hạn) → "Đơn đã hết hạn thanh toán"; job 14:57:00 → đơn CANCELLED, event `expired`, payment CANCELLED + xóa link, sổ kho RELEASE +1 khay (khớp HOLD −1) |
| 8 | Quét sát giờ | quét lúc còn ~15 giây, chờ QR bị che mới bấm chuyển | "Đang kiểm tra thanh toán" → đã thanh toán | **Đạt** (2026-11-14): SO-2026-0088 (130066, 2.000đ), đặt 15:06:10 → hết giờ giữ hàng ~15:21:10; quét lúc còn 00:14, chuyển sau khi QR bị che; webhook về 15:21:36 (**~26 giây sau khi hết giờ, trong ân hạn**) → 200, payment SUCCESS (mã FT263181C41W, đối soát "Không"), đơn PAID, không bị hủy, hàng vẫn giữ (chỉ có HOLD, không RELEASE); web hiện "Thanh toán thành công" |

---

## Giới hạn đã biết

- **Khóa link PayOS không chặn được lần chuyển đã quét sẵn** (bài 5, 6): khách quét QR trước khi đơn bị hủy vẫn chuyển được, PayOS vẫn báo về. Hệ thống xử lý bằng luồng tiền về muộn (ghi nhận tiền, "Cần hoàn", báo Chủ); Chủ hoàn qua form "Hoàn tiền".

- **Chuyển thiếu** không thử thật được: ngân hàng khóa cứng số tiền khi quét QR. Đã cover bằng unit test + IT giả lập webhook.
- **Tự nhận "Trùng"** (khách chuyển 2 lần) và **xử lý sâu chuyển thiếu** (cộng dồn, tự đánh dấu cần hoàn khi hết hạn): tạm để lại; Chủ gắn tay ở trang Thanh toán.
- **Chuyển tay ngoài QR** (tự nhập số tài khoản, thiếu mã PayOS trong nội dung): PayOS không ghép được với đơn nên hệ thống không biết; Chủ đối chiếu sao kê.
- **Job hết hạn** (`POST /sales/hold-expire`) xử lý mọi đơn quá hạn trong DB, kể cả đơn ngoài test. Trong IT, PayOS mặc định trả "không hỏi được" nên các đơn khác chỉ bị hoãn, và mọi thay đổi đều rollback.

# Unit test thanh toán PayOS (Inter 2)

Tài liệu bàn giao unit test phần **Thanh toán PayOS** của Long, ứng với task tracking *"Test thanh toán: Unit + Integration"*.

Unit test chạy độc lập: không cần DB, không gọi PayOS (repository và PayOS đều được mock).

**Nhánh:** `feature/external-toast-shipping-fee`

---

## Map tiêu chí tracking → unit

| Tiêu chí | Cover bởi |
|----------|-----------|
| Chữ ký sai → bỏ qua | `PayosWebhookServiceTest.handle_badSignature_throws401` + `PayosWebhookSignatureTest.verifyWebhook_rejectsTamperedSignature / verifyWebhook_rejectsTamperedAmount` |
| Webhook gửi 2 lần → 1 lần ghi | `PayosWebhookServiceTest.handle_duplicate_isIdempotent` + `handle_success_marksPaidOnce` + `handle_amountMismatch_flagsShortAndNotifiesOwnerOnce` (báo Chủ 1 lần) |
| Số tiền lệch → mismatch | Thiếu: `handle_amountMismatch_flagsShortAndNotifiesOwnerOnce`, `PayosReturnServiceTest.getReturn_payosPaidShortAmount_staysPending`, `PayosOrderExpireHandlerTest.expire_payosPaidShort_flagsShortThenCancelsKeepingNote`. Thừa: `handle_amountOver_marksPaidAndFlagsOver`, `handle_overpaidWebhookAfterAlreadyPaid_flagsOverOnce`, `getReturn_payosPaidWithMoreMoney_marksPaidAndFlagsOver`, `expire_whenPayosPaidWithMoreMoney_marksPaidAndFlagsOver` |
| Webhook đến sau 15 phút | `PayosLatePaymentHandlerTest` (4), `PayosWebhookServiceTest.handle_cancelledPayment_* / handle_orderCancelledPaymentPending_*`, `PayosOrderExpireHandlerTest` (8), `PayosReturnServiceTest.getReturn_pastHoldWithinGrace_*` |
| Chạy thử sandbox | Xem `docs/payos-integration-tests.md` (`PayosSandboxCreateIT` + chạy tay với ngrok) |

---

## Lệnh chạy

```powershell
cd hbsisms-api
.\mvnw.cmd test "-Dtest=PayosWebhookServiceTest,PayosWebhookSignatureTest,PayosSignatureTest,PayosReturnServiceTest,PayosOrderExpireHandlerTest,PayosLatePaymentHandlerTest,PayosLinkServiceTest,PayosLinkCancellerTest,PayosClientMockTest,PayosPropertiesTest,PayosAmountMismatchRecorderTest,PaymentServiceTest,PaymentListItemResponseTest,OwnerNotifierTest,HoldExpireServiceTest"
```

> Chỉ liệt kê tên class, **không** dùng `*Test` chung chung: tránh chạy kèm các IT profile `it-external` (ghi vào DB online dùng chung).

Kết quả gần nhất (2026-11-14): **BUILD SUCCESS — 105 tests, 0 failures.**

| Class | Số test |
|---|---|
| PayosWebhookServiceTest | 16 |
| PayosReturnServiceTest | 15 |
| PayosOrderExpireHandlerTest | 8 |
| PayosLatePaymentHandlerTest | 4 |
| PayosLinkServiceTest | 7 |
| PayosLinkCancellerTest | 3 |
| PayosClientMockTest | 11 |
| PayosAmountMismatchRecorderTest | 5 |
| PaymentServiceTest | 18 |
| PaymentListItemResponseTest | 3 |
| OwnerNotifierTest | 2 |
| HoldExpireServiceTest | 3 |
| PayosPropertiesTest | 5 |
| PayosSignatureTest | 2 |
| PayosWebhookSignatureTest | 3 |

Thư mục: `hbsisms-api/src/test/java/com/hbsisms/feature/` (`payment/payos/`, `payment/`, `payment/dto/`, `system/`, `sales/`).

---

## 1. Webhook — `PayosWebhookServiceTest`

| Test method | Hành vi | Kết quả |
|-------------|---------|---------|
| `handle_badSignature_throws401` | chữ ký sai → 401, không ghi DB | pass |
| `handle_success_marksPaidOnce` | đúng tiền → payment SUCCESS, đơn PAID, 1 event `paid` | pass |
| `handle_duplicate_isIdempotent` | PayOS gửi lại (đã SUCCESS, đã lưu tin) → không ghi gì | pass |
| `handle_amountMismatch_flagsShortAndNotifiesOwnerOnce` | thiếu tiền → không PAID, gắn "Thiếu", báo Chủ 1 lần dù gửi lại | pass |
| `handle_amountOver_marksPaidAndFlagsOver` | thừa tiền → PAID, ghi đúng tiền thực nhận, gắn "Thừa", báo Chủ | pass |
| `handle_overpaidWebhookAfterAlreadyPaid_flagsOverOnce` | đã xác nhận trước (web hỏi PayOS), webhook thừa tới sau → gắn "Thừa" 1 lần | pass |
| `handle_matchingWebhookAfterAlreadyPaid_storesEvidenceOnly` | đã xác nhận trước, webhook đúng tiền → chỉ lưu tin làm bằng chứng | pass |
| `handle_afterPaid_fillsMissingReference` | webhook tới sau → lưu mã giao dịch ngân hàng còn thiếu | pass |
| `handle_cancelledPayment_delegatesToLateHandler` | payment đã hủy → chuyển sang xử lý tiền về muộn | pass |
| `handle_orderCancelledPaymentPending_delegatesToLateHandler` | đơn đã hủy nhưng link còn PENDING → tiền về muộn | pass |
| `handle_cancelledPaymentShortAmount_flagsShortOnly` | đơn đã hủy + chuyển thiếu → chỉ gắn "Thiếu" | pass |
| `handle_cancelledPaymentOverAmount_flagsOverThenLate` | đơn đã hủy + chuyển thừa → ghi đủ tiền nhận, rồi xử lý tiền về muộn | pass |
| `handle_unknownOrderCode_acksWithoutWrite` | orderCode lạ → trả 200, không ghi | pass |
| `handle_missingDataOrOrderCode_acksWithoutWrite` | thiếu `data` / `orderCode` → 200, không ghi | pass |
| `handle_nonSuccessCode_doesNotMarkPaid` | `code` ≠ 00 / `data.code` ≠ 00 / `success=false` → không PAID | pass |
| `handle_missingAmount_acksWithoutWrite` | thiếu `amount` → không ghi | pass |

## 2. Chữ ký — `PayosWebhookSignatureTest`, `PayosSignatureTest`

| Test method | Hành vi | Kết quả |
|-------------|---------|---------|
| `verifyWebhook_matchesOfficialDocsVector` | khớp ví dụ trong tài liệu PayOS | pass |
| `verifyWebhook_rejectsTamperedSignature` | sửa chữ ký → sai | pass |
| `verifyWebhook_rejectsTamperedAmount` | sửa số tiền, giữ chữ ký → sai | pass |
| `signPaymentRequest_isStableHex` | chữ ký tạo link ổn định | pass |
| `signPaymentRequest_usesAlphabeticallyOrderedFields` | ký theo đúng thứ tự trường | pass |

## 3. Trạng thái khi khách quay về — `PayosReturnServiceTest`

| Test method | Hành vi | Kết quả |
|-------------|---------|---------|
| `getReturn_wrongOwner_404` | khách khác → 404, không hỏi PayOS | pass |
| `getReturn_alreadySuccess_returnsPaidWithoutPayos` | đã SUCCESS → PAID, không hỏi PayOS | pass |
| `getReturn_pendingAndPayosPaid_marksPaid` | PENDING + PayOS PAID → ghi nhận đã trả | pass |
| `getReturn_payosPaidWithMoreMoney_marksPaidAndFlagsOver` | PayOS báo `amountPaid` thừa → PAID + "Thừa" | pass |
| `getReturn_payosPaidShortAmount_staysPending` | PayOS báo `amountPaid` thiếu → không PAID | pass |
| `getReturn_pastHoldWithinGrace_asksPayosAndKeepsPending` | quá giờ, còn ân hạn, chưa có tiền → PENDING | pass |
| `getReturn_pastHoldWithinGrace_payosPaid_marksPaid` | quét sát giờ, tiền về trong ân hạn → PAID | pass |
| `getReturn_pastGrace_payosStillPending_returnsExpired` | quá ân hạn → EXPIRED | pass |
| `getReturn_orderCancelledByExpireJob_returnsExpired` | job đã hủy (event `expired`) → EXPIRED | pass |
| `getReturn_orderCancelledByShop_returnsCancelled` | cửa hàng / khách hủy → CANCELLED | pass |
| `getReturn_paidButOrderCancelled_returnsRefund` | đã trả nhưng đơn hủy → REFUND | pass |
| `getReturn_cancelledOrFailedPayment_mapsStatus` | link hủy / lỗi → CANCELLED / FAILED | pass |
| `getReturn_remoteCancelledOrNotFound_mapsByHold` | PayOS báo CANCELLED (còn giờ) → CANCELLED; EXPIRED / NOT_FOUND (quá giờ) → EXPIRED | pass |
| `getReturn_payosGetFails_keepsPending` | PayOS lỗi mạng → PENDING, không ghi | pass |
| `getReturn_payosDisabled_doesNotCallRemote` | PayOS tắt → không gọi PayOS | pass |

## 4. Job hết hạn — `PayosOrderExpireHandlerTest`, `HoldExpireServiceTest`

| Test method | Hành vi | Kết quả |
|-------------|---------|---------|
| `expire_whenPayosPaid_marksPaidWithoutCancel` | PayOS đã trả → ghi nhận, không hủy | pass |
| `expire_whenPayosPaidWithMoreMoney_marksPaidAndFlagsOver` | PayOS đã trả thừa → ghi nhận + "Thừa" | pass |
| `expire_payosPaidShort_flagsShortThenCancelsKeepingNote` | PayOS trả thiếu → "Thiếu" rồi hủy, giữ ghi chú lệch tiền | pass |
| `expire_whenStillPending_cancelsLinkAndOrder` | chưa trả → hủy link, nhả hàng, hủy đơn, event `expired` | pass |
| `expire_whenPayosGetEmpty_defersWithoutRelease` | PayOS lỗi → hoãn, không nhả hàng | pass |
| `expire_whenPayosNotFound_cancelsOrderWithoutRemoteCancel` | PayOS mã 101 → hủy tại hệ thống, không gọi hủy link | pass |
| `expire_orderAlreadyCancelledOrPaid_noop` | đơn đã hủy / đã trả → không đụng | pass |
| `expire_pendingWithoutOrderCode_defers` | payment thiếu mã PayOS → hoãn | pass |
| `run_processesEachExpiredOrderViaHandler` | job gọi xử lý từng đơn hết hạn | pass |
| `run_countsOnlyResolvedOrders` | chỉ đếm đơn xử lý xong | pass |
| `run_usesCutoffNowMinusGrace` | chỉ quét đơn quá giờ giữ hàng + ân hạn (đọc từ `app_config`) | pass |

## 5. Tiền về muộn — `PayosLatePaymentHandlerTest`

| Test method | Hành vi | Kết quả |
|-------------|---------|---------|
| `handleLatePaid_expiredOrder_fullHold_continuesAsPaid` | đơn hủy vì hết hạn, còn đủ hàng → giữ lại, đơn chạy tiếp | pass |
| `handleLatePaid_expiredOrder_notEnoughStock_staysCancelledAndNeedsRefund` | hết hạn, thiếu hàng → vẫn hủy, chờ hoàn, báo Chủ | pass |
| `handleLatePaid_orderCancelledOnPurpose_alwaysRefundsWithoutRehold` | khách / cửa hàng chủ động hủy → luôn chờ hoàn, không giữ lại hàng | pass |
| `handleLatePaid_paymentWithoutOrder_onlyMarksPaid` | payment không gắn đơn → chỉ ghi nhận tiền | pass |

## 6. Tạo link / khóa link — `PayosLinkServiceTest`, `PayosLinkCancellerTest`

| Test method | Hành vi | Kết quả |
|-------------|---------|---------|
| `createForCheckout_callsPayosOnceAndPersistsPending` | gọi PayOS 1 lần, lưu PENDING; hạn link = giờ giữ hàng + ân hạn | pass |
| `createForCheckout_whenDisabled_returns503` | PayOS tắt → 503 | pass |
| `createForCheckout_invalidAmount_rejectedWithoutCallingPayos` | tổng tiền 0 / âm / lẻ / trống → 400, không gọi PayOS | pass |
| `getActiveLink_reusesWithoutCallingPayos` | link còn hạn → trả link cũ | pass |
| `getActiveLink_expired_returns409` | link hết hạn → 409 | pass |
| `getActiveLink_notPendingOrNoUrlOrForeign_rejected` | đã trả / mất link → 409; khách khác / không có mã → 404 | pass |
| `shortDescription_truncatesToNine` | nội dung chuyển khoản ≤ 9 ký tự | pass |
| `cancelPendingLinks_locksOnlyPendingPayos` | chỉ khóa payment PayOS đang chờ | pass |
| `cancelPendingLinks_noOrderId_doesNothing` | đơn chưa có id → không làm gì | pass |
| `cancelPendingLinks_payosFails_stillCancelsLocally` | PayOS hủy lỗi → vẫn hủy tại hệ thống | pass |

## 7. Gọi PayOS — `PayosClientMockTest` (giả lập server PayOS)

| Test method | Hành vi | Kết quả |
|-------------|---------|---------|
| `createPaymentRequest_parsesSuccessPayload` | đọc link + QR khi tạo | pass |
| `getPaymentRequest_parsesPaidStatus` | đọc trạng thái PAID | pass |
| `getPaymentRequest_readsAmountPaid` | đọc số tiền khách thực chuyển | pass |
| `getPaymentRequest_sumsTransactionsWhenNoAmountPaid` | không có `amountPaid` → cộng các giao dịch | pass |
| `getPaymentRequest_non00_returnsEmpty` | mã lỗi → coi như không hỏi được | pass |
| `getPaymentRequest_code101_returnsNotFound` | mã 101 → NOT_FOUND | pass |
| `getPaymentRequest_httpError_returnsEmpty` | PayOS lỗi 500 → không hỏi được | pass |
| `cancelPaymentRequest_returnsTrueOnCode00` | hủy link thành công | pass |
| `cancelPaymentRequest_non00_returnsFalse` | hủy bị từ chối → false | pass |
| `cancelPaymentRequest_httpError_returnsFalse` | lỗi 500 → false | pass |
| `cancelPaymentRequest_blankId_skipsCall` | không có mã → không gọi | pass |

## 8. Ghi lệch tiền / báo Chủ — `PayosAmountMismatchRecorderTest`, `OwnerNotifierTest`

| Test method | Hành vi | Kết quả |
|-------------|---------|---------|
| `record_short_setsFlagNoteRawAndNotifies` | thiếu → "Thiếu", ghi chú số liệu, lưu bằng chứng, báo Chủ | pass |
| `record_alreadyFlagged_isNoop` | đã gắn rồi → không ghi, không báo trùng | pass |
| `recordOverpaid_setsReceivedAmountAndExcess` | thừa → ghi đúng tiền nhận, ghi chú "thừa 8.000đ", báo Chủ | pass |
| `recordOverpaid_twice_notifiesOnce` | gọi 2 lần → chỉ ghi / báo 1 lần | pass |
| `noOrder_linksToPayments` | không có đơn → thông báo dẫn tới trang Thanh toán | pass |
| `notifyOwners_writesOnePerActiveOwner` | mỗi Chủ đang hoạt động nhận 1 thông báo | pass |
| `notifyOwners_noOwner_writesNothing` | không có Chủ → không ghi | pass |

## 9. Đối soát & hoàn tiền — `PaymentServiceTest`, `PaymentListItemResponseTest`

| Test method | Hành vi | Kết quả |
|-------------|---------|---------|
| `create_rejectsAmountAboveOutstanding` / `create_marksOrderPaidWhenFullyCovered` / `create_cancelledOrder_returns409` | ghi nhận thu tay | pass |
| `markReconcile_setsShortFlag_andAudits` | gắn nhãn + ghi audit | pass |
| `markReconcile_clearWithNone_isIdempotent` | bỏ nhãn | pass |
| `markReconcile_missingPayment_returns404` / `markReconcile_rejectsInvalidFlag` | sai id → 404, sai nhãn → 400 | pass |
| `search_passesStatusChannelOrderIdAndReconcileFlagToSpec` | lọc + mặc định mới nhất trước | pass |
| `search_sortAsc_oldestFirst` | `sort=asc` → cũ nhất trước | pass |
| `search_rejectsInvalidStatus` / `search_rejectsInvalidChannel` | bộ lọc sai → 400 | pass |
| `recordRefund_fullAmount_setsRefundedAndClearsNeedsRefund` | hoàn đủ → REFUNDED, bỏ "cần hoàn" | pass |
| `recordRefund_partialOverpaid_keepsPaidStatus` | hoàn phần thừa → đơn vẫn PAID, không đổi trạng thái đơn | pass |
| `recordRefund_amountAboveNet_returns409` | hoàn vượt số đã thu → 409 | pass |
| `recordRefund_cancelledOrder_stillAllowed` | đơn đã hủy vẫn hoàn được | pass |
| `recordRefund_rejectsPayosMethod` | không hoàn qua PayOS | pass |
| `recordRefund_bankTransferWithoutReference_returns400` | chuyển khoản thiếu mã giao dịch (bằng chứng) → 400 | pass |
| `recordRefund_cashWithoutNote_returns400` | tiền mặt thiếu ghi chú (bằng chứng) → 400 | pass |
| `from_overFlag_readsExcessAndPayer` | trả số tiền thừa + tài khoản người chuyển cho form hoàn | pass |
| `from_noFlag_excessNull_blankPayerNull` | không thừa / ô trống → null, không lỗi | pass |
| `needsRefund_parsesBooleanOrString` | đọc cờ "cần hoàn" | pass |

## 10. Cấu hình — `PayosPropertiesTest`

| Test method | Hành vi | Kết quả |
|-------------|---------|---------|
| `bindsFromAppPaymentPayosPrefix_andEnabledWhenSandbox` | đọc cấu hình `app.payment.payos.*` | pass |
| `enabled_modeOff_returnsFalse` | `off` → tắt | pass |
| `enabled_sandboxWithAllKeys_returnsTrue` / `enabled_sandboxMissingKey_returnsFalse` | sandbox cần đủ khóa | pass |
| `enabled_liveWithAllKeys_returnsTrue` | live đủ khóa → bật | pass |

---

## Liên quan (file của người khác — chỉ liệt kê, không sửa)

| Test | Hành vi |
|------|---------|
| `MeOrderServiceCancelTest.cancelMine_pendingPayos_callsCancelLink` | khách hủy đơn → khóa link PayOS |
| `MeOrderServiceCancelTest.cancelMine_payosCancelFails_stillCancelsLocally` | PayOS lỗi → vẫn hủy |
| `MeOrderServiceCancelTest.cancelMine_nonPayosPending_doesNotCallPayos` | đơn không PayOS → không gọi PayOS |
| `SalesOrderServiceTest.cancel_releasesHold` (verify `cancelPendingLinks`) | cửa hàng hủy → khóa link PayOS |

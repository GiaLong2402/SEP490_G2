import { CheckIcon, ClipboardIcon, ClockIcon, CloseIcon, PackageIcon, TruckIcon } from '@/components/icons/Icons'

/**
 * Trạng thái hiển thị cho khách — gộp từ enum backend:
 *   OrderState: PENDING CONFIRMED PREPARING TO_SHIP PARTIAL DELIVERED COMPLETED CANCELLED
 *   PaymentStatus: UNPAID WAITING PAID PARTIAL DEBT DIFF REFUND_PENDING REFUNDED
 * tone khớp class .order-status--<tone> (styles/shared/order.css).
 */
export const ORDER_STATUS = {
  awaiting_payment: { label: 'Chờ thanh toán', tone: 'warning', Icon: ClockIcon },
  preparing: { label: 'Đang chuẩn bị', tone: 'info', Icon: PackageIcon },
  shipping: { label: 'Đang giao', tone: 'accent', Icon: TruckIcon },
  completed: { label: 'Hoàn thành', tone: 'success', Icon: CheckIcon },
  cancelled: { label: 'Đã hủy', tone: 'muted', Icon: CloseIcon },
  expired: { label: 'Hết hạn', tone: 'danger', Icon: ClockIcon },
}

/** Nhãn trên thẻ đơn khi QR đã đóng, đang chờ xác nhận tiền về (đơn vẫn nằm ở chip Chờ thanh toán) */
export const PAYMENT_CHECKING_STATUS = { label: 'Đang kiểm tra thanh toán', tone: 'warning', Icon: ClockIcon }

/** Thứ tự nút lọc trong trang Đơn hàng */
export const ORDER_FILTERS = ['awaiting_payment', 'preparing', 'shipping', 'completed', 'expired', 'cancelled']

/**
 * Mốc thời gian thanh toán QR — khớp backend:
 *  - còn 1 phút: nhắc khách sắp hết giờ
 *  - còn 10 giây: che QR, chuyển sang "Đang kiểm tra thanh toán"
 *  - quá giờ giữ hàng 2 phút (app_config payment_grace_minutes): PayOS ngừng nhận tiền, job hủy đơn
 *    (+1 phút vì job chạy mỗi phút) → coi là hết hạn
 */
export const QR_WARN_SECONDS = 60
export const QR_CLOSE_SECONDS = 10
export const PAYMENT_GRACE_SECONDS = 120
const JOB_BUFFER_SECONDS = 60
export const PAYMENT_CHECK_UNTIL_SECONDS = PAYMENT_GRACE_SECONDS + JOB_BUFFER_SECONDS

/**
 * Giai đoạn của đơn chờ thanh toán theo giờ giữ hàng:
 *  'open' → 'closing' (còn ≤ 1 phút) → 'checking' (QR đã đóng, chờ tiền về) → 'lapsed' (hết hạn)
 */
export function paymentPhase(holdExpiresAt, now = Date.now()) {
  if (!holdExpiresAt) return 'open'
  const left = (new Date(holdExpiresAt).getTime() - now) / 1000
  if (!Number.isFinite(left)) return 'open'
  if (left > QR_WARN_SECONDS) return 'open'
  if (left > QR_CLOSE_SECONDS) return 'closing'
  if (left > -PAYMENT_CHECK_UNTIL_SECONDS) return 'checking'
  return 'lapsed'
}

/** Thời điểm (ms) giai đoạn thanh toán đổi tiếp theo — để hẹn giờ cập nhật màn hình; null nếu không còn. */
export function nextPaymentPhaseAt(holdExpiresAt, now = Date.now()) {
  if (!holdExpiresAt) return null
  const hold = new Date(holdExpiresAt).getTime()
  if (!Number.isFinite(hold)) return null
  const marks = [hold - QR_WARN_SECONDS * 1000, hold - QR_CLOSE_SECONDS * 1000, hold + PAYMENT_CHECK_UNTIL_SECONDS * 1000]
  return marks.find((t) => t > now) ?? null
}

/** Chưa trả tiền (đơn B2C: UNPAID / WAITING). Đã thu rồi hoàn (REFUND_PENDING / REFUNDED) không tính. */
const isUnpaid = (paymentStatus) => !paymentStatus || paymentStatus === 'UNPAID' || paymentStatus === 'WAITING'

/**
 * expiredEvent: đơn có sự kiện 'expired' (job hết hạn thanh toán của backend đã hủy đơn).
 * Backend xóa holdExpiresAt khi hủy, nên đơn đã hủy chỉ nhận ra hết hạn qua sự kiện này.
 */
export function orderUiStatus(state, paymentStatus, holdExpiresAt, expiredEvent = false, now = Date.now()) {
  const unpaid = isUnpaid(paymentStatus)
  if (state === 'CANCELLED') {
    // Hủy vì quá giờ thanh toán → Hết hạn; khách / cửa hàng hủy, hoặc đã thu tiền phải hoàn → Đã hủy
    if (unpaid && (expiredEvent || paymentPhase(holdExpiresAt, now) === 'lapsed')) return 'expired'
    return 'cancelled'
  }
  if (state === 'COMPLETED' || state === 'DELIVERED') return 'completed'
  if (state === 'TO_SHIP' || state === 'PARTIAL') return 'shipping'
  if (state === 'PENDING' && unpaid) {
    return paymentPhase(holdExpiresAt, now) === 'lapsed' ? 'expired' : 'awaiting_payment'
  }
  return 'preparing' // PENDING đã trả, CONFIRMED, PREPARING
}

/**
 * Trạng thái theo thời gian thực của đơn đã qua mapper (trang đang mở thì đồng hồ vẫn chạy):
 *  { uiStatus, checking } — checking = QR đã đóng, đang chờ xác nhận tiền về (vẫn ở chip Chờ thanh toán)
 */
export function liveOrderStatus(order, now = Date.now()) {
  if (order.uiStatus !== 'awaiting_payment' || !order.holdExpiresAt) {
    return { uiStatus: order.uiStatus, checking: false }
  }
  const phase = paymentPhase(order.holdExpiresAt, now)
  if (phase === 'lapsed') return { uiStatus: 'expired', checking: false }
  return { uiStatus: 'awaiting_payment', checking: phase === 'checking' }
}

/** Đơn đã thu tiền nhưng bị hủy → cửa hàng hoàn tiền */
export const isRefundOrder = (order) =>
  order.uiStatus === 'cancelled' && (order.paymentStatus === 'REFUND_PENDING' || order.paymentStatus === 'REFUNDED')

/** RequestStatus backend: NEW QUOTING ORDERED REJECTED CLOSED */
export const REQUEST_STATUS = {
  NEW: { label: 'Đang xem xét', tone: 'warning', Icon: ClockIcon },
  QUOTING: { label: 'Đang báo giá', tone: 'info', Icon: ClipboardIcon },
  ORDERED: { label: 'Đã chốt', tone: 'success', Icon: CheckIcon },
  REJECTED: { label: 'Không đáp ứng được', tone: 'danger', Icon: CloseIcon },
  CLOSED: { label: 'Đã đóng', tone: 'muted', Icon: CloseIcon },
}

export const REQUEST_FILTERS = ['NEW', 'QUOTING', 'ORDERED', 'REJECTED', 'CLOSED']

/** Trạng thái khách cần làm gì đó (thanh toán) → làm nổi */
export const needsCustomerAction = (uiStatus) => uiStatus === 'awaiting_payment' || uiStatus === 'ORDERED'

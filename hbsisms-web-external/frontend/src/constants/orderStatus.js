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
}

/** Thứ tự nút lọc trong trang Đơn hàng */
export const ORDER_FILTERS = ['awaiting_payment', 'preparing', 'shipping', 'completed', 'cancelled']

export function orderUiStatus(state, paymentStatus) {
  if (state === 'CANCELLED') return 'cancelled'
  if (state === 'COMPLETED' || state === 'DELIVERED') return 'completed'
  if (state === 'TO_SHIP' || state === 'PARTIAL') return 'shipping'
  if (state === 'PENDING' && paymentStatus !== 'PAID') return 'awaiting_payment'
  return 'preparing' // PENDING đã trả, CONFIRMED, PREPARING
}

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

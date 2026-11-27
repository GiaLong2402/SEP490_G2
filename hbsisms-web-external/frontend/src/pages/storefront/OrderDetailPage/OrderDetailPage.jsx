import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import AccountShell from '@/components/storefront/AccountShell/AccountShell'
import CancelOrderDialog from '@/components/storefront/CancelOrderDialog/CancelOrderDialog'
import LoadState from '@/components/ui/LoadState'
import {
  ArrowLeftIcon,
  CalendarIcon,
  CheckIcon,
  ClipboardIcon,
  ClockIcon,
  CloseIcon,
  HeadsetIcon,
  HistoryIcon,
  MapPinIcon,
  PackageIcon,
  PhoneIcon,
  QrIcon,
  ShieldIcon,
  StoreIcon,
  TruckIcon,
} from '@/components/icons/Icons'
import payosLogo from '@/assets/payment/payos.svg'
import { ordersApi } from '@/api/orders'
import {
  ORDER_STATUS,
  PAYMENT_CHECKING_STATUS,
  isRefundOrder,
  liveOrderStatus,
  nextPaymentPhaseAt,
} from '@/constants/orderStatus'
import { ROUTES, orderReceiptPath, resumePaymentPath } from '@/constants/routes'
import { useAsync } from '@/hooks/useAsync'
import { formatDateTime, formatSchedule } from '@/utils/date'
import { formatPrice } from '@/utils/formatPrice'

/** Bước hiện tại trên hành trình theo trạng thái hiển thị */
const JOURNEY_STEP = {
  awaiting_payment: 0,
  awaiting_settlement: 1,
  preparing: 2,
  shipping: 3,
  completed: 4,
  cancelled: -1,
  expired: -1,
}

const HEADLINE = {
  awaiting_payment: 'Đơn hàng đang chờ bạn thanh toán',
  awaiting_settlement: 'Hàng đã sẵn sàng — vui lòng tất toán phần còn lại',
  preparing: 'Cửa hàng đang sơ chế và đóng khay',
  shipping: 'Đơn hàng đang trên đường đến bạn',
  completed: 'Đơn hàng đã hoàn tất',
  cancelled: 'Đơn hàng đã bị hủy',
  expired: 'Đơn đã hết hạn thanh toán',
}

function awaitingHeadline(order) {
  if (order.isPreorder && order.paymentStatus === 'UNPAID') {
    return 'Đơn đặt trước đang chờ bạn thanh toán tiền cọc'
  }
  return HEADLINE.awaiting_payment
}

function journeySteps(isDelivery) {
  return [
    { label: 'Đã đặt hàng', Icon: ClipboardIcon },
    { label: 'Đã thanh toán', Icon: QrIcon },
    { label: 'Đang chuẩn bị', Icon: PackageIcon },
    isDelivery ? { label: 'Đang giao', Icon: TruckIcon } : { label: 'Sẵn sàng lấy', Icon: StoreIcon },
    { label: isDelivery ? 'Đã giao' : 'Đã nhận', Icon: CheckIcon },
  ]
}

/** Chi tiết đơn — GET /me/orders/{code} qua ordersApi (đơn của người khác → 404) */
export default function OrderDetailPage() {
  const { code } = useParams()
  const { data: order, loading, error, reload, setData } = useAsync((signal) => ordersApi.getMine(code, signal), [code])

  const breadcrumbs = [
    { label: 'Trang chủ', to: ROUTES.HOME },
    { label: 'Đơn & Yêu cầu', to: ROUTES.ORDERS },
    { label: order ? order.displayCode : 'Chi tiết đơn' },
  ]

  return (
    <AccountShell title="Chi tiết đơn hàng" breadcrumbs={breadcrumbs}>
      {error?.status === 404 ? (
        <div className="od-card od-empty">
          <h2>Không tìm thấy đơn hàng</h2>
          <p>Đơn hàng #{code} không tồn tại hoặc bạn không có quyền xem.</p>
          <Link to={ROUTES.ORDERS} className="btn btn--primary btn--pill">
            Về danh sách đơn
          </Link>
        </div>
      ) : (
        <LoadState loading={loading} error={error} onRetry={reload}>
          {order && <OrderDetail order={order} onChanged={setData} />}
        </LoadState>
      )}
    </AccountShell>
  )
}

function OrderDetail({ order, onChanged }) {
  const [cancelOpen, setCancelOpen] = useState(false)
  const [now, setNow] = useState(() => Date.now())
  // Đơn chờ thanh toán: QR đóng → "Đang kiểm tra thanh toán" → hết hạn, tự đổi khi trang đang mở
  const { uiStatus, checking } = liveOrderStatus(order, now)
  const refund = isRefundOrder(order)
  const status = checking ? PAYMENT_CHECKING_STATUS : ORDER_STATUS[uiStatus]
  const step = JOURNEY_STEP[uiStatus]
  const steps = journeySteps(order.isDelivery)
  const stepTimes = [order.placedAt, order.payment?.paidAt]
  const schedule = formatSchedule(order.schedule)
  const awaitingPayment = uiStatus === 'awaiting_payment' && !checking
  const awaitingSettlement = uiStatus === 'awaiting_settlement'
  const awaitingDeposit = awaitingPayment && order.isPreorder && order.paymentStatus === 'UNPAID'
  const canCancel = order.canCancel && !checking
  const depositDue = order.depositDueAt || order.holdExpiresAt
  const remainingBalance = Math.max(0, order.total - (order.paidAmount || 0))
  const payAmount = awaitingDeposit && order.depositAmount > 0 ? order.depositAmount : order.total
  const remainingAfterDeposit =
    awaitingDeposit && order.depositAmount > 0
      ? Math.max(0, order.total - order.depositAmount)
      : awaitingSettlement
        ? remainingBalance
        : 0

  const nextChange = uiStatus === 'awaiting_payment' ? nextPaymentPhaseAt(order.holdExpiresAt, now) : null
  useEffect(() => {
    if (!nextChange) return undefined
    const timer = window.setTimeout(() => setNow(Date.now()), Math.min(nextChange - Date.now() + 300, 2 ** 31 - 1))
    return () => window.clearTimeout(timer)
  }, [nextChange])

  // Đang kiểm tra: hỏi lại đơn mỗi 5 giây để thấy ngay khi tiền về hoặc đơn bị hủy
  useEffect(() => {
    if (!checking) return undefined
    const ac = new AbortController()
    const id = window.setInterval(() => {
      ordersApi
        .getMine(order.code, ac.signal)
        .then((fresh) => {
          if (fresh.uiStatus !== 'awaiting_payment') onChanged(fresh)
        })
        .catch(() => {})
    }, 5000)
    return () => {
      window.clearInterval(id)
      ac.abort()
    }
  }, [checking, order.code, onChanged])

  return (
    <div className="od">
      {/* Đầu trang: mã đơn + trạng thái */}
      <section className="od-card od-head">
        <div>
          <h2>Đơn hàng {order.displayCode}</h2>
          <p>Đặt lúc {formatDateTime(order.placedAt)}</p>
        </div>
        <div className="od-head__chips">
          <span className={`order-status order-status--${status.tone}`}>
            <status.Icon size={16} />
            {status.label}
          </span>
          {order.isPaid ? (
            <span className="order-status order-status--success">
              <CheckIcon size={16} /> Đã thanh toán
            </span>
          ) : refund ? (
            <span className="order-status order-status--warning">
              <ClockIcon size={16} /> {order.paymentStatus === 'REFUNDED' ? 'Đã hoàn tiền' : 'Chờ hoàn tiền'}
            </span>
          ) : awaitingDeposit ? (
            <span className="order-status order-status--warning">
              <ClockIcon size={16} /> Chờ thanh toán cọc
            </span>
          ) : order.isDeposited ? (
            <span className="order-status order-status--info">
              <CheckIcon size={16} /> {uiStatus === 'cancelled' ? 'Đã mất cọc' : 'Đã cọc'}
            </span>
          ) : uiStatus === 'awaiting_payment' ? (
            <span className="order-status order-status--warning">
              <ClockIcon size={16} /> Chưa thanh toán
            </span>
          ) : null}
        </div>
      </section>

      {/* Hành trình */}
      <section className="od-card">
        <h3 className="od-title">
          <TruckIcon size={22} /> Hành trình đơn hàng
        </h3>
        <div className={`od-banner od-banner--${status.tone}`}>
          <span className="od-banner__icon" aria-hidden="true">
            <status.Icon size={24} />
          </span>
          <div>
            <strong>
              {checking
                ? 'Đang kiểm tra thanh toán'
                : refund
                  ? 'Đơn đã hủy, cửa hàng sẽ hoàn tiền cho bạn'
                  : uiStatus === 'awaiting_payment'
                    ? awaitingHeadline(order)
                    : order.isDeposited || order.state === 'IN_PRODUCTION'
                      ? 'Đã nhận cọc — cửa hàng đang chuẩn bị / sản xuất'
                      : HEADLINE[uiStatus]}
            </strong>
            {checking && <span>Nếu bạn đã chuyển khoản, vui lòng chờ. Đừng chuyển lại lần nữa.</span>}
            {schedule && !checking && uiStatus !== 'cancelled' && uiStatus !== 'expired' && (
              <span>
                {order.isDelivery ? 'Dự kiến giao' : 'Hẹn lấy hàng'}: {schedule}
              </span>
            )}
            {awaitingDeposit && depositDue && (
              <span>Hạn thanh toán cọc tới {formatDateTime(depositDue)}</span>
            )}
            {!awaitingDeposit && awaitingPayment && order.holdExpiresAt && (
              <span>Hàng được giữ tới {formatDateTime(order.holdExpiresAt)}</span>
            )}
          </div>
        </div>

        {step >= 0 && (
          <ol className="od-journey">
            {steps.map(({ label, Icon }, i) => {
              const state = i < step ? 'is-done' : i === step ? 'is-current' : ''
              const time = stepTimes[i]
              return (
                <li key={label} className={`od-journey__step ${state}`}>
                  <span className="od-journey__dot" aria-hidden="true">
                    <Icon size={22} />
                  </span>
                  <strong>{label}</strong>
                  <span>{time && i <= step ? formatDateTime(time).split(' · ')[0] : i > step ? 'Chưa tới' : ''}</span>
                </li>
              )
            })}
          </ol>
        )}
      </section>

      {/* Nhận hàng + lịch sử */}
      <div className="od-grid">
        <section className="od-card">
          <h3 className="od-title">
            <MapPinIcon size={22} /> Thông tin nhận hàng
          </h3>
          <p className="od-receiver__name">{order.receiver.name}</p>
          <ul className="od-info">
            {order.receiver.phone && (
              <li>
                <PhoneIcon size={18} /> {order.receiver.phone}
              </li>
            )}
            <li>
              <MapPinIcon size={18} /> {order.isDelivery ? order.receiver.address || '—' : 'Nhận tại cửa hàng Hòa Bình'}
            </li>
            <li className="od-info__sep">
              {order.isDelivery ? <TruckIcon size={18} /> : <StoreIcon size={18} />}
              {order.isDelivery ? 'Giao tận nơi' : 'Nhận tại cửa hàng'}
            </li>
            {schedule && (
              <li>
                <CalendarIcon size={18} /> {schedule}
              </li>
            )}
            {order.note && (
              <li>
                <ClipboardIcon size={18} /> {order.note}
              </li>
            )}
          </ul>
        </section>

        <section className="od-card">
          <h3 className="od-title">
            <HistoryIcon size={22} /> Lịch sử đơn hàng
          </h3>
          {order.events.length === 0 ? (
            <p className="od-muted">Chưa có cập nhật.</p>
          ) : (
            <ol className="od-history">
              {order.events.map((h, i) => (
                <li key={h.id ?? i} className={i === 0 ? 'is-latest' : ''}>
                  <time>{formatDateTime(h.at)}</time>
                  <span className="od-history__dot" aria-hidden="true" />
                  <div>
                    <strong>{h.title}</strong>
                  </div>
                </li>
              ))}
            </ol>
          )}
        </section>
      </div>

      {/* Sản phẩm */}
      <section className="od-card">
        <h3 className="od-title">
          <PackageIcon size={22} /> Sản phẩm trong đơn
        </h3>
        <ul className="od-lines">
          {order.lines.map((l) => (
            <li key={l.id ?? l.name + l.packLabel} className="od-line">
              <img src={l.imageUrl} alt="" />
              <div className="od-line__name">
                <strong>{l.name}</strong>
                {l.packLabel && <span>{l.packLabel}</span>}
              </div>
              <span className="od-line__unit">{formatPrice(l.unitPrice)} / khay</span>
              <span className="od-line__qty">× {l.qty}</span>
              <strong className="od-line__total">{formatPrice(l.lineTotal)}</strong>
            </li>
          ))}
        </ul>
      </section>

      {/* Thanh toán */}
      <section className="od-card">
        <h3 className="od-title">
          <ShieldIcon size={22} /> Thông tin thanh toán
        </h3>
        <div className="od-pay">
          <div className={`od-pay__status ${order.isPaid ? 'is-paid' : ''}`}>
            <span className="od-banner__icon" aria-hidden="true">
              {order.isPaid ? <CheckIcon size={24} /> : <ClockIcon size={24} />}
            </span>
            <div>
              <strong>
                {order.isPaid
                  ? 'Đã thanh toán'
                  : order.isDeposited
                    ? 'Đã thanh toán cọc'
                    : refund
                      ? order.paymentStatus === 'REFUNDED'
                        ? 'Đã hoàn tiền'
                        : 'Đã nhận tiền, chờ hoàn tiền'
                      : uiStatus === 'cancelled'
                        ? 'Không thanh toán'
                        : awaitingDeposit
                          ? 'Chờ thanh toán cọc'
                          : 'Chưa thanh toán'}
              </strong>
              <span>Chuyển khoản qua PayOS</span>
              {order.payment?.paidAt && <span>{formatDateTime(order.payment.paidAt)}</span>}
              {/* Khách sửa số tiền chuyển nhiều hơn tiền đơn — tự ẩn khi cửa hàng đã hoàn phần thừa */}
              {order.isPaid && order.paidAmount > order.total && (
                <span className="od-pay__over">
                  Bạn đã chuyển {formatPrice(order.paidAmount)}, thừa {formatPrice(order.paidAmount - order.total)}{' '}
                  so với tiền đơn. Cửa hàng sẽ liên hệ để hoàn lại phần thừa.
                </span>
              )}
            </div>
            <img src={payosLogo} alt="PayOS" className="od-pay__logo" />
          </div>
          <dl className="od-sum">
            <div>
              <dt>Tiền hàng</dt>
              <dd>{formatPrice(order.subtotal)}</dd>
            </div>
            {order.discount > 0 && (
              <div>
                <dt>Giảm giá</dt>
                <dd>−{formatPrice(order.discount)}</dd>
              </div>
            )}
            <div>
              <dt>Phí giao hàng</dt>
              <dd>{order.shippingFee ? formatPrice(order.shippingFee) : 'Miễn phí'}</dd>
            </div>
            <div>
              <dt>Tổng đơn</dt>
              <dd>{formatPrice(order.total)}</dd>
            </div>
            {order.isPreorder && order.depositAmount > 0 && (
              <>
                <div>
                  <dt>
                    Tiền cọc
                    {order.depositPercent > 0 ? ` (${order.depositPercent}%)` : ''}
                  </dt>
                  <dd>{formatPrice(order.depositAmount)}</dd>
                </div>
                {(awaitingDeposit || awaitingSettlement) && (
                  <div>
                    <dt>{awaitingSettlement ? 'Còn lại cần tất toán' : 'Còn lại sau cọc'}</dt>
                    <dd>{formatPrice(remainingAfterDeposit)}</dd>
                  </div>
                )}
              </>
            )}
            <div className="od-sum__total">
              <dt>
                {awaitingDeposit
                  ? 'Cần thanh toán ngay (cọc)'
                  : awaitingSettlement
                    ? 'Cần tất toán ngay'
                    : 'Tổng thanh toán'}
              </dt>
              <dd>
                {formatPrice(
                  awaitingDeposit
                    ? payAmount
                    : awaitingSettlement
                      ? remainingBalance
                      : order.isPaid
                        ? order.total
                        : remainingBalance || order.total,
                )}
              </dd>
            </div>
          </dl>
        </div>
      </section>

      {/* Hỗ trợ + thao tác */}
      <section className="od-card od-help">
        <span className="od-banner__icon" aria-hidden="true">
          <HeadsetIcon size={24} />
        </span>
        <div className="od-help__text">
          <strong>
            {awaitingDeposit
              ? 'Thanh toán tiền cọc để cửa hàng giữ hàng và sản xuất phần thiếu'
              : awaitingPayment
                ? 'Hoàn tất thanh toán để cửa hàng chuẩn bị hàng'
                : 'Cần hỗ trợ với đơn hàng?'}
          </strong>
          <span>
            {checking
              ? 'Hệ thống đang kiểm tra giao dịch (khoảng 2 phút). Nếu bạn chưa chuyển khoản, đơn sẽ tự hủy.'
              : refund
                ? 'Cửa hàng đã nhận tiền nhưng đơn không thể giao. Cửa hàng sẽ liên hệ để hoàn tiền cho bạn.'
                  : awaitingSettlement
                    ? 'Hàng đã đủ. Thanh toán phần còn lại để cửa hàng chuẩn bị giao.'
                    : canCancel
                      ? awaitingSettlement
                        ? 'Hủy lúc này bạn sẽ mất tiền cọc; hàng giữ sẽ trả kho để bán lại.'
                        : awaitingDeposit
                          ? 'Bạn có thể hủy đơn khi chưa thanh toán cọc.'
                          : 'Bạn có thể hủy đơn khi chưa thanh toán.'
                      : uiStatus === 'cancelled' || uiStatus === 'expired'
                        ? uiStatus === 'expired'
                          ? 'Đơn hết hạn thanh toán, hàng đã trả kho. Bạn có thể đặt lại đơn mới.'
                          : order.isDeposited
                            ? 'Đơn đã hủy. Tiền cọc không được hoàn; hàng đã trả kho.'
                            : 'Đơn đã hủy. Liên hệ cửa hàng nếu bạn cần hỗ trợ thêm.'
                        : 'Đơn đã thanh toán không tự hủy được — liên hệ cửa hàng nếu cần đổi hoặc hủy.'}
          </span>
        </div>
        <div className="od-help__actions">
          {canCancel && (
            <button type="button" className="od-cancel" onClick={() => setCancelOpen(true)}>
              <CloseIcon size={18} /> Hủy đơn
            </button>
          )}
          {order.isPaid && (
            <Link to={orderReceiptPath(order.code)} className="od-receipt-btn">
              <ClipboardIcon size={18} /> Xem biên lai
            </Link>
          )}
          {awaitingPayment || awaitingSettlement ? (
            <Link to={resumePaymentPath(order.code)} className="btn btn--primary btn--pill">
              {awaitingDeposit ? 'Thanh toán cọc' : awaitingSettlement ? 'Tất toán PayOS' : 'Thanh toán ngay'}
            </Link>
          ) : (
            <Link to={ROUTES.CONTACT} className="btn btn--primary btn--pill">
              <HeadsetIcon size={18} /> Liên hệ cửa hàng
            </Link>
          )}
        </div>
      </section>

      <Link to={ROUTES.ORDERS} className="od-back">
        <ArrowLeftIcon size={20} /> Quay lại danh sách đơn
      </Link>

      {cancelOpen && (
        <CancelOrderDialog open order={order} onClose={() => setCancelOpen(false)} onCancelled={onChanged} />
      )}
    </div>
  )
}

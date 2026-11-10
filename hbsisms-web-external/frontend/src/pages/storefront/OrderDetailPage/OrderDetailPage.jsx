import { useState } from 'react'
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
  ExternalLinkIcon,
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
import { ORDER_STATUS } from '@/constants/orderStatus'
import { ROUTES, orderReceiptPath } from '@/constants/routes'
import { useAsync } from '@/hooks/useAsync'
import { formatDateTime, formatSchedule } from '@/utils/date'
import { formatPrice } from '@/utils/formatPrice'

/** Bước hiện tại trên hành trình theo trạng thái hiển thị */
const JOURNEY_STEP = { awaiting_payment: 0, preparing: 2, shipping: 3, completed: 4, cancelled: -1 }

const HEADLINE = {
  awaiting_payment: 'Đơn hàng đang chờ bạn thanh toán',
  preparing: 'Cửa hàng đang sơ chế và đóng khay',
  shipping: 'Đơn hàng đang trên đường đến bạn',
  completed: 'Đơn hàng đã hoàn tất',
  cancelled: 'Đơn hàng đã bị hủy',
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
  const status = ORDER_STATUS[order.uiStatus]
  const step = JOURNEY_STEP[order.uiStatus]
  const steps = journeySteps(order.isDelivery)
  const stepTimes = [order.placedAt, order.payment?.paidAt]
  const schedule = formatSchedule(order.schedule)
  const awaitingPayment = order.uiStatus === 'awaiting_payment'

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
          ) : (
            order.uiStatus !== 'cancelled' && (
              <span className="order-status order-status--warning">
                <ClockIcon size={16} /> Chưa thanh toán
              </span>
            )
          )}
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
            <strong>{HEADLINE[order.uiStatus]}</strong>
            {schedule && order.uiStatus !== 'cancelled' && (
              <span>
                {order.isDelivery ? 'Dự kiến giao' : 'Hẹn lấy hàng'}: {schedule}
              </span>
            )}
            {awaitingPayment && order.holdExpiresAt && (
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
                {order.isPaid ? 'Đã thanh toán' : order.uiStatus === 'cancelled' ? 'Không thanh toán' : 'Chưa thanh toán'}
              </strong>
              <span>Chuyển khoản qua PayOS</span>
              {order.payment?.paidAt && <span>{formatDateTime(order.payment.paidAt)}</span>}
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
            <div className="od-sum__total">
              <dt>Tổng thanh toán</dt>
              <dd>{formatPrice(order.total)}</dd>
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
          <strong>{awaitingPayment ? 'Hoàn tất thanh toán để cửa hàng chuẩn bị hàng' : 'Cần hỗ trợ với đơn hàng?'}</strong>
          <span>
            {order.canCancel
              ? 'Bạn có thể hủy đơn khi chưa thanh toán.'
              : order.uiStatus === 'cancelled'
                ? 'Đơn đã hủy. Liên hệ cửa hàng nếu bạn cần hỗ trợ thêm.'
                : 'Đơn đã thanh toán không tự hủy được — liên hệ cửa hàng nếu cần đổi hoặc hủy.'}
          </span>
        </div>
        <div className="od-help__actions">
          {order.canCancel && (
            <button type="button" className="od-cancel" onClick={() => setCancelOpen(true)}>
              <CloseIcon size={18} /> Hủy đơn
            </button>
          )}
          {order.isPaid && (
            <Link to={orderReceiptPath(order.code)} className="od-receipt-btn">
              <ClipboardIcon size={18} /> Xem biên lai
            </Link>
          )}
          {awaitingPayment && order.payment?.checkoutUrl ? (
            <a href={order.payment.checkoutUrl} className="btn btn--primary btn--pill">
              Thanh toán ngay <ExternalLinkIcon size={18} />
            </a>
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

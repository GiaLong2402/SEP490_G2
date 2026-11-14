import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { QRCodeSVG } from 'qrcode.react'
import {
  ArrowRightIcon,
  CalendarIcon,
  ClockIcon,
  CloseIcon,
  HeadsetIcon,
  HelpIcon,
  PackageIcon,
  QrIcon,
  ShieldIcon,
  StoreIcon,
  TruckIcon,
} from '@/components/icons/Icons'
import CancelOrderDialog from '@/components/storefront/CancelOrderDialog/CancelOrderDialog'
import { paymentsApi } from '@/api/payments'
import { PAYMENT_GRACE_SECONDS, paymentPhase } from '@/constants/orderStatus'
import { ROUTES, orderDetailPath } from '@/constants/routes'
import { formatSchedule } from '@/utils/date'
import { formatPrice } from '@/utils/formatPrice'
import payosLogo from '@/assets/payment/payos.svg'

// Thời gian giữ hàng mặc định (app_config payment_hold_minutes = 15) — chỉ dùng vẽ thanh tiến độ
const HOLD_SECONDS = 15 * 60
// Hỏi lại trạng thái thanh toán (khách quét QR bằng app ngân hàng, không quay về returnUrl):
// mỗi 5 giây khi còn QR, mỗi 3 giây khi QR đã đóng và đang chờ xác nhận tiền về
const POLL_MS = 5000
const CHECK_POLL_MS = 3000

/** PayOS trả chuỗi VietQR/EMV hoặc URL ảnh QR. */
function PaymentQr({ value }) {
  if (!value) {
    return <p className="pay-qr__missing">Chưa có mã QR. Vui lòng tải lại trang hoặc liên hệ cửa hàng.</p>
  }
  if (/^(https?:|data:image)/i.test(value)) {
    return <img src={value} alt="Mã QR thanh toán PayOS" className="pay-qr__code pay-qr__code--img" />
  }
  return (
    <QRCodeSVG
      value={value}
      size={220}
      level="M"
      marginSize={1}
      className="pay-qr__code"
      role="img"
      aria-label="Mã QR thanh toán PayOS"
    />
  )
}

function formatClock(sec) {
  return `${String(Math.floor(sec / 60)).padStart(2, '0')}:${String(sec % 60).padStart(2, '0')}`
}

/**
 * Bước 4: quét QR PayOS + đếm ngược giữ hàng + thông tin đơn.
 * - order: đơn đã qua mapper (api/mappers/order.js)
 * - payment: { payosOrderCode, amount, checkoutUrl, qrCode, expiresAt } từ POST /checkout
 */
export default function PaymentStep({ order, payment }) {
  const navigate = useNavigate()
  const [now, setNow] = useState(() => Date.now())
  const [cancelOpen, setCancelOpen] = useState(false)
  const [tipOpen, setTipOpen] = useState(false)
  const holdAt = payment.expiresAt ? new Date(payment.expiresAt).getTime() : now + HOLD_SECONDS * 1000
  const left = Math.max(0, Math.round((holdAt - now) / 1000))
  // open → closing (còn ≤ 1 phút) → checking (còn 10 giây: che QR, chờ tiền về ~2 phút) → lapsed
  const phase = paymentPhase(payment.expiresAt, now)
  const qrClosed = phase === 'checking' || phase === 'lapsed'
  const lapsed = phase === 'lapsed'
  // Đếm hết 2 phút ân hạn (khớp "khoảng 2 phút" trong thông báo)
  const checkLeft = Math.max(0, Math.round((holdAt + PAYMENT_GRACE_SECONDS * 1000 - now) / 1000))
  const resultUrl = `${ROUTES.PAYMENT_RESULT}?orderCode=${payment.payosOrderCode}`

  // Đồng hồ chạy tới hết thời gian kiểm tra
  useEffect(() => {
    if (lapsed) return undefined
    const id = window.setInterval(() => setNow(Date.now()), 1000)
    return () => window.clearInterval(id)
  }, [lapsed])

  // Hỏi lại trạng thái: có kết quả (đã trả / hết hạn / đơn bị hủy) → sang trang kết quả
  useEffect(() => {
    if (lapsed) return undefined
    const id = window.setInterval(
      () => {
        paymentsApi
          .getReturnStatus(payment.payosOrderCode)
          .then((res) => {
            if (res.status !== 'PENDING') navigate(resultUrl)
          })
          .catch(() => {})
      },
      qrClosed ? CHECK_POLL_MS : POLL_MS,
    )
    return () => window.clearInterval(id)
  }, [lapsed, qrClosed, payment.payosOrderCode, navigate, resultUrl])

  return (
    <div className="pay">
      <div className="pay-layout">
        {/* ===== Trái: quét mã ===== */}
        <section className="checkout-card pay-main">
          <div className="pay-main__head">
            <span className="pay-main__icon" aria-hidden="true">
              <QrIcon size={26} />
            </span>
            <div>
              <h2>Quét mã để thanh toán</h2>
              <p>Dùng ứng dụng ngân hàng hoặc ví điện tử hỗ trợ QR</p>
            </div>
            <img src={payosLogo} alt="PayOS" className="pay-main__brand" />
          </div>

          <p className="pay-amount">
            <span>Số tiền thanh toán</span>
            <strong>{formatPrice(payment.amount ?? order.total)}</strong>
          </p>

          <div className={`pay-qr ${qrClosed ? 'is-expired' : ''}`}>
            {/* QR đóng thì không vẽ mã nữa — khách không quét được */}
            {qrClosed ? <span className="pay-qr__blank" aria-hidden="true" /> : <PaymentQr value={payment.qrCode} />}
            {qrClosed && <span className="pay-qr__overlay">Mã QR đã đóng</span>}
          </div>

          {!qrClosed && (
            // PayOS chỉ nhận ra giao dịch khi khách quét QR (nội dung có mã riêng của PayOS, số tiền đúng).
            // Không hiện nội dung để sao chép: chuyển tay sẽ thiếu mã → hệ thống không biết khách đã trả.
            <p className="pay-note">
              <strong>Vui lòng quét mã QR và giữ nguyên số tiền, nội dung chuyển khoản.</strong>
              {/* Rê chuột (máy tính) hoặc chạm (điện thoại) vào dấu hỏi để xem giải thích */}
              <span className={`pay-note__help ${tipOpen ? 'is-open' : ''}`}>
                <button
                  type="button"
                  aria-label="Vì sao cần giữ nguyên?"
                  aria-describedby="pay-note-tip"
                  aria-expanded={tipOpen}
                  onClick={() => setTipOpen((v) => !v)}
                  onBlur={() => setTipOpen(false)}
                >
                  <HelpIcon size={18} />
                </button>
                <span id="pay-note-tip" role="tooltip" className="pay-note__tip">
                  Nếu tự nhập số tài khoản để chuyển, hệ thống sẽ không nhận được thanh toán. Nếu lỡ chuyển sai số tiền,
                  cửa hàng sẽ liên hệ để xử lý.
                </span>
              </span>
            </p>
          )}

          {lapsed ? (
            <div className="pay-closed is-expired" role="status">
              <strong>Đơn đã hết hạn thanh toán</strong>
              <p>Hàng đã trả lại kho. Nếu bạn đã chuyển khoản, cửa hàng sẽ liên hệ để hoàn tiền cho bạn.</p>
              <div className="pay-closed__actions">
                <Link to={ROUTES.PRODUCTS} className="btn btn--primary btn--pill">
                  Đặt đơn mới
                </Link>
                <Link to={ROUTES.HOME} className="btn btn--ghost btn--pill">
                  Về trang chủ
                </Link>
              </div>
            </div>
          ) : qrClosed ? (
            <div className="pay-closed" role="status">
              <strong>
                <span className="pay-status__spinner" aria-hidden="true" />
                Mã QR đã đóng, đang kiểm tra giao dịch
              </strong>
              <ul>
                <li>
                  <b>Nếu bạn ĐÃ chuyển khoản:</b> vui lòng chờ, hệ thống đang kiểm tra (khoảng 2 phút).{' '}
                  <b>Đừng chuyển lại lần nữa.</b>
                </li>
                <li>
                  <b>Nếu bạn CHƯA chuyển khoản:</b> không cần làm gì, đơn sẽ tự hủy. Bạn có thể đặt lại đơn mới.
                </li>
              </ul>
            </div>
          ) : (
            <div className="pay-status" role="status">
              <strong>
                <span className="pay-status__spinner" aria-hidden="true" />
                Đang chờ thanh toán
              </strong>
              <span>Trang tự cập nhật khi giao dịch thành công.</span>
            </div>
          )}
          {phase === 'closing' && (
            <p className="pay-warn" role="alert">
              <ClockIcon size={18} />
              Còn dưới 1 phút. Nếu chưa kịp chuyển khoản, bạn có thể đặt lại đơn sau.
            </p>
          )}

        </section>

        {/* ===== Phải: thời gian + đơn hàng + hỗ trợ ===== */}
        <aside className="pay-side">
          <div className={`pay-timer ${lapsed ? 'is-expired' : qrClosed || phase === 'closing' ? 'is-closing' : ''}`}>
            <ClockIcon size={26} />
            <div>
              <span>{qrClosed && !lapsed ? 'Đang kiểm tra giao dịch' : 'Thời gian thanh toán còn lại'}</span>
              <strong>{qrClosed && !lapsed ? formatClock(checkLeft) : formatClock(left)}</strong>
            </div>
            <div className="pay-timer__bar" aria-hidden="true">
              <span style={{ width: `${qrClosed ? 0 : Math.min(100, (left / HOLD_SECONDS) * 100)}%` }} />
            </div>
            <p>
              {lapsed
                ? 'Hàng đã được trả lại kho.'
                : qrClosed
                  ? 'Hàng vẫn được giữ trong lúc kiểm tra.'
                  : 'Hòa Bình đang giữ hàng cho bạn.'}
            </p>
          </div>

          <div className="checkout-card pay-order">
            <div className="pay-order__head">
              <h2>Thông tin đơn hàng</h2>
              <span className="pay-order__code">{order.displayCode}</span>
            </div>
            <dl className="pay-order__rows">
              <div className="pay-order__row">
                <dt>
                  {order.isDelivery ? <TruckIcon size={18} /> : <StoreIcon size={18} />}
                  <span>Nhận hàng</span>
                </dt>
                <dd>{order.isDelivery ? 'Giao tận nơi' : 'Tại cửa hàng'}</dd>
              </div>
              <div className="pay-order__row">
                <dt>
                  <CalendarIcon size={18} />
                  <span>Ngày giờ nhận</span>
                </dt>
                <dd>{formatSchedule(order.schedule)}</dd>
              </div>
              <div className="pay-order__row">
                <dt>
                  <PackageIcon size={18} />
                  <span>Sản phẩm</span>
                </dt>
                <dd>
                  {order.lines.length} loại · {order.lines.reduce((sum, l) => sum + l.qty, 0)} khay
                </dd>
              </div>
            </dl>
            <p className="pay-order__total">
              <span>Tổng thanh toán</span>
              <strong>{formatPrice(order.total)}</strong>
            </p>
            {/* QR đã đóng: không cho hủy (khách có thể vừa chuyển tiền) */}
            {qrClosed ? (
              <Link to={orderDetailPath(order.code)} className="pay-cancel">
                Xem đơn hàng
              </Link>
            ) : (
              <button type="button" className="pay-cancel" onClick={() => setCancelOpen(true)}>
                <CloseIcon size={18} />
                Hủy đơn hàng
              </button>
            )}
          </div>

          <div className="checkout-card pay-help">
            <span className="pay-main__icon" aria-hidden="true">
              <HeadsetIcon size={24} />
            </span>
            <div>
              <strong>Bạn cần hỗ trợ?</strong>
              <Link to={ROUTES.CONTACT}>
                Liên hệ cửa hàng <ArrowRightIcon size={16} />
              </Link>
            </div>
          </div>

          <p className="checkout-sum__secure">
            <ShieldIcon size={18} />
            Thanh toán an toàn qua PayOS
          </p>
        </aside>
      </div>

      {/* Hướng dẫn 3 bước */}
      <ol className="pay-guide">
        <li>
          <span>1</span> Mở ứng dụng ngân hàng
        </li>
        <li>
          <span>2</span> Quét mã và kiểm tra số tiền
        </li>
        <li>
          <span>3</span> Xác nhận thanh toán
        </li>
      </ol>

      {cancelOpen && (
        <CancelOrderDialog
          open
          order={order}
          onClose={() => setCancelOpen(false)}
          onCancelled={() => navigate(orderDetailPath(order.code))}
        />
      )}
    </div>
  )
}

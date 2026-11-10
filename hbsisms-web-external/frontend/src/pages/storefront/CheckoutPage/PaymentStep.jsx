import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import {
  ArrowRightIcon,
  CalendarIcon,
  ClockIcon,
  CloseIcon,
  CopyIcon,
  ExternalLinkIcon,
  HeadsetIcon,
  PackageIcon,
  QrIcon,
  ShieldIcon,
  StoreIcon,
  TruckIcon,
} from '@/components/icons/Icons'
import CancelOrderDialog from '@/components/storefront/CancelOrderDialog/CancelOrderDialog'
import { paymentsApi } from '@/api/payments'
import { ROUTES, orderDetailPath } from '@/constants/routes'
import { formatSchedule } from '@/utils/date'
import { formatPrice } from '@/utils/formatPrice'
import payosLogo from '@/assets/payment/payos.svg'

// Thời gian giữ hàng mặc định (app_config payment_hold_minutes = 15) — chỉ dùng vẽ thanh tiến độ
const HOLD_SECONDS = 15 * 60
// Hỏi lại trạng thái thanh toán mỗi 5 giây (khách quét QR bằng app ngân hàng, không quay về returnUrl)
const POLL_MS = 5000

const secondsUntil = (iso) => (iso ? Math.max(0, Math.round((new Date(iso) - Date.now()) / 1000)) : HOLD_SECONDS)

/**
 * Hình QR GIẢ (không quét được) dựng từ seed.
 * Khi nối PayOS: vẽ chuỗi payment.qrCode bằng thư viện QR (vd. qrcode.react) — xem API_CONTRACT.md.
 */
function DemoQr({ seed }) {
  const n = 25
  let x = [...seed].reduce((s, c) => s * 31 + c.charCodeAt(0), 7) >>> 0
  const rand = () => {
    x = (x * 1103515245 + 12345) >>> 0
    return x / 4294967296
  }
  const inFinder = (r, c) =>
    (r < 8 && c < 8) || (r < 8 && c >= n - 8) || (r >= n - 8 && c < 8)
  const inLogo = (r, c) => r >= 9 && r <= 15 && c >= 9 && c <= 15
  const cells = []
  for (let r = 0; r < n; r++) {
    for (let c = 0; c < n; c++) {
      if (!inFinder(r, c) && !inLogo(r, c) && rand() > 0.52) cells.push([r, c])
    }
  }
  const finder = (r, c) => (
    <g key={`f${r}${c}`}>
      <rect x={c} y={r} width="7" height="7" rx="1.2" fill="currentColor" />
      <rect x={c + 1} y={r + 1} width="5" height="5" rx="0.8" fill="#fff" />
      <rect x={c + 2} y={r + 2} width="3" height="3" rx="0.6" fill="currentColor" />
    </g>
  )
  return (
    <svg viewBox={`0 0 ${n} ${n}`} className="pay-qr__code" role="img" aria-label="Mã QR thanh toán">
      {cells.map(([r, c]) => (
        <rect key={`${r}-${c}`} x={c} y={r} width="1" height="1" fill="currentColor" />
      ))}
      {finder(0, 0)}
      {finder(0, n - 7)}
      {finder(n - 7, 0)}
    </svg>
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
  const [left, setLeft] = useState(() => secondsUntil(payment.expiresAt))
  const [copied, setCopied] = useState(false)
  const [cancelOpen, setCancelOpen] = useState(false)
  const expired = left === 0
  const transferNote = order.code
  const resultUrl = `${ROUTES.PAYMENT_RESULT}?orderCode=${payment.payosOrderCode}`

  // Đếm ngược theo hạn server trả về
  useEffect(() => {
    if (expired) return undefined
    const id = window.setInterval(() => setLeft(secondsUntil(payment.expiresAt)), 1000)
    return () => window.clearInterval(id)
  }, [expired, payment.expiresAt])

  // Hỏi lại trạng thái: đã trả → sang trang kết quả
  useEffect(() => {
    if (expired) return undefined
    const id = window.setInterval(() => {
      paymentsApi
        .getReturnStatus(payment.payosOrderCode)
        .then((res) => {
          if (res.status !== 'PENDING') navigate(resultUrl)
        })
        .catch(() => {})
    }, POLL_MS)
    return () => window.clearInterval(id)
  }, [expired, payment.payosOrderCode, navigate, resultUrl])

  function copyNote() {
    navigator.clipboard?.writeText(transferNote).then(() => {
      setCopied(true)
      window.setTimeout(() => setCopied(false), 1800)
    })
  }

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

          <div className={`pay-qr ${expired ? 'is-expired' : ''}`}>
            <DemoQr seed={payment.qrCode || order.code} />
            <span className="pay-qr__logo" aria-hidden="true">
              <QrIcon size={22} />
            </span>
            {expired && <span className="pay-qr__overlay">Mã đã hết hạn</span>}
          </div>

          <div className="pay-note">
            <span>
              Nội dung chuyển khoản: <strong>{transferNote}</strong>
            </span>
            <button type="button" onClick={copyNote} aria-label="Sao chép nội dung chuyển khoản">
              <CopyIcon size={18} />
              {copied ? 'Đã sao chép' : 'Sao chép'}
            </button>
          </div>

          {expired || !payment.checkoutUrl ? (
            <span className="cart-summary__pay pay-open is-disabled" aria-disabled="true">
              Mở trang thanh toán PayOS
              <ExternalLinkIcon size={20} />
            </span>
          ) : (
            <a href={payment.checkoutUrl} className="cart-summary__pay pay-open">
              Mở trang thanh toán PayOS
              <ExternalLinkIcon size={20} />
            </a>
          )}

          {expired ? (
            <p className="pay-status is-expired">Hết thời gian thanh toán. Đơn sẽ được hủy và hàng trả lại kho.</p>
          ) : (
            <div className="pay-status" role="status">
              <strong>
                <span className="pay-status__spinner" aria-hidden="true" />
                Đang chờ thanh toán
              </strong>
              <span>Trang tự cập nhật khi giao dịch thành công.</span>
            </div>
          )}

        </section>

        {/* ===== Phải: thời gian + đơn hàng + hỗ trợ ===== */}
        <aside className="pay-side">
          <div className={`pay-timer ${expired ? 'is-expired' : ''}`}>
            <ClockIcon size={26} />
            <div>
              <span>Thời gian thanh toán còn lại</span>
              <strong>{formatClock(left)}</strong>
            </div>
            <div className="pay-timer__bar" aria-hidden="true">
              <span style={{ width: `${Math.min(100, (left / HOLD_SECONDS) * 100)}%` }} />
            </div>
            <p>{expired ? 'Hàng đã được trả lại kho.' : 'Hòa Bình đang giữ hàng cho bạn.'}</p>
          </div>

          <div className="checkout-card pay-order">
            <div className="pay-order__head">
              <h2>Thông tin đơn hàng</h2>
              <span className="pay-order__code">{order.displayCode}</span>
            </div>
            <dl>
              <div>
                <dt>{order.isDelivery ? <TruckIcon size={18} /> : <StoreIcon size={18} />} Nhận hàng</dt>
                <dd>{order.isDelivery ? 'Giao tận nơi' : 'Tại cửa hàng'}</dd>
              </div>
              <div>
                <dt>
                  <CalendarIcon size={18} /> Ngày giờ nhận
                </dt>
                <dd>{formatSchedule(order.schedule)}</dd>
              </div>
              <div>
                <dt>
                  <PackageIcon size={18} /> Sản phẩm
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
            {expired ? (
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

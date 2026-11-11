import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { QRCodeSVG } from 'qrcode.react'
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

/** PayOS trả chuỗi VietQR/EMV hoặc URL ảnh QR. */
function PaymentQr({ value }) {
  if (!value) {
    return <p className="pay-qr__missing">Chưa có mã QR — mở trang PayOS bên dưới.</p>
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
            <PaymentQr value={payment.qrCode} />
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

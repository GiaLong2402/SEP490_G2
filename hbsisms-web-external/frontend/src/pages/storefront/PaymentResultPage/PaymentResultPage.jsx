import { useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import PageBanner from '@/components/storefront/PageBanner/PageBanner'
import PaymentSuccessCard from '@/components/storefront/PaymentSuccessCard/PaymentSuccessCard'
import { ClockIcon, CloseIcon, InfoIcon, RefreshIcon } from '@/components/icons/Icons'
import { paymentsApi } from '@/api/payments'
import { paymentPhase } from '@/constants/orderStatus'
import { ROUTES, orderDetailPath, resumePaymentPath } from '@/constants/routes'
import { formatPrice } from '@/utils/formatPrice'
import './PaymentResultPage.css'

// Đang chờ webhook: hỏi lại mỗi 2 giây, tối đa 5 lần rồi để khách tự bấm "Kiểm tra lại".
// Riêng lúc QR đã đóng và đang trong 2 phút kiểm tra thì hỏi tiếp cho tới khi có kết quả.
const RETRY_MS = 2000
const MAX_AUTO_RETRY = 5

const inCheckWindow = (order) => order?.uiStatus === 'awaiting_payment' && paymentPhase(order.holdExpiresAt) === 'checking'

/**
 * Trang returnUrl / cancelUrl của PayOS: /thanh-toan/ket-qua?orderCode=...
 * KHÔNG tin status trên URL — luôn hỏi backend (GET /payments/payos/return), task của Long.
 */
export default function PaymentResultPage() {
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const orderCode = params.get('orderCode')

  const [result, setResult] = useState(null) // { status, amount, order }
  const [error, setError] = useState(null)
  const [tries, setTries] = useState(0)
  const [checking, setChecking] = useState(true)
  const [relinking, setRelinking] = useState(false)

  useEffect(() => {
    if (!orderCode) return undefined
    const ac = new AbortController()
    let alive = true
    const timer = window.setTimeout(
      () => {
        if (alive) setChecking(true)
        paymentsApi
          .getReturnStatus(orderCode, ac.signal)
          .then((res) => {
            if (!alive) return
            setResult(res)
            setError(null)
            setChecking(false)
            if (res.status === 'PENDING' && (tries < MAX_AUTO_RETRY || inCheckWindow(res.order))) setTries((t) => t + 1)
          })
          .catch((err) => {
            if (!alive || err.name === 'AbortError') return
            setError(err)
            setChecking(false)
          })
      },
      tries === 0 ? 0 : RETRY_MS,
    )
    return () => {
      alive = false
      window.clearTimeout(timer)
      ac.abort()
    }
  }, [orderCode, tries])

  async function payAgain() {
    setRelinking(true)
    try {
      // orderCode trên URL return là payosOrderCode — lấy mã đơn để vào màn QR custom
      const link = await paymentsApi.recreateLink(orderCode)
      const salesCode = link.order?.code
      if (salesCode) {
        navigate(resumePaymentPath(salesCode))
        return
      }
      // Fallback: vẫn ở lại flow nếu không có mã đơn
      navigate(`${ROUTES.ORDERS}`)
    } catch (err) {
      setError(err)
      setRelinking(false)
    }
  }

  const status = result?.status
  const order = result?.order

  let content
  if (!orderCode) {
    content = (
      <ResultCard tone="danger" icon={<InfoIcon size={34} />} title="Thiếu thông tin giao dịch">
        <p>Đường dẫn không có mã giao dịch. Hãy mở đơn hàng trong mục Đơn &amp; yêu cầu để kiểm tra.</p>
        <Actions primary={{ to: ROUTES.ORDERS, label: 'Xem đơn hàng' }} />
      </ResultCard>
    )
  } else if (error && !result) {
    content = (
      <ResultCard tone="danger" icon={<InfoIcon size={34} />} title="Chưa kiểm tra được thanh toán">
        <p>
          {(error.message || 'Có lỗi khi kết nối máy chủ').replace(/[.!?]?$/, '.')} Nếu bạn đã chuyển khoản, tiền sẽ
          không bị mất.
        </p>
        <Actions
          primary={{ onClick: () => setTries((t) => t + 1), label: 'Kiểm tra lại', icon: <RefreshIcon size={18} /> }}
          secondary={{ to: ROUTES.ORDERS, label: 'Xem đơn hàng' }}
        />
      </ResultCard>
    )
  } else if (status === 'PENDING' && inCheckWindow(order)) {
    // QR đã đóng, đang chờ xác nhận tiền về trong thời gian ân hạn
    content = (
      <ResultCard
        tone="accent"
        icon={<span className="pay-result__spinner" aria-hidden="true" />}
        title="Mã QR đã đóng, đang kiểm tra giao dịch"
      >
        <ul className="pay-result__cases">
          <li>
            <b>Nếu bạn ĐÃ chuyển khoản:</b> vui lòng chờ, hệ thống đang kiểm tra (khoảng 2 phút).{' '}
            <b>Đừng chuyển lại lần nữa.</b>
          </li>
          <li>
            <b>Nếu bạn CHƯA chuyển khoản:</b> không cần làm gì, đơn sẽ tự hủy. Bạn có thể đặt lại đơn mới.
          </li>
        </ul>
        <Actions primary={null} secondary={order ? { to: orderDetailPath(order.code), label: 'Xem đơn hàng' } : null} />
      </ResultCard>
    )
  } else if (!result || status === 'PENDING') {
    const gaveUp = result && tries >= MAX_AUTO_RETRY && !checking
    content = (
      <ResultCard tone="accent" icon={<span className="pay-result__spinner" aria-hidden="true" />} title="Đang xác nhận thanh toán">
        <p>
          Hòa Bình đang chờ ngân hàng xác nhận giao dịch
          {order ? (
            <>
              {' '}
              cho đơn <strong>{order.displayCode}</strong>
            </>
          ) : null}
          . Việc này thường mất vài giây.
        </p>
        {gaveUp && (
          <p className="pay-result__hint">
            Vẫn chưa nhận được xác nhận. Nếu bạn đã trả tiền, đơn sẽ tự cập nhật — bạn có thể kiểm tra lại hoặc xem đơn
            sau.
          </p>
        )}
        <Actions
          primary={
            gaveUp ? { onClick: () => setTries(1), label: 'Kiểm tra lại', icon: <RefreshIcon size={18} /> } : null
          }
          secondary={order ? { to: orderDetailPath(order.code), label: 'Xem đơn hàng' } : null}
        />
      </ResultCard>
    )
  } else if (status === 'PAID') {
    content = <PaymentSuccessCard order={order} />
  } else if (status === 'EXPIRED') {
    content = (
      <ResultCard tone="warning" icon={<ClockIcon size={34} />} title="Đơn đã hết hạn thanh toán">
        <p>
          Đơn <strong>{order?.displayCode}</strong> đã quá thời gian giữ hàng nên được hủy, hàng đã trả lại kho. Bạn có
          thể đặt lại đơn mới.
        </p>
        <p className="pay-result__hint">
          Nếu bạn đã chuyển khoản sau khi mã QR đóng, cửa hàng sẽ liên hệ để hoàn tiền cho bạn.
        </p>
        <Actions
          primary={{ to: ROUTES.PRODUCTS, label: 'Tiếp tục mua sắm' }}
          secondary={order ? { to: orderDetailPath(order.code), label: 'Xem đơn hàng' } : null}
        />
      </ResultCard>
    )
  } else if (status === 'REFUND') {
    // Tiền về muộn nhưng đơn đã hủy (hết hàng / đơn bị hủy trước đó) → cửa hàng hoàn tiền
    content = (
      <ResultCard tone="warning" icon={<InfoIcon size={34} />} title="Cửa hàng đã nhận tiền, sẽ hoàn lại cho bạn">
        <p>
          Hòa Bình đã nhận được tiền cho đơn <strong>{order?.displayCode}</strong> nhưng đơn không thể giao (đơn đã hủy
          hoặc đã hết hàng). Cửa hàng sẽ liên hệ để hoàn tiền cho bạn. Bạn không cần làm gì thêm.
        </p>
        {result.amount != null && (
          <p className="pay-result__amount">
            <span>Số tiền sẽ hoàn</span>
            <strong>{formatPrice(result.amount)}</strong>
          </p>
        )}
        <Actions
          primary={{ to: ROUTES.CONTACT, label: 'Liên hệ cửa hàng' }}
          secondary={order ? { to: orderDetailPath(order.code), label: 'Xem đơn hàng' } : null}
        />
      </ResultCard>
    )
  } else if (status === 'CANCELLED' && order?.state === 'CANCELLED') {
    // Đơn đã bị hủy (khách tự hủy hoặc cửa hàng hủy) — không thanh toán lại được
    content = (
      <ResultCard tone="danger" icon={<CloseIcon size={34} />} title="Đơn hàng đã bị hủy">
        <p>
          Đơn <strong>{order.displayCode}</strong> đã bị hủy nên mã QR không còn dùng được. Nếu bạn đã chuyển khoản, cửa
          hàng sẽ liên hệ để hoàn tiền cho bạn.
        </p>
        <Actions
          primary={{ to: ROUTES.PRODUCTS, label: 'Tiếp tục mua sắm' }}
          secondary={{ to: orderDetailPath(order.code), label: 'Xem đơn hàng' }}
        />
      </ResultCard>
    )
  } else {
    // CANCELLED (link PayOS bị hủy, đơn vẫn còn) | FAILED
    const cancelled = status === 'CANCELLED'
    content = (
      <ResultCard
        tone="danger"
        icon={<CloseIcon size={34} />}
        title={cancelled ? 'Bạn đã hủy thanh toán' : 'Thanh toán chưa thành công'}
      >
        <p>
          {cancelled
            ? 'Giao dịch trên PayOS đã bị hủy, bạn chưa bị trừ tiền.'
            : 'Ngân hàng chưa xử lý được giao dịch, bạn chưa bị trừ tiền.'}{' '}
          Đơn <strong>{order?.displayCode}</strong> vẫn được giữ trong thời gian thanh toán.
        </p>
        {order && (
          <p className="pay-result__amount">
            <span>Số tiền cần thanh toán</span>
            <strong>{formatPrice(result.amount ?? order.total)}</strong>
          </p>
        )}
        {error && <p className="modal-error">{error.message}</p>}
        <Actions
          primary={{
            onClick: payAgain,
            label: relinking ? 'Đang mở mã QR…' : 'Thanh toán lại',
            disabled: relinking,
          }}
          secondary={order ? { to: orderDetailPath(order.code), label: 'Xem đơn hàng' } : null}
        />
      </ResultCard>
    )
  }

  return (
    <div className="pay-result-page">
      <PageBanner
        title="Kết quả thanh toán"
        breadcrumbs={[
          { label: 'Trang chủ', to: ROUTES.HOME },
          { label: 'Đơn & Yêu cầu', to: ROUTES.ORDERS },
          { label: 'Kết quả thanh toán' },
        ]}
      />
      <div className="container pay-result-page__body">{content}</div>
    </div>
  )
}

function ResultCard({ tone, icon, title, children }) {
  return (
    <div className={`checkout-done pay-result pay-result--${tone}`} role="status">
      <span className="checkout-done__icon pay-result__icon" aria-hidden="true">
        {icon}
      </span>
      <h2>{title}</h2>
      {children}
    </div>
  )
}

/** primary / secondary: { to?, onClick?, label, icon?, disabled? } */
function Actions({ primary, secondary }) {
  const render = (a, cls) =>
    a.to ? (
      <Link to={a.to} className={cls}>
        {a.icon}
        {a.label}
      </Link>
    ) : (
      <button type="button" className={cls} onClick={a.onClick} disabled={a.disabled}>
        {a.icon}
        {a.label}
      </button>
    )
  return (
    <div className="checkout-done__actions">
      {primary && render(primary, 'btn btn--primary btn--pill')}
      {secondary && render(secondary, 'btn btn--ghost btn--pill')}
    </div>
  )
}

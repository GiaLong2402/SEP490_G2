import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import PageBanner from '@/components/storefront/PageBanner/PageBanner'
import LoadState from '@/components/ui/LoadState'
import { ordersApi } from '@/api/orders'
import { paymentsApi } from '@/api/payments'
import { ROUTES, orderDetailPath } from '@/constants/routes'
import PaymentStep from '@/pages/storefront/CheckoutPage/PaymentStep'
import '@/pages/storefront/CheckoutPage/CheckoutPage.css'

/**
 * Tiếp tục thanh toán từ Đơn & yêu cầu — hiện lại màn QR custom (không mở trang PayOS).
 * Route: /thanh-toan/don/:code
 * Hỗ trợ: cọc (UNPAID) hoặc tất toán phần còn lại (DEPOSITED + AWAITING_SETTLEMENT).
 */
export default function ResumePaymentPage() {
  const { code } = useParams()
  const [order, setOrder] = useState(null)
  const [payment, setPayment] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  useEffect(() => {
    if (!code) return undefined
    const ac = new AbortController()
    let alive = true
    setLoading(true)
    setError(null)

    ;(async () => {
      try {
        let loaded = await ordersApi.getMine(code, ac.signal)
        if (!alive) return

        const needsDeposit =
          loaded.isPreorder && loaded.paymentStatus === 'UNPAID' && loaded.depositAmount > 0
        const needsSettlement =
          loaded.isPreorder &&
          loaded.paymentStatus === 'DEPOSITED' &&
          (loaded.state === 'AWAITING_SETTLEMENT' || loaded.uiStatus === 'awaiting_settlement') &&
          Math.max(0, loaded.total - (loaded.paidAmount || 0)) > 0

        if (loaded.isPaid || (!needsDeposit && !needsSettlement && loaded.isDeposited)) {
          setOrder(loaded)
          setPayment(null)
          setLoading(false)
          return
        }

        let payosOrderCode = needsSettlement ? null : loaded.payment?.payosOrderCode
        let seedLink = null
        if (!payosOrderCode && needsDeposit) {
          seedLink = await ordersApi.createDepositPayosLink(code)
          if (!alive) return
          payosOrderCode = seedLink.payosOrderCode
          loaded = await ordersApi.getMine(code, ac.signal)
          if (!alive) return
        } else if (needsSettlement) {
          seedLink = await ordersApi.createBalancePayosLink(code)
          if (!alive) return
          payosOrderCode = seedLink.payosOrderCode
          loaded = await ordersApi.getMine(code, ac.signal)
          if (!alive) return
        }

        if (!payosOrderCode) {
          setOrder(loaded)
          setError(
            new Error(
              needsDeposit
                ? 'Không tạo được link thanh toán cọc.'
                : needsSettlement
                  ? 'Không tạo được link tất toán.'
                  : 'Đơn này chưa có mã thanh toán PayOS.',
            ),
          )
          setLoading(false)
          return
        }

        let link = seedLink
        try {
          link = await paymentsApi.recreateLink(payosOrderCode)
        } catch {
          if (!seedLink) throw new Error('Không lấy được link thanh toán PayOS.')
        }
        if (!alive) return

        const fallbackAmount = needsDeposit
          ? loaded.depositAmount
          : needsSettlement
            ? Math.max(0, loaded.total - (loaded.paidAmount || 0))
            : loaded.total

        setOrder(loaded)
        setPayment({
          payosOrderCode: link?.payosOrderCode ?? payosOrderCode,
          amount: Number(link?.amount ?? seedLink?.amount ?? fallbackAmount),
          checkoutUrl: link?.checkoutUrl ?? seedLink?.checkoutUrl ?? null,
          qrCode: link?.qrCode ?? seedLink?.qrCode ?? null,
          expiresAt: link?.expiresAt ?? seedLink?.expiresAt ?? loaded.depositDueAt ?? loaded.holdExpiresAt,
        })
        setLoading(false)
      } catch (err) {
        if (!alive || err.name === 'AbortError') return
        setError(err)
        setLoading(false)
      }
    })()

    return () => {
      alive = false
      ac.abort()
    }
  }, [code])

  const isSettlement =
    order?.paymentStatus === 'DEPOSITED' &&
    (order?.state === 'AWAITING_SETTLEMENT' || order?.uiStatus === 'awaiting_settlement')
  const isDepositPay = order?.isPreorder && order?.paymentStatus === 'UNPAID'

  const breadcrumbs = [
    { label: 'Trang chủ', to: ROUTES.HOME },
    { label: 'Đơn & Yêu cầu', to: ROUTES.ORDERS },
    { label: order?.displayCode || code, to: orderDetailPath(code) },
    { label: isSettlement ? 'Tất toán' : 'Thanh toán' },
  ]

  const alreadyDone = order && !payment && (order.isPaid || (order.isDeposited && !isSettlement))

  return (
    <div className="checkout-page">
      <PageBanner
        title={
          isSettlement
            ? 'Tất toán phần còn lại'
            : isDepositPay
              ? 'Thanh toán tiền cọc'
              : 'Thanh toán đơn hàng'
        }
        breadcrumbs={breadcrumbs}
      />
      <div className="container checkout-page__body">
        {alreadyDone ? (
          <div className="checkout-done">
            <h2>{order.isDeposited && !order.isPaid ? 'Đã nhận tiền cọc' : 'Đơn đã thanh toán'}</h2>
            <p>
              {order.isDeposited && !order.isPaid
                ? `Đơn ${order.displayCode} đã cọc — cửa hàng sẽ chuẩn bị / sản xuất.`
                : `Đơn ${order.displayCode} không còn cần thanh toán.`}
            </p>
            <div className="checkout-done__actions">
              <Link to={orderDetailPath(order.code)} className="btn btn--primary btn--pill">
                Xem đơn hàng
              </Link>
            </div>
          </div>
        ) : (
          <LoadState loading={loading} error={error} onRetry={() => window.location.reload()}>
            {order && payment ? <PaymentStep order={order} payment={payment} /> : null}
          </LoadState>
        )}
      </div>
    </div>
  )
}

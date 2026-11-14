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
        const loaded = await ordersApi.getMine(code, ac.signal)
        if (!alive) return
        if (loaded.isPaid) {
          setOrder(loaded)
          setPayment(null)
          setLoading(false)
          return
        }
        const payosOrderCode = loaded.payment?.payosOrderCode
        if (!payosOrderCode) {
          setOrder(loaded)
          setError(new Error('Đơn này chưa có mã thanh toán PayOS.'))
          setLoading(false)
          return
        }
        const link = await paymentsApi.recreateLink(payosOrderCode)
        if (!alive) return
        setOrder(link.order || loaded)
        setPayment({
          payosOrderCode: link.payosOrderCode ?? payosOrderCode,
          amount: link.amount ?? loaded.total,
          checkoutUrl: link.checkoutUrl ?? null,
          qrCode: link.qrCode ?? null,
          expiresAt: link.expiresAt ?? loaded.holdExpiresAt,
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

  const breadcrumbs = [
    { label: 'Trang chủ', to: ROUTES.HOME },
    { label: 'Đơn & Yêu cầu', to: ROUTES.ORDERS },
    { label: order?.displayCode || code, to: orderDetailPath(code) },
    { label: 'Thanh toán' },
  ]

  return (
    <div className="checkout-page">
      <PageBanner title="Thanh toán đơn hàng" breadcrumbs={breadcrumbs} />
      <div className="container checkout-page__body">
        {order?.isPaid ? (
          <div className="checkout-done">
            <h2>Đơn đã thanh toán</h2>
            <p>Đơn {order.displayCode} không còn cần thanh toán.</p>
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

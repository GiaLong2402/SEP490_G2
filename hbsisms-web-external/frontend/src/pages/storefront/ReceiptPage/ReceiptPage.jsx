import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import PageBanner from '@/components/storefront/PageBanner/PageBanner'
import { ArrowLeftIcon, CheckIcon, MailIcon } from '@/components/icons/Icons'
import { ROUTES, orderDetailPath } from '@/constants/routes'
import LoadState from '@/components/ui/LoadState'
import { ordersApi } from '@/api/orders'
import { useAsync } from '@/hooks/useAsync'
import { formatDateTime } from '@/utils/date'
import { formatPrice } from '@/utils/formatPrice'
import { moneyToWords } from '@/utils/numberToWords'
import './ReceiptPage.css'

// TODO: lấy thông tin cửa hàng thật (địa chỉ, số điện thoại) từ cấu hình hệ thống
const STORE_INFO = {
  name: 'HẢI SẢN HÒA BÌNH',
  address: 'Hải Phòng',
  phone: 'Đang cập nhật',
}

function receiptDateText(value) {
  const d = value ? new Date(value) : new Date()
  return `Ngày ${d.getDate()} tháng ${d.getMonth() + 1} năm ${d.getFullYear()}`
}

/**
 * Biên lai bán hàng — form chung, in / lưu PDF bằng hộp thoại in của trình duyệt.
 */
export default function ReceiptPage() {
  const { code } = useParams()
  const { data: order, loading, error, reload } = useAsync((signal) => ordersApi.getMine(code, signal), [code])
  const [emailNote, setEmailNote] = useState('')

  const breadcrumbs = [
    { label: 'Trang chủ', to: ROUTES.HOME },
    { label: 'Đơn & Yêu cầu', to: ROUTES.ORDERS },
    order ? { label: order.displayCode, to: orderDetailPath(order.code) } : null,
    { label: 'Biên lai' },
  ].filter(Boolean)

  if (loading || (error && error.status !== 404)) {
    return (
      <div className="receipt-page">
        <PageBanner title="Biên lai" breadcrumbs={breadcrumbs} />
        <div className="container receipt-page__body">
          <LoadState loading={loading} error={error} onRetry={reload} />
        </div>
      </div>
    )
  }

  if (!order || !order.isPaid) {
    return (
      <div className="receipt-page">
        <PageBanner title="Biên lai" breadcrumbs={breadcrumbs} />
        <div className="container receipt-page__body">
          <div className="receipt-missing">
            <h2>Chưa có biên lai</h2>
            <p>Biên lai chỉ có sau khi đơn hàng được thanh toán.</p>
            <Link to={ROUTES.ORDERS} className="btn btn--primary btn--pill">
              Về danh sách đơn
            </Link>
          </div>
        </div>
      </div>
    )
  }

  const total = order.total

  return (
    <div className="receipt-page">
      <PageBanner title="Biên lai" breadcrumbs={breadcrumbs} />

      <div className="container receipt-page__body">
        <div className="receipt-toolbar">
          <Link to={orderDetailPath(order.code)} className="receipt-back">
            <ArrowLeftIcon size={18} /> Quay lại đơn hàng
          </Link>
          <div className="receipt-toolbar__actions">
            <button
              type="button"
              className="receipt-btn"
              onClick={() => setEmailNote('Gửi biên lai qua email sẽ có khi hệ thống email được nối.')}
            >
              <MailIcon size={18} /> Gửi qua email
            </button>
            <button type="button" className="receipt-btn" onClick={() => window.print()}>
              In biên lai
            </button>
            {/* Hộp thoại in của trình duyệt có tùy chọn "Lưu dưới dạng PDF" */}
            <button type="button" className="receipt-btn receipt-btn--primary" onClick={() => window.print()}>
              Tải PDF
            </button>
          </div>
        </div>
        {emailNote && (
          <p className="receipt-toast" role="status">
            {emailNote}
          </p>
        )}

        {/* ===== Tờ biên lai ===== */}
        <article className="receipt" aria-label="Biên lai bán hàng">
          <header className="receipt__top">
            <div className="receipt__store">
              <strong>{STORE_INFO.name}</strong>
              <span>
                <b>Địa chỉ:</b> {STORE_INFO.address}
              </span>
              <span>
                <b>ĐT:</b> {STORE_INFO.phone}
              </span>
            </div>
            <div className="receipt__title">
              <h2>BIÊN LAI BÁN HÀNG</h2>
              <span>
                Số: <b>{order.displayCode}</b>
              </span>
              <span>Mặt hàng: Chả hải sản đóng khay · Bán online</span>
            </div>
          </header>

          <dl className="receipt__fields">
            <div>
              <dt>Tên khách hàng:</dt>
              <dd>
                {order.receiver.name} · {order.receiver.phone}
              </dd>
            </div>
            <div>
              <dt>Địa chỉ:</dt>
              <dd>{order.isDelivery ? order.receiver.address : 'Nhận tại cửa hàng'}</dd>
            </div>
            <div>
              <dt>Thanh toán:</dt>
              <dd>
                PayOS · {formatDateTime(order.payment?.paidAt)} · Đã xác minh
              </dd>
            </div>
          </dl>

          <table className="receipt__table">
            <thead>
              <tr>
                <th>STT</th>
                <th>TÊN HÀNG</th>
                <th>SỐ LƯỢNG</th>
                <th>ĐƠN GIÁ</th>
                <th>THÀNH TIỀN</th>
              </tr>
            </thead>
            <tbody>
              {order.lines.map((l, i) => (
                <tr key={l.id ?? l.name + l.packLabel}>
                  <td className="is-center">{i + 1}</td>
                  <td className="is-bold">
                    {l.name}
                    {l.packLabel ? ` · ${l.packLabel}` : ''}
                  </td>
                  <td className="is-center">{l.qty} khay</td>
                  <td className="is-right">{formatPrice(l.unitPrice)}</td>
                  <td className="is-right is-bold">{formatPrice(l.lineTotal)}</td>
                </tr>
              ))}
              {order.shippingFee > 0 && (
                <tr>
                  <td className="is-center">{order.lines.length + 1}</td>
                  <td className="is-bold">Phí giao hàng</td>
                  <td className="is-center">1</td>
                  <td className="is-right">{formatPrice(order.shippingFee)}</td>
                  <td className="is-right is-bold">{formatPrice(order.shippingFee)}</td>
                </tr>
              )}
            </tbody>
            <tfoot>
              <tr>
                <td colSpan={4} className="is-center is-bold">
                  CỘNG
                </td>
                <td className="is-right is-bold">{formatPrice(total)}</td>
              </tr>
            </tfoot>
          </table>

          <div className="receipt__amount">
            <p>
              Thành tiền: <strong>{formatPrice(total)}</strong>
            </p>
            <em>Bằng chữ: {moneyToWords(total)}.</em>
          </div>

          <p className="receipt__date">{receiptDateText(order.payment?.paidAt)}</p>
          <div className="receipt__sign">
            <div>
              <strong>KHÁCH HÀNG</strong>
              <span>(Ký, ghi rõ họ tên)</span>
            </div>
            <div>
              <strong>NGƯỜI BÁN HÀNG</strong>
              <span>(Ký, ghi rõ họ tên)</span>
              <span className="receipt__verified">
                <CheckIcon size={14} /> Đã xác nhận trên hệ thống
              </span>
            </div>
          </div>

          <footer className="receipt__foot">
            Đây là biên lai bán lẻ, không phải hóa đơn điện tử thuế. Bán theo khay cố định, không cân lại khi nhận
            hàng. Cảm ơn bạn đã mua hàng!
          </footer>
        </article>
      </div>
    </div>
  )
}

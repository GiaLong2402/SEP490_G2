import { Link } from 'react-router-dom'
import { CheckIcon } from '@/components/icons/Icons'
import payosLogo from '@/assets/payment/payos.svg'
import { ROUTES, orderDetailPath } from '@/constants/routes'
import { formatPrice } from '@/utils/formatPrice'
import { formatSchedule } from '@/utils/date'

/**
 * Thẻ "Thanh toán thành công" — dùng ở trang kết quả PayOS.
 * order: đơn đã qua mapper (api/mappers/order.js). Style: .checkout-done / .paid-info (styles/shared/result-card.css)
 */
export default function PaymentSuccessCard({ order }) {
  return (
    <div className="checkout-done checkout-done--paid">
      <span className="checkout-done__icon" aria-hidden="true">
        <CheckIcon size={36} />
      </span>
      <h2>Thanh toán thành công</h2>
      <p>
        Cảm ơn bạn! Đơn <strong>{order.displayCode}</strong> đã được ghi nhận, Hòa Bình sẽ chuẩn bị hàng theo thời
        gian bạn chọn.
      </p>

      <dl className="paid-info">
        <div>
          <dt>Mã đơn</dt>
          <dd>{order.displayCode}</dd>
        </div>
        <div>
          <dt>Số tiền</dt>
          <dd className="paid-info__amount">{formatPrice(order.total)}</dd>
        </div>
        <div>
          <dt>Phương thức</dt>
          <dd>
            <img src={payosLogo} alt="PayOS" className="paid-info__logo" />
          </dd>
        </div>
        <div>
          <dt>{order.isDelivery ? 'Giao hàng' : 'Nhận tại quầy'}</dt>
          <dd>{formatSchedule(order.schedule) || '—'}</dd>
        </div>
      </dl>

      <Link to={orderDetailPath(order.code)} className="btn btn--primary btn--pill btn--full">
        Theo dõi đơn hàng
      </Link>
      <Link to={ROUTES.HOME} className="paid-home">
        Về trang chủ
      </Link>
    </div>
  )
}

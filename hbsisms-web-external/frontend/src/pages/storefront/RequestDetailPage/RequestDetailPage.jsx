import { Link, useParams } from 'react-router-dom'
import AccountShell from '@/components/storefront/AccountShell/AccountShell'
import LoadState from '@/components/ui/LoadState'
import {
  ArrowLeftIcon,
  CalendarIcon,
  ClipboardIcon,
  CloseIcon,
  HeadsetIcon,
  InfoIcon,
  PackageIcon,
} from '@/components/icons/Icons'
import { salesRequestsApi } from '@/api/salesRequests'
import { REQUEST_STATUS } from '@/constants/orderStatus'
import { ROUTES, orderDetailPath } from '@/constants/routes'
import { useAsync } from '@/hooks/useAsync'
import { formatDate, formatDateTime } from '@/utils/date'
import './RequestDetailPage.css'

/** Lời nhắn nổi bật theo RequestStatus */
const HEADLINE = {
  NEW: { title: 'Cửa hàng đang xem xét yêu cầu', sub: 'Hòa Bình sẽ kiểm tra khả năng chế biến và báo lại cho bạn.' },
  QUOTING: { title: 'Cửa hàng đang báo giá', sub: 'Bạn sẽ nhận được giá và ngày giao dự kiến sớm.' },
  ORDERED: { title: 'Yêu cầu đã được chốt thành đơn hàng', sub: 'Thanh toán đơn để cửa hàng bắt đầu chuẩn bị hàng.' },
  REJECTED: { title: 'Cửa hàng chưa đáp ứng được yêu cầu', sub: 'Xem lý do bên dưới, bạn có thể gửi lại với số lượng hoặc ngày khác.' },
  CLOSED: { title: 'Yêu cầu đã đóng', sub: 'Yêu cầu này không còn được xử lý.' },
}

/** 3 bước của yêu cầu: gửi → cửa hàng xem xét → chốt */
const STEPS = ['Đã gửi', 'Cửa hàng xem xét', 'Chốt đơn']
const STEP_INDEX = { NEW: 1, QUOTING: 1, ORDERED: 2, REJECTED: 1, CLOSED: 1 }

const qty = (n) => (n == null ? '—' : `${n} khay`)

export default function RequestDetailPage() {
  const { code } = useParams()
  const { data: req, loading, error, reload } = useAsync((signal) => salesRequestsApi.getMine(code, signal), [code])

  const breadcrumbs = [
    { label: 'Trang chủ', to: ROUTES.HOME },
    { label: 'Đơn & Yêu cầu', to: ROUTES.ORDERS },
    { label: req ? req.code : 'Chi tiết yêu cầu' },
  ]

  return (
    <AccountShell title="Chi tiết yêu cầu" breadcrumbs={breadcrumbs}>
      <LoadState loading={loading} error={error} onRetry={reload}>
        {req && <RequestDetail req={req} />}
      </LoadState>
    </AccountShell>
  )
}

function RequestDetail({ req }) {
  const status = REQUEST_STATUS[req.status] || REQUEST_STATUS.NEW
  const headline = HEADLINE[req.status] || HEADLINE.NEW
  const stepIndex = STEP_INDEX[req.status] ?? 1
  const stopped = req.status === 'REJECTED' || req.status === 'CLOSED'
  const responded = req.feasible != null

  return (
    <div className="od">
      {/* Đầu trang */}
      <section className="od-card od-head">
        <div>
          <h2>Yêu cầu {req.code}</h2>
          <p>Gửi lúc {formatDateTime(req.receivedAt)}</p>
        </div>
        <div className="od-head__chips">
          <span className={`order-status order-status--${status.tone}`}>
            <status.Icon size={16} />
            {status.label}
          </span>
        </div>
      </section>

      {/* Tiến trình */}
      <section className="od-card">
        <h3 className="od-title">
          <ClipboardIcon size={22} /> Tiến trình yêu cầu
        </h3>
        <div className={`od-banner od-banner--${status.tone}`}>
          <span className="od-banner__icon" aria-hidden="true">
            <status.Icon size={24} />
          </span>
          <div>
            <strong>{headline.title}</strong>
            <span>{headline.sub}</span>
          </div>
        </div>
        <ol className="od-journey rq-journey">
          {STEPS.map((label, i) => {
            const isStopHere = stopped && i === stepIndex
            const state = i < stepIndex ? 'is-done' : i === stepIndex ? 'is-current' : ''
            const Icon = i === 0 ? ClipboardIcon : i === 1 ? (isStopHere ? CloseIcon : InfoIcon) : PackageIcon
            return (
              <li key={label} className={`od-journey__step ${state} ${isStopHere ? 'is-stopped' : ''}`}>
                <span className="od-journey__dot" aria-hidden="true">
                  <Icon size={22} />
                </span>
                <strong>{isStopHere ? status.label : label}</strong>
                <span>{i === 0 ? formatDate(req.receivedAt) : i > stepIndex ? 'Chưa tới' : ''}</span>
              </li>
            )
          })}
        </ol>
      </section>

      <div className="od-grid">
        {/* Nội dung khách gửi */}
        <section className="od-card">
          <h3 className="od-title">
            <PackageIcon size={22} /> Nội dung yêu cầu
          </h3>
          <p className="rq-summary">{req.summary || '—'}</p>
          <dl className="rq-facts">
            <div>
              <dt>Số lượng cần</dt>
              <dd>{qty(req.requestedQty)}</dd>
            </div>
            <div>
              <dt>Lúc gửi còn sẵn</dt>
              <dd>{qty(req.availableQty)}</dd>
            </div>
            <div>
              <dt>
                <CalendarIcon size={16} /> Ngày cần nhận
              </dt>
              <dd>{req.neededDate ? formatDate(req.neededDate) : '—'}</dd>
            </div>
          </dl>
          {req.note && <p className="rq-note">Ghi chú: {req.note}</p>}
        </section>

        {/* Phản hồi cửa hàng */}
        <section className="od-card">
          <h3 className="od-title">
            <HeadsetIcon size={22} /> Phản hồi của cửa hàng
          </h3>
          {responded ? (
            <dl className="rq-facts">
              <div>
                <dt>Khả năng đáp ứng</dt>
                <dd className={req.feasible ? 'is-ok' : 'is-bad'}>{req.feasible ? 'Đáp ứng được' : 'Chưa đáp ứng đủ'}</dd>
              </div>
              <div>
                <dt>Đáp ứng được</dt>
                <dd>{qty(req.fulfillableQty)}</dd>
              </div>
              <div>
                <dt>Còn thiếu</dt>
                <dd>{qty(req.shortageQty)}</dd>
              </div>
              <div>
                <dt>Ngày giao dự kiến</dt>
                <dd>{req.expectedDate ? formatDate(req.expectedDate) : '—'}</dd>
              </div>
            </dl>
          ) : (
            <p className="rq-waiting">Cửa hàng chưa phản hồi. Bạn sẽ nhận thông báo khi có kết quả.</p>
          )}

          {req.rejectReason && (
            <p className="order-card__reason rq-reason">
              <InfoIcon size={20} />
              <span>
                <strong>Lý do:</strong> {req.rejectReason}
              </span>
            </p>
          )}
        </section>
      </div>

      {/* Hành động */}
      <section className="od-card od-help">
        <span className="od-banner__icon" aria-hidden="true">
          <HeadsetIcon size={24} />
        </span>
        <div className="od-help__text">
          <strong>{req.status === 'ORDERED' ? 'Đơn từ yêu cầu này đã sẵn sàng' : 'Cần trao đổi thêm?'}</strong>
          <span>
            {req.status === 'ORDERED'
              ? 'Xem đơn để kiểm tra giá chốt và thanh toán.'
              : 'Liên hệ cửa hàng để thay đổi số lượng hoặc ngày nhận.'}
          </span>
        </div>
        <div className="od-help__actions">
          {req.status === 'REJECTED' && (
            <Link to={ROUTES.PRODUCTS} className="od-receipt-btn">
              Gửi lại yêu cầu
            </Link>
          )}
          {req.status === 'ORDERED' && req.orderCode ? (
            <Link to={orderDetailPath(req.orderCode)} className="btn btn--primary btn--pill">
              Xem đơn &amp; thanh toán
            </Link>
          ) : (
            <Link to={ROUTES.CONTACT} className="btn btn--primary btn--pill">
              <HeadsetIcon size={18} /> Liên hệ cửa hàng
            </Link>
          )}
        </div>
      </section>

      <Link to={ROUTES.ORDERS} className="od-back">
        <ArrowLeftIcon size={20} /> Quay lại danh sách
      </Link>
    </div>
  )
}

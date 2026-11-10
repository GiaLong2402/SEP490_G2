import { useState } from 'react'
import { Link } from 'react-router-dom'
import AccountShell from '@/components/storefront/AccountShell/AccountShell'
import Pagination from '@/components/storefront/Pagination/Pagination'
import LoadState from '@/components/ui/LoadState'
import {
  CalendarIcon,
  ChevronDownIcon,
  ChevronUpIcon,
  ClipboardIcon,
  ExternalLinkIcon,
  InfoIcon,
  PackageIcon,
  SearchIcon,
} from '@/components/icons/Icons'
import { ordersApi } from '@/api/orders'
import { salesRequestsApi } from '@/api/salesRequests'
import { ORDER_FILTERS, ORDER_STATUS, REQUEST_FILTERS, REQUEST_STATUS, needsCustomerAction } from '@/constants/orderStatus'
import { ROUTES, orderDetailPath, requestDetailPath } from '@/constants/routes'
import { useAsync } from '@/hooks/useAsync'
import { formatDate, formatDateTime, formatSchedule } from '@/utils/date'
import { formatPrice } from '@/utils/formatPrice'
import './OrdersPage.css'

const PAGE_SIZE = 4

const TIME_OPTIONS = [
  { value: 'all', label: 'Mọi thời gian' },
  { value: '30', label: '30 ngày qua' },
  { value: '90', label: '3 tháng qua' },
]

function withinDays(iso, days, now) {
  if (days === 'all' || !iso) return true
  return (now - new Date(iso)) / 86400000 <= Number(days)
}

/** Dòng nhắn ở chân thẻ đơn */
function orderMessage(order) {
  switch (order.uiStatus) {
    case 'awaiting_payment':
      return order.holdExpiresAt
        ? `Thanh toán trước ${formatDateTime(order.holdExpiresAt).split(' · ')[0]} để giữ hàng`
        : 'Đơn đang chờ bạn thanh toán'
    case 'preparing':
      return 'Cửa hàng đang sơ chế và đóng khay'
    case 'shipping':
      return order.isDelivery ? 'Đơn hàng đang trên đường giao đến bạn' : 'Hàng đã sẵn sàng, mời bạn đến lấy'
    case 'completed':
      return order.isDelivery ? 'Đã giao hàng thành công' : 'Bạn đã nhận hàng'
    default:
      return 'Đơn hàng đã hủy'
  }
}

const REQUEST_MESSAGE = {
  NEW: 'Cửa hàng đang xem xét yêu cầu',
  QUOTING: 'Cửa hàng đang báo giá cho bạn',
  ORDERED: 'Đã chốt đơn, thanh toán để cửa hàng chuẩn bị hàng',
  REJECTED: 'Cửa hàng chưa đáp ứng được yêu cầu này',
  CLOSED: 'Yêu cầu đã đóng',
}

function StatusChip({ meta }) {
  return (
    <span className={`order-status order-status--${meta.tone}`}>
      <meta.Icon size={16} />
      {meta.label}
    </span>
  )
}

/** Đơn & Yêu cầu của tôi — GET /me/orders + GET /me/sales-requests */
export default function OrdersPage() {
  const ordersQuery = useAsync((signal) => ordersApi.listMine(signal), [])
  const requestsQuery = useAsync((signal) => salesRequestsApi.listMine(signal), [])
  const [now] = useState(() => Date.now())

  const [tab, setTab] = useState('orders')
  const [filter, setFilter] = useState('all')
  const [query, setQuery] = useState('')
  const [time, setTime] = useState('all')
  const [page, setPage] = useState(1)
  const [openReason, setOpenReason] = useState(null)

  const isOrders = tab === 'orders'
  const tabs = [
    {
      id: 'orders',
      label: 'Đơn hàng',
      Icon: PackageIcon,
      list: (ordersQuery.data || []).map((o) => ({ ...o, status: o.uiStatus, date: o.placedAt })),
      filters: ORDER_FILTERS,
      meta: ORDER_STATUS,
      q: ordersQuery,
    },
    {
      id: 'requests',
      label: 'Yêu cầu',
      Icon: ClipboardIcon,
      list: (requestsQuery.data || []).map((r) => ({ ...r, date: r.receivedAt })),
      filters: REQUEST_FILTERS,
      meta: REQUEST_STATUS,
      q: requestsQuery,
    },
  ]
  const current = tabs.find((t) => t.id === tab)

  const keyword = query.trim().toLowerCase()
  const matched = current.list.filter(
    (item) =>
      withinDays(item.date, time, now) &&
      (keyword === '' ||
        item.code.toLowerCase().includes(keyword) ||
        (isOrders
          ? item.lines.some((l) => l.name.toLowerCase().includes(keyword))
          : item.summary.toLowerCase().includes(keyword))),
  )
  const visible = filter === 'all' ? matched : matched.filter((i) => i.status === filter)
  const pageCount = Math.max(1, Math.ceil(visible.length / PAGE_SIZE))
  const safePage = Math.min(page, pageCount)
  const pageItems = visible.slice((safePage - 1) * PAGE_SIZE, safePage * PAGE_SIZE)

  function changeTab(id) {
    setTab(id)
    setFilter('all')
    setPage(1)
    setOpenReason(null)
  }

  return (
    <AccountShell
      title="Đơn & Yêu cầu của tôi"
      breadcrumbs={[
        { label: 'Trang chủ', to: ROUTES.HOME },
        { label: 'Tài khoản', to: ROUTES.ACCOUNT },
        { label: 'Đơn & Yêu cầu' },
      ]}
    >
      <div className="orders-page">
        <div className="account-tabs" role="tablist" aria-label="Đơn hàng và yêu cầu">
          {tabs.map(({ id, label, Icon, list }) => (
            <button
              key={id}
              type="button"
              role="tab"
              aria-selected={tab === id}
              className={`account-tabs__item ${tab === id ? 'is-active' : ''}`}
              onClick={() => changeTab(id)}
            >
              <Icon size={22} className="account-tabs__icon" />
              <span>{label}</span>
              <span className="orders-tab__count">
                {list.length}
                {list.some((i) => needsCustomerAction(i.status)) && (
                  <span className="orders-tab__dot" aria-label="Có mục cần xử lý" />
                )}
              </span>
            </button>
          ))}
        </div>

        <div className="orders-filter" role="group" aria-label="Lọc theo trạng thái">
          {['all', ...current.filters].map((key) => {
            const count = key === 'all' ? matched.length : matched.filter((i) => i.status === key).length
            return (
              <button
                key={key}
                type="button"
                aria-pressed={filter === key}
                className={`orders-filter__chip ${filter === key ? 'is-active' : ''}`}
                onClick={() => {
                  setFilter(key)
                  setPage(1)
                }}
              >
                {key === 'all' ? 'Tất cả' : current.meta[key].label}
                <span>{count}</span>
              </button>
            )
          })}
        </div>

        <div className="orders-toolbar">
          <label className="orders-search">
            <SearchIcon size={18} />
            <input
              type="search"
              value={query}
              onChange={(e) => {
                setQuery(e.target.value)
                setPage(1)
              }}
              placeholder={isOrders ? 'Tìm theo mã đơn hoặc tên sản phẩm' : 'Tìm theo mã yêu cầu hoặc nội dung'}
            />
          </label>
          <label className="orders-time">
            <CalendarIcon size={18} />
            <select
              value={time}
              onChange={(e) => {
                setTime(e.target.value)
                setPage(1)
              }}
              aria-label="Thời gian"
            >
              {TIME_OPTIONS.map((o) => (
                <option key={o.value} value={o.value}>
                  {o.label}
                </option>
              ))}
            </select>
            <ChevronDownIcon size={18} className="orders-time__chevron" />
          </label>
        </div>

        <LoadState
          loading={current.q.loading}
          error={current.q.error}
          onRetry={current.q.reload}
          empty={pageItems.length === 0}
          emptyText={
            current.list.length === 0
              ? isOrders
                ? 'Bạn chưa có đơn hàng nào.'
                : 'Bạn chưa có yêu cầu nào.'
              : `Không tìm thấy ${isOrders ? 'đơn hàng' : 'yêu cầu'} nào phù hợp.`
          }
          emptyAction={
            current.list.length === 0 && (
              <Link to={ROUTES.PRODUCTS} className="btn btn--primary btn--pill">
                Xem sản phẩm
              </Link>
            )
          }
        >
          <ul className="order-list">
            {pageItems.map((item) =>
              isOrders ? (
                <OrderCard key={item.code} order={item} />
              ) : (
                <RequestCard
                  key={item.code}
                  req={item}
                  open={openReason === item.code}
                  onToggle={() => setOpenReason(openReason === item.code ? null : item.code)}
                />
              ),
            )}
          </ul>
        </LoadState>

        {pageCount > 1 && <Pagination page={safePage} totalPages={pageCount} onChange={setPage} />}
      </div>
    </AccountShell>
  )
}

function OrderCard({ order }) {
  const meta = ORDER_STATUS[order.uiStatus]
  const awaiting = order.uiStatus === 'awaiting_payment'
  return (
    <li className={`order-card ${awaiting ? 'is-action' : ''}`}>
      <div className="order-card__head">
        <strong>Mã đơn: {order.displayCode}</strong>
        <span className="order-card__date">Ngày đặt: {formatDate(order.placedAt)}</span>
        <StatusChip meta={meta} />
      </div>

      <ul className="order-card__lines">
        {order.lines.map((line) => (
          <li key={line.id ?? line.name + line.packLabel} className="order-line">
            <img className="order-line__thumb" src={line.imageUrl} alt="" />
            <div className="order-line__name">
              <strong>{line.name}</strong>
              <span>{line.packLabel}</span>
            </div>
            <span className="order-line__qty">Số lượng: {line.qty}</span>
            <span className="order-line__price">{formatPrice(line.lineTotal)}</span>
          </li>
        ))}
      </ul>

      <div className="order-card__foot">
        <p className="order-card__total">
          Tổng thanh toán: <strong>{formatPrice(order.total)}</strong>
        </p>
        <p className={`order-card__msg order-card__msg--${meta.tone}`}>
          <meta.Icon size={18} />
          {orderMessage(order)}
        </p>
        <div className="order-card__actions">
          <Link to={orderDetailPath(order.code)} className="order-btn">
            Xem chi tiết
          </Link>
          {awaiting && order.payment?.checkoutUrl && (
            <a href={order.payment.checkoutUrl} className="order-btn order-btn--primary">
              Thanh toán <ExternalLinkIcon size={16} />
            </a>
          )}
          {order.uiStatus === 'shipping' && (
            <Link to={orderDetailPath(order.code)} className="order-btn order-btn--primary">
              Theo dõi đơn
            </Link>
          )}
        </div>
      </div>
    </li>
  )
}

function RequestCard({ req, open, onToggle }) {
  const meta = REQUEST_STATUS[req.status] || REQUEST_STATUS.NEW
  const ordered = req.status === 'ORDERED'
  return (
    <li className={`order-card ${ordered ? 'is-action' : ''}`}>
      <div className="order-card__head">
        <strong>Mã yêu cầu: {req.code}</strong>
        <span className="order-card__date">
          Ngày gửi: {formatDate(req.receivedAt)}
          {req.neededDate && <> · Cần nhận: {formatDate(req.neededDate)}</>}
        </span>
        <StatusChip meta={meta} />
      </div>

      <div className="request-body">
        <p className="request-body__summary">{req.summary}</p>
        <div className="request-body__facts">
          {req.requestedQty != null && <span>Cần {req.requestedQty} khay</span>}
          {req.fulfillableQty != null && <span className="is-ok">Đáp ứng {req.fulfillableQty} khay</span>}
          {req.expectedDate && <span>Giao dự kiến {formatSchedule({ date: req.expectedDate })}</span>}
        </div>
      </div>

      <div className="order-card__foot">
        <p className={`order-card__msg order-card__msg--${meta.tone}`}>
          <meta.Icon size={18} />
          {REQUEST_MESSAGE[req.status]}
        </p>
        <div className="order-card__actions">
          {req.rejectReason && (
            <button type="button" className="order-btn" aria-expanded={open} onClick={onToggle}>
              Xem lý do
              {open ? <ChevronUpIcon size={18} /> : <ChevronDownIcon size={18} />}
            </button>
          )}
          <Link to={requestDetailPath(req.code)} className="order-btn">
            Xem chi tiết
          </Link>
          {ordered && req.orderCode && (
            <Link to={orderDetailPath(req.orderCode)} className="order-btn order-btn--primary">
              Thanh toán
            </Link>
          )}
        </div>
      </div>

      {open && (
        <p className="order-card__reason">
          <InfoIcon size={20} />
          <span>
            <strong>Lý do:</strong> {req.rejectReason}
          </span>
        </p>
      )}
    </li>
  )
}

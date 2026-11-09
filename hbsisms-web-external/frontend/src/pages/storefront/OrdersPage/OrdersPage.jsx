import { useState } from 'react'
import AccountShell from '@/components/storefront/AccountShell/AccountShell'
import Pagination from '@/components/storefront/Pagination/Pagination'
import {
  CalendarIcon,
  CheckIcon,
  ChevronDownIcon,
  ChevronUpIcon,
  ClipboardIcon,
  ClockIcon,
  CloseIcon,
  InfoIcon,
  PackageIcon,
  SearchIcon,
  TruckIcon,
} from '@/components/icons/Icons'
import { ROUTES } from '@/constants/routes'
import { DEMO_ORDERS, DEMO_REQUESTS } from '@/data/demoOrders'
import { formatPrice } from '@/utils/formatPrice'
import '../AccountPage/AccountPage.css'
import './OrdersPage.css'

const PAGE_SIZE = 4
const TODAY = new Date()

const TABS = [
  {
    id: 'orders',
    label: 'Đơn hàng',
    Icon: PackageIcon,
    list: DEMO_ORDERS,
    filters: ['awaiting_payment', 'preparing', 'shipping', 'completed', 'cancelled'],
  },
  {
    id: 'requests',
    label: 'Yêu cầu',
    Icon: ClipboardIcon,
    list: DEMO_REQUESTS,
    filters: ['reviewing', 'confirmed', 'rejected'],
  },
]

/** Nhãn + màu + icon của từng trạng thái */
const STATUS = {
  awaiting_payment: { label: 'Chờ thanh toán', tone: 'warning', Icon: ClockIcon },
  preparing: { label: 'Đang chuẩn bị', tone: 'info', Icon: PackageIcon },
  shipping: { label: 'Đang giao', tone: 'accent', Icon: TruckIcon },
  completed: { label: 'Hoàn thành', tone: 'success', Icon: CheckIcon },
  cancelled: { label: 'Đã hủy', tone: 'muted', Icon: CloseIcon },
  reviewing: { label: 'Đang xem xét', tone: 'warning', Icon: ClockIcon },
  confirmed: { label: 'Đã chốt', tone: 'success', Icon: CheckIcon },
  rejected: { label: 'Không đáp ứng được', tone: 'danger', Icon: CloseIcon },
}

/** Nút hành động chính theo trạng thái */
const PRIMARY_ACTION = {
  awaiting_payment: 'Thanh toán',
  shipping: 'Theo dõi đơn',
  completed: 'Mua lại',
  cancelled: 'Mua lại',
  confirmed: 'Thanh toán',
}

const TIME_OPTIONS = [
  { value: 'all', label: 'Mọi thời gian' },
  { value: '30', label: '30 ngày qua' },
  { value: '90', label: '3 tháng qua' },
]

/** Trạng thái khách cần thanh toán → làm nổi */
const NEEDS_ACTION = ['awaiting_payment', 'confirmed']
const needsAction = (item) => NEEDS_ACTION.includes(item.status)

function formatDate(iso) {
  const [y, m, d] = iso.split('-')
  return `${d}/${m}/${y}`
}

function withinDays(iso, days) {
  if (days === 'all') return true
  return (TODAY - new Date(iso)) / 86400000 <= Number(days)
}

function lineTotal(line) {
  return line.price == null ? null : line.price * line.qty
}

function StatusChip({ status }) {
  const { label, tone, Icon } = STATUS[status]
  return (
    <span className={`order-status order-status--${tone}`}>
      <Icon size={16} />
      {label}
    </span>
  )
}

export default function OrdersPage() {
  const [tab, setTab] = useState('orders')
  const [filter, setFilter] = useState('all')
  const [query, setQuery] = useState('')
  const [time, setTime] = useState('all')
  const [page, setPage] = useState(1)
  const [openReason, setOpenReason] = useState(null)

  const current = TABS.find((t) => t.id === tab)
  const isOrders = tab === 'orders'

  const keyword = query.trim().toLowerCase()
  const matched = current.list.filter(
    (item) =>
      withinDays(item.date, time) &&
      (keyword === '' ||
        item.code.toLowerCase().includes(keyword) ||
        item.lines.some((l) => l.name.toLowerCase().includes(keyword))),
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
          {TABS.map(({ id, label, Icon, list }) => (
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
                {list.some(needsAction) && (
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
                {key === 'all' ? 'Tất cả' : STATUS[key].label}
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
              placeholder={isOrders ? 'Tìm theo mã đơn hoặc tên sản phẩm' : 'Tìm theo mã yêu cầu hoặc tên sản phẩm'}
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

        {pageItems.length === 0 ? (
          <p className="orders-empty">Không tìm thấy {isOrders ? 'đơn hàng' : 'yêu cầu'} nào phù hợp.</p>
        ) : (
          <ul className="order-list">
            {pageItems.map((item) => {
              const totals = item.lines.map(lineTotal)
              const priced = totals.every((v) => v != null)
              const total = priced ? totals.reduce((s, v) => s + v, 0) + (item.shippingFee || 0) : null
              const action = PRIMARY_ACTION[item.status]
              const isOpen = openReason === item.code
              const { tone, Icon: MsgIcon } = STATUS[item.status]

              return (
                <li key={item.code} className={`order-card ${needsAction(item) ? 'is-action' : ''}`}>
                  <div className="order-card__head">
                    <strong>
                      {isOrders ? 'Mã đơn' : 'Mã yêu cầu'}: {item.code}
                    </strong>
                    <span className="order-card__date">
                      {isOrders ? 'Ngày đặt' : 'Ngày gửi'}: {formatDate(item.date)}
                      {item.wantDate && <> · Muốn nhận: {formatDate(item.wantDate)}</>}
                    </span>
                    <StatusChip status={item.status} />
                  </div>

                  <ul className="order-card__lines">
                    {item.lines.map((line) => (
                      <li key={line.name + line.variant} className="order-line">
                        <img className="order-line__thumb" src={line.image} alt="" />
                        <div className="order-line__name">
                          <strong>{line.name}</strong>
                          <span>{line.variant}</span>
                        </div>
                        <span className="order-line__qty">Số lượng: {line.qty}</span>
                        <span className={`order-line__price ${line.price == null ? 'is-pending' : ''}`}>
                          {line.price != null
                            ? formatPrice(lineTotal(line))
                            : item.status === 'rejected'
                              ? '—'
                              : 'Chờ báo giá'}
                        </span>
                      </li>
                    ))}
                  </ul>

                  <div className="order-card__foot">
                    {total != null && (
                      <p className="order-card__total">
                        {isOrders ? 'Tổng thanh toán' : 'Giá đã chốt'}: <strong>{formatPrice(total)}</strong>
                      </p>
                    )}
                    <p className={`order-card__msg order-card__msg--${tone}`}>
                      <MsgIcon size={18} />
                      {item.message}
                    </p>
                    <div className="order-card__actions">
                      {item.reason ? (
                        <button
                          type="button"
                          className="order-btn"
                          aria-expanded={isOpen}
                          onClick={() => setOpenReason(isOpen ? null : item.code)}
                        >
                          Xem lý do
                          {isOpen ? <ChevronUpIcon size={18} /> : <ChevronDownIcon size={18} />}
                        </button>
                      ) : (
                        <button type="button" className="order-btn">
                          Xem chi tiết
                        </button>
                      )}
                      {action && (
                        <button type="button" className="order-btn order-btn--primary">
                          {action}
                        </button>
                      )}
                    </div>
                  </div>

                  {isOpen && (
                    <p className="order-card__reason">
                      <InfoIcon size={20} />
                      <span>
                        <strong>Lý do:</strong> {item.reason}
                      </span>
                    </p>
                  )}
                </li>
              )
            })}
          </ul>
        )}

        {pageCount > 1 && <Pagination page={safePage} totalPages={pageCount} onChange={setPage} />}
      </div>
    </AccountShell>
  )
}

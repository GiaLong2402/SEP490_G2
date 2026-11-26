import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { BellIcon } from '@/components/icons/Icons'
import { ROUTES } from '@/constants/routes'
import { notificationsApi } from '@/api/notifications'
import { formatDateTime } from '@/utils/date'
import iconOrderConfirmed from '@/assets/notifications/order-confirmed.png'
import iconOrderShipping from '@/assets/notifications/order-shipping.png'
import iconBulkRequest from '@/assets/notifications/bulk-request.png'
import iconPaymentSuccess from '@/assets/notifications/payment-success.png'
import iconOrderCompleted from '@/assets/notifications/order-completed.png'
import iconReviewRequest from '@/assets/notifications/review-request.png'
import './NotificationBell.css'

/** Map type → icon */
const NOTIFICATION_ICONS = {
  'order-confirmed': iconOrderConfirmed,
  'order-shipping': iconOrderShipping,
  'bulk-request': iconBulkRequest,
  'quote-sent': iconBulkRequest,
  'payment-success': iconPaymentSuccess,
  'settlement-ready': iconPaymentSuccess,
  'order-completed': iconOrderCompleted,
  'review-request': iconReviewRequest,
}

const TABS = [
  { id: 'all', label: 'Tất cả' },
  { id: 'unread', label: 'Chưa đọc' },
]

/**
 * Chuông thông báo + dropdown — GET /notifications khi đã login.
 */
export default function NotificationBell({ isLoggedIn }) {
  const navigate = useNavigate()
  const [isOpen, setIsOpen] = useState(false)
  const [activeTab, setActiveTab] = useState('all')
  const [notifications, setNotifications] = useState([])
  const [loading, setLoading] = useState(false)
  const wrapRef = useRef(null)

  const load = useCallback(async () => {
    if (!isLoggedIn) {
      setNotifications([])
      return
    }
    setLoading(true)
    try {
      const list = await notificationsApi.list()
      setNotifications(Array.isArray(list) ? list : [])
    } catch {
      setNotifications([])
    } finally {
      setLoading(false)
    }
  }, [isLoggedIn])

  useEffect(() => {
    load()
    if (!isLoggedIn) return undefined
    const timer = window.setInterval(load, 60_000)
    return () => window.clearInterval(timer)
  }, [isLoggedIn, load])

  useEffect(() => {
    if (!isOpen) return undefined
    if (isLoggedIn) load()

    function handleClickOutside(e) {
      if (wrapRef.current && !wrapRef.current.contains(e.target)) setIsOpen(false)
    }

    function handleEscape(e) {
      if (e.key === 'Escape') setIsOpen(false)
    }

    document.addEventListener('mousedown', handleClickOutside)
    document.addEventListener('keydown', handleEscape)
    return () => {
      document.removeEventListener('mousedown', handleClickOutside)
      document.removeEventListener('keydown', handleEscape)
    }
  }, [isOpen, isLoggedIn, load])

  const unreadCount = isLoggedIn ? notifications.filter((n) => !n.isRead).length : 0
  const visibleList =
    activeTab === 'unread' ? notifications.filter((n) => !n.isRead) : notifications

  async function markAsRead(n) {
    if (!n.isRead) {
      setNotifications((list) => list.map((row) => (row.id === n.id ? { ...row, isRead: true } : row)))
      try {
        await notificationsApi.markRead(n.id)
      } catch {
        // giữ UI đã đọc; lần load sau sẽ khớp server
      }
    }
    setIsOpen(false)
    if (n.link) navigate(n.link)
  }

  async function markAllAsRead() {
    setNotifications((list) => list.map((row) => ({ ...row, isRead: true })))
    try {
      await notificationsApi.markAllRead()
    } catch {
      load()
    }
  }

  return (
    <div className="notif" ref={wrapRef}>
      <button
        type="button"
        className={`site-bell ${isOpen ? 'is-open' : ''}`}
        aria-label={unreadCount > 0 ? `Thông báo, ${unreadCount} chưa đọc` : 'Thông báo'}
        aria-expanded={isOpen}
        onClick={() => setIsOpen(!isOpen)}
      >
        <BellIcon size={22} />
        {unreadCount > 0 && (
          <span className="site-bell__badge" aria-hidden="true">
            {unreadCount > 9 ? '9+' : unreadCount}
          </span>
        )}
      </button>

      {isOpen && (
        <div className="notif__panel" role="dialog" aria-label="Thông báo">
          <div className="notif__head">
            <h2>
              Thông báo
              {unreadCount > 0 && <span className="notif__new">{unreadCount} mới</span>}
            </h2>
            {isLoggedIn && unreadCount > 0 && (
              <button type="button" className="notif__mark-all" onClick={markAllAsRead}>
                Đánh dấu tất cả đã đọc
              </button>
            )}
          </div>

          {!isLoggedIn && (
            <div className="notif__empty">
              <p>Đăng nhập để xem thông báo về đơn hàng của bạn.</p>
              <Link to={ROUTES.LOGIN} className="notif__login" onClick={() => setIsOpen(false)}>
                Đăng nhập
              </Link>
            </div>
          )}

          {isLoggedIn && (
            <>
              <div className="notif__tabs" role="tablist">
                {TABS.map((tab) => (
                  <button
                    key={tab.id}
                    type="button"
                    role="tab"
                    aria-selected={activeTab === tab.id}
                    className={activeTab === tab.id ? 'is-active' : ''}
                    onClick={() => setActiveTab(tab.id)}
                  >
                    {tab.label}
                    {tab.id === 'unread' && ` (${unreadCount})`}
                  </button>
                ))}
              </div>

              {loading && visibleList.length === 0 ? (
                <p className="notif__empty">Đang tải…</p>
              ) : visibleList.length === 0 ? (
                <p className="notif__empty">
                  {activeTab === 'unread' ? 'Không có thông báo chưa đọc.' : 'Chưa có thông báo.'}
                </p>
              ) : (
                <ul className="notif__list">
                  {visibleList.map((n) => (
                    <li key={n.id}>
                      <button
                        type="button"
                        className={`notif__item ${n.isRead ? '' : 'is-unread'}`}
                        onClick={() => markAsRead(n)}
                      >
                        <img
                          src={NOTIFICATION_ICONS[n.type] || iconOrderConfirmed}
                          alt=""
                          className="notif__icon"
                        />
                        <span className="notif__text">
                          <strong>{n.title}</strong>
                          <span>{n.body}</span>
                          <small>{formatDateTime(n.createdAt)}</small>
                        </span>
                        {!n.isRead && <span className="notif__dot" aria-label="Chưa đọc" />}
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </>
          )}
        </div>
      )}
    </div>
  )
}

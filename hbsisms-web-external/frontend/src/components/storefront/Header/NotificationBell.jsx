import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { BellIcon } from '@/components/icons/Icons'
import { ROUTES } from '@/constants/routes'
import { DEMO_NOTIFICATIONS } from '@/data/demoNotifications'
import iconOrderConfirmed from '@/assets/notifications/order-confirmed.png'
import iconOrderShipping from '@/assets/notifications/order-shipping.png'
import iconBulkRequest from '@/assets/notifications/bulk-request.png'
import iconPaymentSuccess from '@/assets/notifications/payment-success.png'
import iconOrderCompleted from '@/assets/notifications/order-completed.png'
import iconReviewRequest from '@/assets/notifications/review-request.png'
import './NotificationBell.css'

// Loại thông báo => icon. Thêm loại mới: thêm 1 dòng ở đây + 1 ảnh trong assets/notifications
const NOTIFICATION_ICONS = {
  'order-confirmed': iconOrderConfirmed,
  'order-shipping': iconOrderShipping,
  'bulk-request': iconBulkRequest,
  'payment-success': iconPaymentSuccess,
  'order-completed': iconOrderCompleted,
  'review-request': iconReviewRequest,
}

const TABS = [
  { id: 'all', label: 'Tất cả' },
  { id: 'unread', label: 'Chưa đọc' },
]

/**
 * Chuông thông báo + khung dropdown xổ xuống khi bấm.
 *
 * props:
 * - isLoggedIn: chưa đăng nhập => khung chỉ hiện lời mời đăng nhập
 */
export default function NotificationBell({ isLoggedIn }) {
  const [isOpen, setIsOpen] = useState(false)
  const [activeTab, setActiveTab] = useState('all')
  // DEMO: giữ danh sách ở đây. Sau này lấy từ API.
  const [notifications, setNotifications] = useState(DEMO_NOTIFICATIONS)
  const wrapRef = useRef(null)

  const unreadCount = isLoggedIn ? notifications.filter((n) => !n.isRead).length : 0

  // Tab "Chưa đọc" chỉ hiện thông báo isRead = false
  const visibleList =
    activeTab === 'unread' ? notifications.filter((n) => !n.isRead) : notifications

  // Đóng khung khi bấm ra ngoài hoặc nhấn Esc (giống menu tài khoản)
  useEffect(() => {
    if (!isOpen) return undefined

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
  }, [isOpen])

  // Bấm 1 thông báo => đánh dấu đã đọc thông báo đó
  function markAsRead(id) {
    setNotifications((list) => list.map((n) => (n.id === id ? { ...n, isRead: true } : n)))
  }

  function markAllAsRead() {
    setNotifications((list) => list.map((n) => ({ ...n, isRead: true })))
  }

  return (
    <div className="notif" ref={wrapRef}>
      {/* ----- Nút chuông ----- */}
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

      {/* ----- Khung dropdown ----- */}
      {isOpen && (
        <div className="notif__panel" role="dialog" aria-label="Thông báo">
          {/* Tiêu đề */}
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

          {/* Chưa đăng nhập */}
          {!isLoggedIn && (
            <div className="notif__empty">
              <p>Đăng nhập để xem thông báo về đơn hàng của bạn.</p>
              <Link to={ROUTES.LOGIN} className="notif__login" onClick={() => setIsOpen(false)}>
                Đăng nhập
              </Link>
            </div>
          )}

          {/* Đã đăng nhập */}
          {isLoggedIn && (
            <>
              {/* Tab */}
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

              {/* Danh sách (cuộn được khi dài) */}
              {visibleList.length === 0 ? (
                <p className="notif__empty">Bạn đã đọc hết thông báo.</p>
              ) : (
                <ul className="notif__list">
                  {visibleList.map((n) => (
                    <li key={n.id}>
                      <button
                        type="button"
                        className={`notif__item ${n.isRead ? '' : 'is-unread'}`}
                        onClick={() => markAsRead(n.id)}
                      >
                        <img src={NOTIFICATION_ICONS[n.type]} alt="" className="notif__icon" />
                        <span className="notif__text">
                          <strong>{n.title}</strong>
                          <span>{n.message}</span>
                          <small>{n.time}</small>
                        </span>
                        {/* Chấm xanh = chưa đọc */}
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

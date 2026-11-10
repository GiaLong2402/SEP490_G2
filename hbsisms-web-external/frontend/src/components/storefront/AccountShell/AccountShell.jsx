import { useEffect, useRef } from 'react'
import { NavLink, useLocation, useNavigate } from 'react-router-dom'
import PageBanner from '@/components/storefront/PageBanner/PageBanner'
import {
  ClipboardIcon,
  LogoutIcon,
  MapPinIcon,
  TextAaIcon,
  UserIcon,
} from '@/components/icons/Icons'
import { ROUTES } from '@/constants/routes'
import { useAuth } from '@/context/AuthContext'
import { getInitials } from '@/utils/initials'
import './AccountShell.css'

const NAV = [
  { to: ROUTES.ACCOUNT, label: 'Tài khoản', Icon: UserIcon },
  { to: ROUTES.ORDERS, label: 'Đơn & yêu cầu của tôi', Icon: ClipboardIcon },
  { to: ROUTES.ADDRESSES, label: 'Sổ địa chỉ', Icon: MapPinIcon },
  { to: ROUTES.DISPLAY_SETTINGS, label: 'Cài đặt hiển thị', Icon: TextAaIcon },
]

/**
 * Khung chung cho các trang tài khoản: banner + sidebar trái + nội dung phải.
 * Mobile: sidebar thành hàng mục cuộn ngang.
 */
export default function AccountShell({ title, breadcrumbs, children }) {
  const { user, isLoggedIn, logout } = useAuth()
  const navigate = useNavigate()
  const { pathname } = useLocation()
  const listRef = useRef(null)

  // Mobile: hàng mục cuộn ngang → kéo mục đang mở vào giữa tầm nhìn
  useEffect(() => {
    const list = listRef.current
    const active = list?.querySelector('.is-active')
    if (!list || !active || list.scrollWidth <= list.clientWidth) return
    const a = active.getBoundingClientRect()
    const l = list.getBoundingClientRect()
    list.scrollLeft += a.left - l.left - (list.clientWidth - a.width) / 2
  }, [pathname])

  const name = user?.name || 'Khách hàng'

  function handleLogout() {
    logout()
    navigate(ROUTES.HOME)
  }

  return (
    <div className="account-shell">
      <PageBanner title={title} breadcrumbs={breadcrumbs} />

      <div className="container account-shell__body">
        <aside className="account-nav" aria-label="Mục tài khoản">
          <div className="account-nav__head">
            <span className="account-nav__avatar" aria-hidden="true">
              {getInitials(name)}
            </span>
            <span className="account-nav__who">
              <strong>{name}</strong>
              <span>{isLoggedIn ? 'Khách hàng' : 'Chưa đăng nhập'}</span>
            </span>
          </div>

          <nav className="account-nav__list" ref={listRef}>
            {NAV.map(({ to, label, Icon }) => (
              <NavLink
                key={to}
                to={to}
                end={to === ROUTES.ACCOUNT}
                className={({ isActive }) => `account-nav__item ${isActive ? 'is-active' : ''}`}
              >
                <Icon size={20} />
                <span>{label}</span>
              </NavLink>
            ))}
            {isLoggedIn && (
              <button
                type="button"
                className="account-nav__item account-nav__item--danger"
                onClick={handleLogout}
              >
                <LogoutIcon size={20} />
                <span>Đăng xuất</span>
              </button>
            )}
          </nav>
        </aside>

        <div className="account-shell__main">{children}</div>
      </div>
    </div>
  )
}

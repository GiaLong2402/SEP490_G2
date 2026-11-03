import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  ChevronDownIcon,
  ClipboardIcon,
  LogoutIcon,
  TextAaIcon,
  UserIcon,
} from '@/components/icons/Icons'
import { ROUTES } from '@/constants/routes'
import { getInitials } from '@/utils/initials'

/**
 * Nút avatar + tên → menu tài khoản (sau khi đăng nhập)
 */
export default function UserAccountMenu({ userName, onLogout, workAreaLink }) {
  const [open, setOpen] = useState(false)
  const wrapRef = useRef(null)

  // Đóng menu khi click ra ngoài
  useEffect(() => {
    if (!open) return undefined

    function handleClickOutside(e) {
      if (wrapRef.current && !wrapRef.current.contains(e.target)) {
        setOpen(false)
      }
    }

    function handleEscape(e) {
      if (e.key === 'Escape') setOpen(false)
    }

    document.addEventListener('mousedown', handleClickOutside)
    document.addEventListener('keydown', handleEscape)
    return () => {
      document.removeEventListener('mousedown', handleClickOutside)
      document.removeEventListener('keydown', handleEscape)
    }
  }, [open])

  function closeMenu() {
    setOpen(false)
  }

  return (
    <div className="site-account" ref={wrapRef}>
      <button
        type="button"
        className={`site-account__trigger ${open ? 'is-open' : ''}`}
        aria-expanded={open}
        aria-haspopup="menu"
        onClick={() => setOpen((v) => !v)}
      >
        <span className="site-account__avatar" aria-hidden="true">
          {getInitials(userName)}
        </span>
        <span className="site-account__name">{userName}</span>
        <ChevronDownIcon size={16} className="site-account__chevron" />
      </button>

      {open && (
        <div className="site-account__menu" role="menu">
          <Link
            role="menuitem"
            to={ROUTES.ACCOUNT}
            className="site-account__item"
            onClick={closeMenu}
          >
            <UserIcon size={18} />
            <span>Tài khoản</span>
          </Link>

          <Link
            role="menuitem"
            to={ROUTES.ORDERS}
            className="site-account__item"
            onClick={closeMenu}
          >
            <ClipboardIcon size={18} />
            <span>Đơn &amp; Yêu cầu của tôi</span>
          </Link>

          <Link
            role="menuitem"
            to={ROUTES.DISPLAY_SETTINGS}
            className="site-account__item"
            onClick={closeMenu}
          >
            <TextAaIcon size={18} />
            <span>Cài đặt chữ lớn &amp; Hiển thị</span>
          </Link>

          {workAreaLink && (
            <Link
              role="menuitem"
              to={workAreaLink}
              className="site-account__item"
              onClick={closeMenu}
            >
              <ClipboardIcon size={18} />
              <span>Khu vực làm việc</span>
            </Link>
          )}

          <div className="site-account__divider" role="separator" />

          <button
            type="button"
            role="menuitem"
            className="site-account__item site-account__item--danger"
            onClick={() => {
              closeMenu()
              onLogout()
            }}
          >
            <LogoutIcon size={18} />
            <span>Đăng xuất</span>
          </button>
        </div>
      )}
    </div>
  )
}

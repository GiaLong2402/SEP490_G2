import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  ChevronDownIcon,
  ChevronRightIcon,
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
        <div className="site-account__menu" role="menu" aria-label="Tài khoản">
          <div className="site-account__head">
            <span className="site-account__head-avatar" aria-hidden="true">
              {getInitials(userName)}
            </span>
            <span className="site-account__head-text">
              <strong>{userName}</strong>
              <span>Tài khoản của bạn</span>
            </span>
          </div>

          <div className="site-account__list">
            <Link role="menuitem" to={ROUTES.ACCOUNT} className="site-account__item" onClick={closeMenu}>
              <span className="site-account__icon">
                <UserIcon size={24} />
              </span>
              <span className="site-account__text">
                <strong>Tài khoản</strong>
                <span>Thông tin cá nhân, địa chỉ</span>
              </span>
              <ChevronRightIcon size={18} className="site-account__arrow" />
            </Link>

            <Link role="menuitem" to={ROUTES.ORDERS} className="site-account__item" onClick={closeMenu}>
              <span className="site-account__icon">
                <ClipboardIcon size={24} />
              </span>
              <span className="site-account__text">
                <strong>Đơn &amp; Yêu cầu của tôi</strong>
                <span>Theo dõi đơn và yêu cầu</span>
              </span>
              <ChevronRightIcon size={18} className="site-account__arrow" />
            </Link>

            <Link
              role="menuitem"
              to={ROUTES.DISPLAY_SETTINGS}
              className="site-account__item"
              onClick={closeMenu}
            >
              <span className="site-account__icon">
                <TextAaIcon size={24} />
              </span>
              <span className="site-account__text">
                <strong>Cài đặt chữ lớn &amp; Hiển thị</strong>
                <span>Phóng to chữ cho dễ đọc</span>
              </span>
              <ChevronRightIcon size={18} className="site-account__arrow" />
            </Link>

            {workAreaLink && (
              <Link role="menuitem" to={workAreaLink} className="site-account__item" onClick={closeMenu}>
                <span className="site-account__icon">
                  <ClipboardIcon size={24} />
                </span>
                <span className="site-account__text">
                  <strong>Khu vực làm việc</strong>
                  <span>Màn hình cho nhân viên</span>
                </span>
                <ChevronRightIcon size={18} className="site-account__arrow" />
              </Link>
            )}
          </div>

          <div className="site-account__foot">
            <button
              type="button"
              role="menuitem"
              className="site-account__item site-account__item--danger"
              onClick={() => {
                closeMenu()
                onLogout()
              }}
            >
              <span className="site-account__icon">
                <LogoutIcon size={24} />
              </span>
              <span className="site-account__text">
                <strong>Đăng xuất</strong>
              </span>
            </button>
          </div>
        </div>
      )}
    </div>
  )
}

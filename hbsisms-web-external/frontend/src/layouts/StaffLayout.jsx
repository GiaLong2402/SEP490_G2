import { Link, NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '@/context/AuthContext'
import { ROLE_LABELS } from '@/constants/roles'
import { ROUTES } from '@/constants/routes'
import { getHomePathForRole } from '@/auth/roleUtils'
import './StaffLayout.css'

/**
 * Layout khu vực nội bộ (sale / warehouse / owner / admin).
 * Tách riêng — không dùng Header/Footer storefront.
 */
export default function StaffLayout({ title, navItems = [] }) {
  const { user, role, logout } = useAuth()

  return (
    <div className="admin-shell staff-layout">
      <aside className="staff-sidebar">
        <Link to={getHomePathForRole(role)} className="staff-sidebar__brand">
          Hòa Bình
          <small>Khu vực nội bộ</small>
        </Link>

        <nav className="staff-sidebar__nav" aria-label="Menu nội bộ">
          {navItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) =>
                `staff-sidebar__link ${isActive ? 'is-active' : ''}`
              }
            >
              {item.label}
            </NavLink>
          ))}
        </nav>

        <div className="staff-sidebar__footer">
          <Link to={ROUTES.HOME}>← Về trang bán hàng</Link>
        </div>
      </aside>

      <div className="staff-main">
        <header className="staff-topbar">
          <div>
            <h1>{title}</h1>
            <p>
              {user?.name} · {ROLE_LABELS[role]}
            </p>
          </div>
          <button type="button" className="staff-logout" onClick={logout}>
            Đăng xuất
          </button>
        </header>
        <div className="staff-content">
          <Outlet />
        </div>
      </div>
    </div>
  )
}

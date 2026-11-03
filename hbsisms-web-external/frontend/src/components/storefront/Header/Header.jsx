import { useState } from 'react'
import { Link, NavLink, useNavigate } from 'react-router-dom'
import BrandLogo from '@/components/brand/BrandLogo'
import {
  CartIcon,
  ChevronDownIcon,
  MenuIcon,
  SearchIcon,
  UserIcon,
} from '@/components/icons/Icons'
import { HOME_CATEGORIES } from '@/constants/categories'
import { ROUTES } from '@/constants/routes'
import { useAuth } from '@/context/AuthContext'
import { useCart } from '@/context/CartContext'
import { ROLES } from '@/constants/roles'
import { getHomePathForRole } from '@/auth/roleUtils'
import NotificationBell from './NotificationBell'
import UserAccountMenu from './UserAccountMenu'
import './Header.css'

const NAV_ITEMS = [
  { to: ROUTES.HOME, label: 'Trang chủ', end: true },
  { to: ROUTES.PRODUCTS, label: 'Sản phẩm' },
  { to: ROUTES.ABOUT, label: 'Về Hòa Bình' },
  { to: ROUTES.CONTACT, label: 'Liên hệ' },
]

export default function Header() {
  const { user, isLoggedIn, role, logout } = useAuth()
  const { totalItems } = useCart()
  const navigate = useNavigate()
  const [keyword, setKeyword] = useState('')
  const [openCategories, setOpenCategories] = useState(false)

  function handleSearch(e) {
    e.preventDefault()
    const q = keyword.trim()
    navigate(q ? `${ROUTES.PRODUCTS}?q=${encodeURIComponent(q)}` : ROUTES.PRODUCTS)
  }

  const isStaff =
    isLoggedIn && role !== ROLES.CUSTOMER && role !== ROLES.GUEST

  return (
    <header className="site-header">
      <div className="site-header__top container">
        <Link to={ROUTES.HOME} className="site-logo" aria-label="Hòa Bình trang chủ">
          <BrandLogo />
        </Link>

        <form className="site-search" onSubmit={handleSearch} role="search">
          <SearchIcon className="site-search__icon" />
          <input
            type="search"
            placeholder="Tìm hải sản, sản phẩm bạn muốn..."
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            aria-label="Tìm kiếm sản phẩm"
          />
        </form>

        {/* Thứ tự: Giỏ → Chuông → Đăng nhập / Menu tài khoản */}
        <div className="site-header__actions">
          <Link
            to={ROUTES.CART}
            className="site-cart"
            aria-label={`Giỏ hàng, ${totalItems} sản phẩm`}
          >
            <CartIcon size={22} />
            {totalItems > 0 && (
              <span className="site-cart__badge">{totalItems}</span>
            )}
          </Link>

          {/* Bấm chuông => xổ khung thông báo (xem NotificationBell.jsx) */}
          <NotificationBell isLoggedIn={isLoggedIn} />

          {isLoggedIn ? (
            <UserAccountMenu
              userName={user?.name || 'Tài khoản'}
              onLogout={logout}
              workAreaLink={isStaff ? getHomePathForRole(role) : null}
            />
          ) : (
            <Link to={ROUTES.LOGIN} className="site-login">
              <UserIcon size={22} />
              <span>Đăng nhập</span>
            </Link>
          )}
        </div>
      </div>

      <div className="site-header__nav">
        <div className="container site-header__nav-inner">
          <div className="site-cats">
            <button
              type="button"
              className="site-cats__toggle"
              aria-expanded={openCategories}
              aria-controls="category-menu"
              onClick={() => setOpenCategories((v) => !v)}
            >
              <MenuIcon size={18} />
              <span>Danh mục sản phẩm</span>
              <ChevronDownIcon size={16} />
            </button>

            {openCategories && (
              <ul id="category-menu" className="site-cats__menu" role="menu">
                {HOME_CATEGORIES.map((cat) => (
                  <li key={cat.id} role="none">
                    <Link
                      role="menuitem"
                      to={`${ROUTES.PRODUCTS}?category=${cat.id}`}
                      onClick={() => setOpenCategories(false)}
                    >
                      {cat.name}
                    </Link>
                  </li>
                ))}
              </ul>
            )}
          </div>

          <nav className="site-nav" aria-label="Menu chính">
            {NAV_ITEMS.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.end}
                className={({ isActive }) =>
                  `site-nav__link ${isActive ? 'is-active' : ''}`
                }
              >
                {item.label}
              </NavLink>
            ))}
          </nav>
        </div>
      </div>
    </header>
  )
}

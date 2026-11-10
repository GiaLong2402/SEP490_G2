import { useEffect, useRef, useState } from 'react'
import { Link, NavLink, useNavigate } from 'react-router-dom'
import BrandLogo from '@/components/brand/BrandLogo'
import {
  CartIcon,
  ChevronDownIcon,
  ChevronRightIcon,
  MenuIcon,
  SearchIcon,
  UserIcon,
} from '@/components/icons/Icons'
import { api } from '@/api/client'
import { ROUTES } from '@/constants/routes'
import { useAuth } from '@/context/AuthContext'
import { useCart } from '@/context/CartContext'
import { getRootCategoriesWithChildren } from '@/utils/categoryTree'
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
  const { user, isLoggedIn, logout } = useAuth()
  const { totalItems } = useCart()
  const [navCategories, setNavCategories] = useState([])
  const catsRef = useRef(null)

  useEffect(() => {
    const ac = new AbortController()
    api
      .get('/categories', null, ac.signal)
      .then((rows) => setNavCategories(getRootCategoriesWithChildren(rows)))
      .catch(() => setNavCategories([]))
    return () => ac.abort()
  }, [])

  const navigate = useNavigate()
  const [keyword, setKeyword] = useState('')
  const [openCategories, setOpenCategories] = useState(false)

  useEffect(() => {
    if (!openCategories) return undefined

    function handlePointerDown(event) {
      if (catsRef.current && !catsRef.current.contains(event.target)) {
        setOpenCategories(false)
      }
    }

    function handleKeyDown(event) {
      if (event.key === 'Escape') setOpenCategories(false)
    }

    document.addEventListener('pointerdown', handlePointerDown)
    document.addEventListener('keydown', handleKeyDown)
    return () => {
      document.removeEventListener('pointerdown', handlePointerDown)
      document.removeEventListener('keydown', handleKeyDown)
    }
  }, [openCategories])

  function handleSearch(e) {
    e.preventDefault()
    const q = keyword.trim()
    navigate(q ? `${ROUTES.PRODUCTS}?q=${encodeURIComponent(q)}` : ROUTES.PRODUCTS)
  }

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

          <NotificationBell isLoggedIn={isLoggedIn} />

          {isLoggedIn ? (
            <UserAccountMenu
              userName={user?.name || 'Tài khoản'}
              onLogout={logout}
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
          <div className="site-cats" ref={catsRef}>
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
                {navCategories.length === 0 ? (
                  <li role="none">
                    <Link role="menuitem" to={ROUTES.PRODUCTS} onClick={() => setOpenCategories(false)}>
                      Tất cả sản phẩm
                    </Link>
                  </li>
                ) : (
                  navCategories.map((cat) => (
                    <li
                      key={cat.id}
                      role="none"
                      className={`site-cats__item ${cat.children.length ? 'has-children' : ''}`}
                    >
                      <Link
                        role="menuitem"
                        to={`${ROUTES.PRODUCTS}?category=${cat.id}`}
                        className="site-cats__parent"
                        onClick={() => setOpenCategories(false)}
                      >
                        <span>{cat.name}</span>
                        {cat.children.length > 0 ? <ChevronRightIcon size={14} /> : null}
                      </Link>
                      {cat.children.length > 0 ? (
                        <ul className="site-cats__submenu" role="menu">
                          {cat.children.map((child) => (
                            <li key={child.id} role="none">
                              <Link
                                role="menuitem"
                                to={`${ROUTES.PRODUCTS}?category=${child.id}`}
                                onClick={() => setOpenCategories(false)}
                              >
                                {child.name}
                              </Link>
                            </li>
                          ))}
                        </ul>
                      ) : null}
                    </li>
                  ))
                )}
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

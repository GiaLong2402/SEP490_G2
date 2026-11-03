import { Link, Outlet } from 'react-router-dom'
import BrandLogo from '@/components/brand/BrandLogo'
import Footer from '@/components/storefront/Footer/Footer'
import { ROUTES } from '@/constants/routes'
import './AuthLayout.css'

/**
 * Layout trang đăng nhập: chỉ logo + tên cửa hàng, không search/menu/giỏ.
 * Footer vẫn dùng chung storefront.
 */
export default function AuthLayout() {
  return (
    <div className="auth-layout">
      <header className="auth-header">
        <div className="container auth-header__inner">
          <Link to={ROUTES.HOME} className="auth-header__logo" aria-label="Hòa Bình trang chủ">
            <BrandLogo />
          </Link>
        </div>
      </header>

      <main>
        <Outlet />
      </main>

      <Footer />
    </div>
  )
}

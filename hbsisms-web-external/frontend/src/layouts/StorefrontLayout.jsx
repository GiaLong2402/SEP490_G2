import { Outlet } from 'react-router-dom'
import Header from '@/components/storefront/Header/Header'
import Footer from '@/components/storefront/Footer/Footer'

/** Layout bán hàng: Header + nội dung trang + Footer */
export default function StorefrontLayout() {
  return (
    <div className="storefront-layout">
      <Header />
      <main>
        <Outlet />
      </main>
      <Footer />
    </div>
  )
}

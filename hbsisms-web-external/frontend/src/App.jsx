import { BrowserRouter, Route, Routes } from 'react-router-dom'

import StorefrontLayout from '@/layouts/StorefrontLayout'
import AuthLayout from '@/layouts/AuthLayout'

import HomePage from '@/pages/storefront/HomePage/HomePage'
import ProductsPage from '@/pages/storefront/ProductsPage/ProductsPage'
import ProductDetailPage from '@/pages/storefront/ProductDetailPage/ProductDetailPage'
import AboutPage from '@/pages/storefront/AboutPage/AboutPage'
import ContactPage from '@/pages/storefront/ContactPage/ContactPage'
import CartPage from '@/pages/storefront/CartPage/CartPage'
import CheckoutPage from '@/pages/storefront/CheckoutPage/CheckoutPage'
import PaymentResultPage from '@/pages/storefront/PaymentResultPage/PaymentResultPage'
import ResumePaymentPage from '@/pages/storefront/ResumePaymentPage/ResumePaymentPage'
import RequestDetailPage from '@/pages/storefront/RequestDetailPage/RequestDetailPage'
import LoginPage from '@/pages/storefront/LoginPage/LoginPage'
import AccountPage from '@/pages/storefront/AccountPage/AccountPage'
import AddressBookPage from '@/pages/storefront/AccountPage/AddressBookPage'
import OrdersPage from '@/pages/storefront/OrdersPage/OrdersPage'
import OrderDetailPage from '@/pages/storefront/OrderDetailPage/OrderDetailPage'
import ReceiptPage from '@/pages/storefront/ReceiptPage/ReceiptPage'
import DisplaySettingsPage from '@/pages/storefront/DisplaySettingsPage/DisplaySettingsPage'

import NotFoundPage from '@/pages/shared/NotFoundPage'

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        {/* ===== Storefront: chỉ guest + customer (nhân viên dùng web nội bộ) ===== */}
        <Route element={<StorefrontLayout />}>
          <Route index element={<HomePage />} />
          <Route path="san-pham" element={<ProductsPage />} />
          <Route path="san-pham/:slug" element={<ProductDetailPage />} />
          <Route path="ve-hoa-binh" element={<AboutPage />} />
          <Route path="lien-he" element={<ContactPage />} />
          <Route path="gio-hang" element={<CartPage />} />
          <Route path="thanh-toan" element={<CheckoutPage />} />
          <Route path="thanh-toan/ket-qua" element={<PaymentResultPage />} />
          <Route path="thanh-toan/don/:code" element={<ResumePaymentPage />} />
          <Route path="tai-khoan" element={<AccountPage />} />
          <Route path="tai-khoan/so-dia-chi" element={<AddressBookPage />} />
          <Route path="don-hang" element={<OrdersPage />} />
          <Route path="don-hang/:code" element={<OrderDetailPage />} />
          <Route path="don-hang/:code/bien-lai" element={<ReceiptPage />} />
          <Route path="yeu-cau/:code" element={<RequestDetailPage />} />
          <Route path="cai-dat-hien-thi" element={<DisplaySettingsPage />} />
          {/* 404 — đặt cuối cùng */}
          <Route path="*" element={<NotFoundPage />} />
        </Route>

        {/* Đăng nhập: header chỉ logo, không menu/search/giỏ */}
        <Route element={<AuthLayout />}>
          <Route path="dang-nhap" element={<LoginPage />} />
        </Route>

      </Routes>
    </BrowserRouter>
  )
}

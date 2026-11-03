import { BrowserRouter, Route, Routes } from 'react-router-dom'
import ProtectedRoute from '@/auth/ProtectedRoute'
import { ROLES } from '@/constants/roles'
import { ROUTES } from '@/constants/routes'

import StorefrontLayout from '@/layouts/StorefrontLayout'
import AuthLayout from '@/layouts/AuthLayout'
import StaffLayout from '@/layouts/StaffLayout'

import HomePage from '@/pages/storefront/HomePage/HomePage'
import ProductsPage from '@/pages/storefront/ProductsPage/ProductsPage'
import ProductDetailPage from '@/pages/storefront/ProductDetailPage/ProductDetailPage'
import AboutPage from '@/pages/storefront/AboutPage/AboutPage'
import ContactPage from '@/pages/storefront/ContactPage/ContactPage'
import CartPage from '@/pages/storefront/CartPage/CartPage'
import LoginPage from '@/pages/storefront/LoginPage/LoginPage'
import AccountPlaceholderPage from '@/pages/storefront/AccountPlaceholderPage'

import SaleDashboardPage from '@/pages/staff/sale/SaleDashboardPage'
import WarehouseDashboardPage from '@/pages/staff/warehouse/WarehouseDashboardPage'
import OwnerDashboardPage from '@/pages/owner/OwnerDashboardPage'
import AdminDashboardPage from '@/pages/admin/AdminDashboardPage'

import UnauthorizedPage from '@/pages/shared/UnauthorizedPage'
import NotFoundPage from '@/pages/shared/NotFoundPage'

/** Menu sidebar theo từng role */
const SALE_NAV = [
  { to: ROUTES.SALE_DASHBOARD, label: 'Tổng quan', end: true },
]
const WAREHOUSE_NAV = [
  { to: ROUTES.WAREHOUSE_DASHBOARD, label: 'Tổng quan', end: true },
]
const OWNER_NAV = [
  { to: ROUTES.OWNER_DASHBOARD, label: 'Tổng quan', end: true },
]
const ADMIN_NAV = [
  { to: ROUTES.ADMIN_DASHBOARD, label: 'Tổng quan', end: true },
]

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        {/* ===== Storefront: guest + customer ===== */}
        <Route element={<StorefrontLayout />}>
          <Route index element={<HomePage />} />
          <Route path="san-pham" element={<ProductsPage />} />
          <Route path="san-pham/:slug" element={<ProductDetailPage />} />
          <Route path="ve-hoa-binh" element={<AboutPage />} />
          <Route path="lien-he" element={<ContactPage />} />
          <Route path="gio-hang" element={<CartPage />} />
          <Route
            path="tai-khoan"
            element={
              <AccountPlaceholderPage
                title="Tài khoản"
                description="Trang quản lý thông tin tài khoản (đang phát triển)."
              />
            }
          />
          <Route
            path="don-hang"
            element={
              <AccountPlaceholderPage
                title="Đơn & Yêu cầu của tôi"
                description="Danh sách đơn hàng và yêu cầu (đang phát triển)."
              />
            }
          />
          <Route
            path="cai-dat-hien-thi"
            element={
              <AccountPlaceholderPage
                title="Cài đặt chữ lớn & Hiển thị"
                description="Tuỳ chọn chữ lớn / hiển thị (đang phát triển)."
              />
            }
          />
          <Route path="khong-co-quyen" element={<UnauthorizedPage />} />
        </Route>

        {/* Đăng nhập: header chỉ logo, không menu/search/giỏ */}
        <Route element={<AuthLayout />}>
          <Route path="dang-nhap" element={<LoginPage />} />
        </Route>

        {/* ===== Nhân viên bán hàng ===== */}
        <Route
          path="nv-ban-hang"
          element={
            <ProtectedRoute allowedRoles={[ROLES.SALE_STAFF, ROLES.OWNER, ROLES.ADMIN]}>
              <StaffLayout title="Nhân viên bán hàng" navItems={SALE_NAV} />
            </ProtectedRoute>
          }
        >
          <Route index element={<SaleDashboardPage />} />
        </Route>

        {/* ===== Nhân viên kho ===== */}
        <Route
          path="nv-kho"
          element={
            <ProtectedRoute allowedRoles={[ROLES.WAREHOUSE_STAFF, ROLES.OWNER, ROLES.ADMIN]}>
              <StaffLayout title="Nhân viên kho" navItems={WAREHOUSE_NAV} />
            </ProtectedRoute>
          }
        >
          <Route index element={<WarehouseDashboardPage />} />
        </Route>

        {/* ===== Chủ cửa hàng ===== */}
        <Route
          path="chu-cua-hang"
          element={
            <ProtectedRoute allowedRoles={[ROLES.OWNER, ROLES.ADMIN]}>
              <StaffLayout title="Chủ cửa hàng" navItems={OWNER_NAV} />
            </ProtectedRoute>
          }
        >
          <Route index element={<OwnerDashboardPage />} />
        </Route>

        {/* ===== Admin ===== */}
        <Route
          path="quan-tri"
          element={
            <ProtectedRoute allowedRoles={[ROLES.ADMIN]}>
              <StaffLayout title="Quản trị" navItems={ADMIN_NAV} />
            </ProtectedRoute>
          }
        >
          <Route index element={<AdminDashboardPage />} />
        </Route>

        {/* 404 — đặt cuối cùng để không nuốt route nội bộ */}
        <Route element={<StorefrontLayout />}>
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}

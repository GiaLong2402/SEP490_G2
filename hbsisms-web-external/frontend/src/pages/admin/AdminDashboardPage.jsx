import PlaceholderPage from '@/pages/shared/PlaceholderPage'

export default function AdminDashboardPage() {
  return (
    <PlaceholderPage
      title="Quản trị hệ thống"
      description="Khu vực admin — quản lý người dùng, danh mục, cấu hình."
      bullets={[
        'Quản lý tài khoản & phân quyền',
        'Quản lý danh mục / sản phẩm',
        'Cấu hình hệ thống',
      ]}
    />
  )
}

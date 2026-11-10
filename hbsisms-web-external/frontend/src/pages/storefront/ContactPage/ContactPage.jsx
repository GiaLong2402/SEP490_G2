import Breadcrumbs from '@/components/storefront/Breadcrumbs/Breadcrumbs'
import { ROUTES } from '@/constants/routes'

export default function ContactPage() {
  return (
    <div className="simple-page container">
      <Breadcrumbs
        items={[
          { label: 'Trang chủ', to: ROUTES.HOME },
          { label: 'Liên hệ' },
        ]}
      />
      <h1>Liên hệ</h1>
      <p>
        Trang liên hệ đang để khung sẵn. Khi có hotline / địa chỉ / Facebook / Zalo
        chính thức, gắn vào đây — không bịa số điện thoại hay chứng nhận.
      </p>
      <div className="simple-page__card">
        <p><strong>Khu vực:</strong> Hải Phòng</p>
        <p><strong>Hỗ trợ:</strong> Đặt hàng qua website hoặc liên hệ cửa hàng khi cần tư vấn số lượng lớn.</p>
      </div>
    </div>
  )
}

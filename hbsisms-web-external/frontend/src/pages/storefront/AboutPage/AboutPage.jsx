import { Link } from 'react-router-dom'
import Breadcrumbs from '@/components/storefront/Breadcrumbs/Breadcrumbs'
import Button from '@/components/ui/Button'
import storePacking from '@/assets/about/store-packing.png'
import { ROUTES } from '@/constants/routes'

export default function AboutPage() {
  return (
    <div className="simple-page container">
      <Breadcrumbs
        items={[
          { label: 'Trang chủ', to: ROUTES.HOME },
          { label: 'Về Hòa Bình' },
        ]}
      />
      <div className="simple-page__grid">
        <img src={storePacking} alt="Minh họa đóng khay hải sản Hòa Bình" />
        <div>
          <h1>Về Hòa Bình</h1>
          <p>
            Hòa Bình là cửa hàng hải sản Hải Phòng, tập trung vào tôm, cá biển, mực
            và hải sản chế biến — chăm chút từ sơ chế đến đóng khay rõ khối lượng.
          </p>
          <p>
            Nội dung giới thiệu chi tiết (địa chỉ, giờ mở cửa, cam kết…) sẽ cập nhật
            khi cửa hàng cung cấp thông tin chính thức.
          </p>
          <Link to={ROUTES.PRODUCTS}>
            <Button variant="secondary">Xem sản phẩm →</Button>
          </Link>
        </div>
      </div>
    </div>
  )
}

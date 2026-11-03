import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import Breadcrumbs from '@/components/storefront/Breadcrumbs/Breadcrumbs'
import ProductCard from '@/components/storefront/ProductCard/ProductCard'
import { ROUTES } from '@/constants/routes'
import { useCart } from '@/context/CartContext'
import { findProductBySlug, getRelatedProducts } from '@/data/demoProducts'
import { getDefaultVariant } from '@/utils/productFilters'
import hoaPhuong from '@/assets/decorations/hoa-phuong.png'
import songLine from '@/assets/decorations/song-line.png'
import ProductGallery from './components/ProductGallery'
import ProductBuyBox from './components/ProductBuyBox'
import ProductInfoTabs from './components/ProductInfoTabs'
import './ProductDetailPage.css'

/**
 * TRANG CHI TIẾT SẢN PHẨM — /san-pham/:slug
 *
 * Trang này chỉ "ghép" các khối lại với nhau:
 *   ┌───────────────────┬────────────────────┐
 *   │ ProductGallery    │ ProductBuyBox      │
 *   │ (ảnh)             │ (giá, quy cách,    │
 *   │                   │  nút mua,          │
 *   │                   │  BulkOrderForm)    │
 *   ├───────────────────┴────────────────────┤
 *   │ ProductInfoTabs (thông tin / bảo quản) │
 *   ├────────────────────────────────────────┤
 *   │ Sản phẩm liên quan (ProductCard)       │
 *   └────────────────────────────────────────┘
 * Muốn sửa khối nào thì mở file tương ứng trong thư mục ./components
 */
export default function ProductDetailPage() {
  const { slug } = useParams() // lấy "tom-su-so-che" từ URL
  const product = findProductBySlug(slug)

  if (!product) {
    return (
      <div className="container product-missing">
        <h1>Không tìm thấy sản phẩm</h1>
        <Link to={ROUTES.PRODUCTS}>← Quay lại danh sách</Link>
      </div>
    )
  }

  // key={product.id}: khi chuyển sang sản phẩm khác, React tạo lại trang từ đầu
  // => quy cách, số lượng... tự reset về mặc định
  return <ProductDetailContent key={product.id} product={product} />
}

function ProductDetailContent({ product }) {
  const navigate = useNavigate()
  const { addToCart } = useCart()

  // Quy cách đang chọn — đặt ở đây vì cả BuyBox lẫn InfoTabs đều cần
  const [variantId, setVariantId] = useState(product.defaultVariantId)
  const variant = product.variants.find((v) => v.id === variantId) || getDefaultVariant(product)

  const related = getRelatedProducts(product, 3)

  function handleAddToCart(quantity) {
    addToCart({ product, variant, quantity })
  }

  function handleBuyNow(quantity) {
    addToCart({ product, variant, quantity })
    navigate(ROUTES.CART)
  }

  return (
    <div className="product-detail">
      {/* Họa tiết trang trí */}
      <img className="decoration product-detail__flowers" src={hoaPhuong} alt="" aria-hidden="true" />
      <img className="decoration product-detail__wave-top" src={songLine} alt="" aria-hidden="true" />
      <img className="decoration product-detail__wave-mid" src={songLine} alt="" aria-hidden="true" />

      <div className="container product-detail__inner">
        <Breadcrumbs
          items={[
            { label: 'Trang chủ', to: ROUTES.HOME },
            { label: 'Sản phẩm', to: ROUTES.PRODUCTS },
            { label: product.categoryName, to: `${ROUTES.PRODUCTS}?category=${product.categoryId}` },
            { label: product.name },
          ]}
        />

        {/* Ảnh | Mua hàng */}
        <div className="product-detail__top">
          <ProductGallery images={product.galleryImages} name={product.name} />

          <ProductBuyBox
            product={product}
            variant={variant}
            onVariantChange={setVariantId}
            onAddToCart={handleAddToCart}
            onBuyNow={handleBuyNow}
          />
        </div>

        <ProductInfoTabs product={product} variant={variant} />

        {/* Sản phẩm liên quan */}
        <section className="product-related">
          <h2>Sản phẩm liên quan</h2>
          <img className="product-related__wave" src={songLine} alt="" aria-hidden="true" />

          <div className="product-related__grid">
            {related.map((item) => (
              <ProductCard
                key={item.id}
                product={item}
                variant={getDefaultVariant(item)}
                cartVariant="full"
                imageRatio="wide"
              />
            ))}
          </div>
        </section>
      </div>
    </div>
  )
}

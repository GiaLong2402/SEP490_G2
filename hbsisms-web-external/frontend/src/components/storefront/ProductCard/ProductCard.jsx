import { Link } from 'react-router-dom'
import StockBadge from '@/components/storefront/StockBadge/StockBadge'
import CartIconButton from '@/components/storefront/CartIconButton/CartIconButton'
import { productDetailPath } from '@/constants/routes'
import { formatPrice } from '@/utils/formatPrice'
import { useCart } from '@/context/CartContext'
import './ProductCard.css'

/**
 * Card sản phẩm dùng chung.
 *
 * props:
 * - product: object sản phẩm đầy đủ (có variants) HOẶC listing item
 * - variant: biến thể đang hiển thị (nếu không truyền sẽ lấy default)
 * - cartVariant: 'icon' | 'full'
 */
export default function ProductCard({
  product,
  variant,
  cartVariant = 'icon',
  showWeight = true,
  /**
   * Tỉ lệ khung ảnh:
   * - 'standard'  (5:4)   trang Sản phẩm
   * - 'landscape' (1.55:1) trang chủ - Sản phẩm nổi bật
   * - 'wide'      (2.4:1)  trang chi tiết - Sản phẩm liên quan
   */
  imageRatio = 'standard',
}) {
  const { addToCart } = useCart()

  // Hỗ trợ cả product đầy đủ và listing item
  const displayVariant = variant || product.variant || product.variants?.[0]
  const slug = product.slug
  const name = product.name
  const image = product.primaryImageUrl
  const inStock =
    product.inStock !== false &&
    displayVariant?.inStock !== false &&
    (displayVariant?.stock == null || displayVariant.stock > 0)

  function handleAdd(e) {
    e.preventDefault()
    e.stopPropagation()
    if (!inStock || !displayVariant) return

    // Nếu chỉ có listing item, dựng product tối thiểu cho cart
    const cartProduct = product.variants
      ? product
      : {
          id: product.productId || product.id,
          slug,
          name,
          primaryImageUrl: image,
        }

    addToCart({ product: cartProduct, variant: displayVariant, quantity: 1 })
  }

  const cartLabel = `Thêm ${name}${displayVariant ? ` ${displayVariant.label}` : ''} vào giỏ`

  return (
    <article className={`product-card product-card--${imageRatio}`}>
      <Link to={productDetailPath(slug)} className="product-card__media">
        <img src={image} alt={name} loading="lazy" width={400} height={400} />
        <StockBadge inStock={inStock} solid className="product-card__badge" />
      </Link>

      <div className="product-card__body">
        <Link to={productDetailPath(slug)} className="product-card__name">
          {name}
        </Link>

        {showWeight && displayVariant && (
          <p className="product-card__meta">{displayVariant.label}</p>
        )}

        <div
          className={`product-card__footer ${
            cartVariant === 'full' ? 'product-card__footer--stack' : ''
          }`}
        >
          <p className="product-card__price">
            {formatPrice(displayVariant?.price)}
            <span> / khay</span>
          </p>

          <CartIconButton
            variant={cartVariant}
            label={cartLabel}
            disabled={!inStock}
            onClick={handleAdd}
          />
        </div>
      </div>
    </article>
  )
}

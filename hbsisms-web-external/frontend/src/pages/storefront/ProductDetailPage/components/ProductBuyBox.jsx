import { useState } from 'react'
import StockBadge from '@/components/storefront/StockBadge/StockBadge'
import {
  ArrowRightIcon,
  CartIcon,
  CheckIcon,
  HeadsetIcon,
  MinusIcon,
  PackageIcon,
  PlusIcon,
  TruckIcon,
} from '@/components/icons/Icons'
import { formatPrice, formatTrayLabel, formatWeight } from '@/utils/formatPrice'
import BulkOrderForm from './BulkOrderForm'
import './ProductBuyBox.css'

// 3 cam kết ở cuối khung mua hàng
const BENEFITS = [
  { icon: PackageIcon, text: 'Đóng khay rõ khối lượng' },
  { icon: TruckIcon, text: 'Nhận tại cửa hàng hoặc giao tận nơi' },
  { icon: HeadsetIcon, text: 'Liên hệ cửa hàng khi cần hỗ trợ' },
]

/**
 * Khung mua hàng bên phải trang chi tiết.
 *
 * props:
 * - product:         sản phẩm đang xem
 * - variant:         quy cách đang chọn (Khay 500 g / Khay 1 kg)
 * - onVariantChange: gọi khi bấm chọn quy cách khác, truyền vào id quy cách
 * - onAddToCart:     bấm "Thêm vào giỏ", truyền vào số lượng khay
 * - onBuyNow:        bấm "Mua ngay", truyền vào số lượng khay
 */
export default function ProductBuyBox({ product, variant, onVariantChange, onAddToCart, onBuyNow }) {
  const [quantity, setQuantity] = useState(1)

  // Không cho số lượng nhỏ hơn 1 hoặc lớn hơn tồn kho
  const maxQuantity = Math.max(1, variant.stock || 1)

  function changeQuantity(newValue) {
    const safeValue = Math.min(maxQuantity, Math.max(1, newValue))
    setQuantity(safeValue)
  }

  function chooseVariant(variantId) {
    onVariantChange(variantId)
    setQuantity(1) // đổi quy cách thì đặt lại số lượng
  }

  const totalWeight = variant.weightGrams * quantity
  const subtotal = variant.price * quantity
  const eyebrow = product.categoryId === 'che-bien' ? 'HẢI SẢN CHẾ BIẾN' : 'HẢI SẢN SƠ CHẾ'

  return (
    <div className="buy-box">
      {/* ----- Tên, trạng thái, giá ----- */}
      <p className="buy-box__eyebrow">{eyebrow}</p>
      <h1 className="buy-box__name">{product.name}</h1>

      <div className="buy-box__status">
        <StockBadge inStock={product.inStock} solid />
        <span className="buy-box__divider" aria-hidden="true" />
        <span>Danh mục: {product.categoryName}</span>
      </div>

      <p className="buy-box__price">
        {formatPrice(variant.price)}
        <span> / khay</span>
      </p>

      <p className="buy-box__desc">{product.description}</p>

      {/* ----- Chọn quy cách ----- */}
      <p className="buy-box__label">Quy cách đóng gói</p>
      <div className="buy-box__variants" role="radiogroup" aria-label="Quy cách đóng gói">
        {product.variants.map((v) => {
          const isActive = v.id === variant.id
          return (
            <button
              key={v.id}
              type="button"
              role="radio"
              aria-checked={isActive}
              className={`buy-box__variant ${isActive ? 'is-active' : ''}`}
              onClick={() => chooseVariant(v.id)}
            >
              {isActive && (
                <span className="buy-box__variant-check">
                  <CheckIcon size={14} />
                </span>
              )}
              {formatTrayLabel(v.weightGrams)}
            </button>
          )
        })}
      </div>

      {/* ----- Số lượng ----- */}
      <p className="buy-box__label">Số lượng khay</p>
      <div className="buy-box__qty-row">
        <div className="buy-box__qty">
          <button type="button" aria-label="Giảm" onClick={() => changeQuantity(quantity - 1)}>
            <MinusIcon />
          </button>
          <input
            type="number"
            min={1}
            max={maxQuantity}
            value={quantity}
            aria-label="Số lượng khay"
            onChange={(e) => changeQuantity(Number(e.target.value) || 1)}
          />
          <button type="button" aria-label="Tăng" onClick={() => changeQuantity(quantity + 1)}>
            <PlusIcon />
          </button>
        </div>
        <p className="buy-box__weight">
          Tổng khối lượng: <strong>{formatWeight(totalWeight)}</strong>
        </p>
      </div>

      <p className="buy-box__subtotal">
        Tạm tính: <strong>{formatPrice(subtotal)}</strong>
      </p>

      {/* ----- Nút mua ----- */}
      <div className="buy-box__actions">
        <button
          type="button"
          className="buy-box__btn is-outline"
          disabled={!product.inStock}
          onClick={() => onAddToCart(quantity)}
        >
          <CartIcon size={24} /> Thêm vào giỏ
        </button>
        <button
          type="button"
          className="buy-box__btn is-solid"
          disabled={!product.inStock}
          onClick={() => onBuyNow(quantity)}
        >
          Mua ngay <ArrowRightIcon size={22} />
        </button>
      </div>

      {/* ----- Đặt số lượng lớn (component riêng) ----- */}
      <BulkOrderForm productName={product.name} variantLabel={formatTrayLabel(variant.weightGrams)} />

      {/* ----- 3 cam kết ----- */}
      <ul className="buy-box__benefits">
        {BENEFITS.map((item) => {
          const Icon = item.icon
          return (
            <li key={item.text}>
              <Icon size={30} />
              <span>{item.text}</span>
            </li>
          )
        })}
      </ul>
    </div>
  )
}

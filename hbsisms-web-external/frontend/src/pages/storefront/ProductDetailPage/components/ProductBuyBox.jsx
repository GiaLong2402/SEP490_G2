import { useEffect, useState } from 'react'
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
import { formatPrice, formatTrayLabel } from '@/utils/formatPrice'
import BulkOrderForm from './BulkOrderForm'
import './ProductBuyBox.css'

/** Soft-cap nhập tay trên detail; không gắn với tồn (cho phép vượt để gửi yêu cầu khi checkout). */
const SOFT_MAX_QTY = 999

const BENEFITS = [
  { icon: PackageIcon, text: 'Đóng khay rõ khối lượng' },
  { icon: TruckIcon, text: 'Nhận tại cửa hàng hoặc giao tận nơi' },
  { icon: HeadsetIcon, text: 'Liên hệ cửa hàng khi cần hỗ trợ' },
]

/**
 * Khung mua hàng bên phải trang chi tiết.
 */
export default function ProductBuyBox({ product, variant, onVariantChange, onAddToCart, onBuyNow }) {
  const [quantity, setQuantity] = useState(1)

  const availableTrays = Math.max(0, Number(variant?.stock) || 0)
  const variantInStock = availableTrays > 0
  const maxQuantity = variantInStock ? SOFT_MAX_QTY : 0
  const tiers = Array.isArray(variant?.tiers) ? variant.tiers : []

  useEffect(() => {
    setQuantity(1)
  }, [variant?.id])

  function changeQuantity(newValue) {
    if (!variantInStock) {
      setQuantity(1)
      return
    }
    setQuantity(Math.min(maxQuantity, Math.max(1, newValue)))
  }

  function chooseVariant(nextId) {
    onVariantChange(nextId)
  }

  const subtotal = (variant?.price || 0) * quantity
  const eyebrow = product.categoryId === 'che-bien' ? 'HẢI SẢN CHẾ BIẾN' : 'HẢI SẢN SƠ CHẾ'

  return (
    <div className="buy-box">
      <p className="buy-box__eyebrow">{eyebrow}</p>
      <h1 className="buy-box__name">{product.name}</h1>

      <div className="buy-box__status">
        <StockBadge inStock={variantInStock} solid />
        <span className="buy-box__divider" aria-hidden="true" />
        <span>Danh mục: {product.categoryName}</span>
      </div>

      <p className="buy-box__price">
        {formatPrice(variant.price)}
        <span> / khay</span>
      </p>

      {tiers.length > 0 ? (
        <ul className="buy-box__tiers" aria-label="Giá theo số lượng">
          {tiers.map((tier) => (
            <li key={`${tier.minQtyKhay}-${tier.unitPrice}`}>
              Từ {tier.minQtyKhay} khay: <strong>{formatPrice(tier.unitPrice)}</strong>
              <span> / khay</span>
            </li>
          ))}
        </ul>
      ) : null}

      <p className="buy-box__desc">{product.description}</p>

      <p className="buy-box__label">Quy cách đóng gói</p>
      <div className="buy-box__variants" role="radiogroup" aria-label="Quy cách đóng gói">
        {product.variants.map((v) => {
          const isActive = v.id === variant.id
          const vInStock = (v.stock || 0) > 0
          return (
            <button
              key={v.id}
              type="button"
              role="radio"
              aria-checked={isActive}
              className={`buy-box__variant ${isActive ? 'is-active' : ''} ${!vInStock ? 'is-oos' : ''}`}
              onClick={() => chooseVariant(v.id)}
            >
              {isActive && (
                <span className="buy-box__variant-check">
                  <CheckIcon size={14} />
                </span>
              )}
              {formatTrayLabel(v.weightGrams)}
              {!vInStock ? <span className="buy-box__variant-oos">Hết</span> : null}
            </button>
          )
        })}
      </div>

      <p className="buy-box__label">Số lượng khay</p>
      <div className="buy-box__qty-row">
        <div className="buy-box__qty">
          <button
            type="button"
            aria-label="Giảm"
            disabled={!variantInStock}
            onClick={() => changeQuantity(quantity - 1)}
          >
            <MinusIcon />
          </button>
          <input
            type="number"
            min={1}
            max={maxQuantity || 1}
            value={quantity}
            disabled={!variantInStock}
            aria-label="Số lượng khay"
            onChange={(e) => changeQuantity(Number(e.target.value) || 1)}
          />
          <button
            type="button"
            aria-label="Tăng"
            disabled={!variantInStock}
            onClick={() => changeQuantity(quantity + 1)}
          >
            <PlusIcon />
          </button>
        </div>
        {variantInStock ? (
          <p className="buy-box__stock-hint" aria-live="polite">
            {availableTrays.toLocaleString('vi-VN')} khay có sẵn
          </p>
        ) : null}
      </div>

      <p className="buy-box__subtotal">
        Tạm tính: <strong>{formatPrice(subtotal)}</strong>
      </p>

      <div className="buy-box__actions">
        <button
          type="button"
          className="buy-box__btn is-outline"
          disabled={!variantInStock}
          onClick={() => onAddToCart(quantity)}
        >
          <CartIcon size={24} /> Thêm vào giỏ
        </button>
        <button
          type="button"
          className="buy-box__btn is-solid"
          disabled={!variantInStock}
          onClick={() => onBuyNow(quantity)}
        >
          Mua ngay <ArrowRightIcon size={22} />
        </button>
      </div>

      <BulkOrderForm productName={product.name} variantLabel={formatTrayLabel(variant.weightGrams)} />

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

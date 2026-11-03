import { CartIcon } from '@/components/icons/Icons'
import './CartIconButton.css'

/**
 * Nút giỏ cam — dùng trên ProductCard.
 * variant: 'icon' (homepage/listing) | 'full' (related products)
 */
export default function CartIconButton({
  label,
  variant = 'icon',
  disabled = false,
  onClick,
}) {
  if (variant === 'full') {
    return (
      <button
        type="button"
        className="cart-btn cart-btn--full"
        aria-label={label}
        disabled={disabled}
        onClick={onClick}
      >
        <CartIcon size={18} />
        <span>Thêm vào giỏ</span>
      </button>
    )
  }

  return (
    <button
      type="button"
      className="cart-btn cart-btn--icon"
      aria-label={label}
      disabled={disabled}
      onClick={onClick}
    >
      <CartIcon size={24} />
    </button>
  )
}

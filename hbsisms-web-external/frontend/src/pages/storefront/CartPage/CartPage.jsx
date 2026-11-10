import { Link, useNavigate } from 'react-router-dom'
import PageBanner from '@/components/storefront/PageBanner/PageBanner'
import {
  ArrowLeftIcon,
  ArrowRightIcon,
  MinusIcon,
  PlusIcon,
  TrashIcon,
} from '@/components/icons/Icons'
import { ROUTES, productDetailPath } from '@/constants/routes'
import { useCart } from '@/context/CartContext'
import { formatPrice, formatTrayLabel } from '@/utils/formatPrice'
import './CartPage.css'

/**
 * Rút gọn tên cho khung tóm tắt: "Tôm sú sơ chế" -> "Tôm sú"
 * (lấy 2 chữ đầu)
 */
function shortProductName(name) {
  const words = name.trim().split(/\s+/)
  return words.slice(0, 2).join(' ')
}

/**
 * TRANG GIỎ HÀNG — /gio-hang
 *
 *   ┌──────────────────────────────┬─────────────────┐
 *   │ Bảng sản phẩm (cart-box)     │ Tóm tắt         │
 *   │  - mỗi dòng: ảnh, tên, giá,  │ (cart-summary)  │
 *   │    số lượng, thành tiền, xóa │  - danh sách    │
 *   │  - chân: tiếp tục / xóa hết  │  - tổng tiền    │
 *   │                              │  - nút Thanh toán│
 *   └──────────────────────────────┴─────────────────┘
 */
export default function CartPage() {
  // Lấy dữ liệu + hàm xử lý giỏ hàng từ CartContext (dùng chung toàn app)
  const { items, totalItems, totalAmount, updateQuantity, removeItem, clearCart } = useCart()
  const navigate = useNavigate()

  function handleCheckout() {
    navigate(ROUTES.CHECKOUT)
  }

  return (
    <div className="cart-page">
      <PageBanner
        title="Giỏ hàng của bạn"
        breadcrumbs={[{ label: 'Trang chủ', to: ROUTES.HOME }, { label: 'Giỏ hàng' }]}
      />

      <div className="container cart-page__body">
        {/* ----- Giỏ trống ----- */}
        {items.length === 0 && (
          <div className="cart-empty">
            <p>Giỏ hàng đang trống.</p>
            <Link to={ROUTES.PRODUCTS} className="btn btn--secondary">
              Xem sản phẩm
            </Link>
          </div>
        )}

        {/* ----- Có sản phẩm ----- */}
        {items.length > 0 && (
          <div className="cart-layout">
            {/* ===== TRÁI: bảng sản phẩm ===== */}
            <div>
              <p className="cart-count">
                {items.length} sản phẩm • {totalItems} khay
              </p>

              <section className="cart-box" aria-label="Danh sách sản phẩm trong giỏ">
                {/* Hàng tiêu đề (ẩn trên mobile) */}
                <div className="cart-box__head" aria-hidden="true">
                  <span>Sản phẩm</span>
                  <span className="is-center">Đơn giá</span>
                  <span className="is-center">Số lượng</span>
                  <span className="is-center">Thành tiền</span>
                  <span />
                </div>

                <ul>
                  {items.map((item) => (
                    <li key={item.key} className="cart-row">
                      {/* Cột 1: ảnh + tên + quy cách */}
                      <div className="cart-product">
                        <Link to={productDetailPath(item.slug)} className="cart-product__img">
                          <img src={item.image} alt="" />
                        </Link>
                        <div>
                          <Link to={productDetailPath(item.slug)} className="cart-product__name">
                            {item.name}
                          </Link>
                          <p className="cart-product__variant">
                            {formatTrayLabel(item.variant.weightGrams)}
                          </p>
                        </div>
                      </div>

                      {/* Cột 2: đơn giá ("cart-label" chỉ hiện trên mobile) */}
                      <p className="cart-row__unit">
                        <span className="cart-label">Đơn giá</span>
                        {formatPrice(item.variant.price)}
                      </p>

                      {/* Cột 3: tăng / giảm số lượng */}
                      <div className="cart-row__qty">
                        <div className="cart-stepper">
                          <button
                            type="button"
                            aria-label="Giảm số lượng"
                            onClick={() => updateQuantity(item.key, item.quantity - 1)}
                          >
                            <MinusIcon size={18} />
                          </button>
                          <span>{item.quantity}</span>
                          <button
                            type="button"
                            aria-label="Tăng số lượng"
                            onClick={() => updateQuantity(item.key, item.quantity + 1)}
                          >
                            <PlusIcon size={18} />
                          </button>
                        </div>
                      </div>

                      {/* Cột 4: thành tiền = đơn giá x số lượng */}
                      <p className="cart-row__total">
                        <span className="cart-label">Thành tiền</span>
                        {formatPrice(item.variant.price * item.quantity)}
                      </p>

                      {/* Cột 5: nút xóa */}
                      <button
                        type="button"
                        className="cart-row__remove"
                        aria-label={`Xóa ${item.name}`}
                        onClick={() => removeItem(item.key)}
                      >
                        <TrashIcon size={22} />
                      </button>
                    </li>
                  ))}
                </ul>

                {/* Chân bảng */}
                <div className="cart-box__foot">
                  <Link to={ROUTES.PRODUCTS} className="cart-continue">
                    <ArrowLeftIcon size={22} />
                    Tiếp tục mua sắm
                  </Link>
                  <button type="button" className="cart-clear" onClick={clearCart}>
                    <TrashIcon size={18} />
                    Xóa giỏ hàng
                  </button>
                </div>
              </section>
            </div>

            {/* ===== PHẢI: tóm tắt giỏ hàng ===== */}
            <aside className="cart-summary" aria-labelledby="cart-summary-title">
              <h2 id="cart-summary-title">Tóm tắt giỏ hàng</h2>

              {/* Mỗi sản phẩm là 1 thẻ trắng nhỏ */}
              <ul className="cart-summary__list">
                {items.map((item) => (
                  <li key={item.key} className="cart-summary__item">
                    <img src={item.image} alt="" />
                    <p>
                      <strong>{shortProductName(item.name)}</strong>
                      <span> · {item.quantity} khay</span>
                    </p>
                    <span className="cart-summary__item-price">
                      {formatPrice(item.variant.price * item.quantity)}
                    </span>
                  </li>
                ))}
              </ul>

              <div className="cart-summary__rows">
                <p>
                  <span>Tạm tính ({totalItems} khay)</span>
                  <strong>{formatPrice(totalAmount)}</strong>
                </p>
                <p>
                  <span>Phí giao hàng</span>
                  <span>Tính ở bước sau</span>
                </p>
              </div>

              <div className="cart-summary__total">
                <span>Tổng tạm tính</span>
                <strong>{formatPrice(totalAmount)}</strong>
              </div>
              <p className="cart-summary__hint">Chưa bao gồm phí giao hàng.</p>

              <button type="button" className="cart-summary__pay" onClick={handleCheckout}>
                Thanh toán
                <ArrowRightIcon size={22} />
              </button>

            </aside>
          </div>
        )}
      </div>
    </div>
  )
}

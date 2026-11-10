import { Fragment, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import PageBanner from '@/components/storefront/PageBanner/PageBanner'
import AddressFormDialog from '@/components/storefront/AddressFormDialog/AddressFormDialog'
import LoadState from '@/components/ui/LoadState'
import {
  ArrowLeftIcon,
  ArrowRightIcon,
  CalendarIcon,
  CheckIcon,
  ClipboardIcon,
  ClockIcon,
  MapPinIcon,
  PencilIcon,
  PlusIcon,
  QrIcon,
  ShieldIcon,
  StoreIcon,
  TruckIcon,
} from '@/components/icons/Icons'
import { addressesApi } from '@/api/addresses'
import { checkoutApi, newIdempotencyKey } from '@/api/checkout'
import { ROUTES } from '@/constants/routes'
import { useAuth } from '@/context/AuthContext'
import { useCart } from '@/context/CartContext'
import { useAsync } from '@/hooks/useAsync'
import { formatPrice, formatTrayLabel } from '@/utils/formatPrice'
import { formatFullDate, sameDay, toIsoDate } from '@/utils/date'
import DatePicker from './DatePicker'
import PaymentStep from './PaymentStep'
import StockShortageDialog from './StockShortageDialog'
import './CheckoutPage.css'

// Phí giao ước tính để hiển thị — backend tính lại khi tạo đơn (theo khoảng cách)
const SHIPPING_FEE = 20000
// TODO: thay bằng địa chỉ thật của cửa hàng
const STORE_ADDRESS = 'Cửa hàng Hải sản Hòa Bình, Hải Phòng'

const STEPS = ['Nhận hàng', 'Ngày giờ nhận', 'Xác nhận', 'Thanh toán']

/** value gửi API (timeWindow) + label hiển thị */
const SLOTS = [
  { id: '08:00-10:00', label: '08:00 – 10:00', start: 8 },
  { id: '10:00-12:00', label: '10:00 – 12:00', start: 10 },
  { id: '14:00-16:00', label: '14:00 – 16:00', start: 14 },
  { id: '16:00-18:00', label: '16:00 – 18:00', start: 16 },
]

export default function CheckoutPage() {
  const navigate = useNavigate()
  const { user, isLoggedIn } = useAuth()
  const { items, totalItems, totalAmount, clearCart, updateQuantity, removeItem } = useCart()

  const [now] = useState(() => new Date())
  const [today] = useState(() => new Date(now.getFullYear(), now.getMonth(), now.getDate()))
  const [step, setStep] = useState(0)
  const [method, setMethod] = useState('delivery')
  const [selectedAddressId, setSelectedAddressId] = useState(null)
  const [addrDialog, setAddrDialog] = useState(null) // null | { address? }
  const [date, setDate] = useState(today)
  const [slotId, setSlotId] = useState(null)
  const [note, setNote] = useState('')

  const [placing, setPlacing] = useState(false)
  const [placeError, setPlaceError] = useState('')
  const [cartNotice, setCartNotice] = useState('')
  const [shortages, setShortages] = useState(null)
  const [placed, setPlaced] = useState(null) // { order, payment } sau POST /checkout
  // 1 key cho 1 lần đặt: bấm lại / mạng chập chờn → backend vẫn chỉ tạo 1 đơn
  const idemKey = useRef(null)

  const addressesQuery = useAsync(
    (signal) => (isLoggedIn ? addressesApi.list(signal) : Promise.resolve([])),
    [isLoggedIn],
  )
  const addresses = addressesQuery.data || []
  const selectedAddress =
    addresses.find((a) => a.id === selectedAddressId) || addresses.find((a) => a.isDefault) || addresses[0] || null

  const isDelivery = method === 'delivery'
  const shipping = isDelivery ? SHIPPING_FEE : 0
  const total = totalAmount + shipping

  // Hôm nay: cửa hàng cần ~2 tiếng chuẩn bị → khóa khung giờ quá sát
  const isSlotDisabled = (s) => sameDay(date, today) && s.start <= now.getHours() + 1
  const slot = SLOTS.find((s) => s.id === slotId && !isSlotDisabled(s))

  const stepReady = [!isDelivery || Boolean(selectedAddress), Boolean(slot), !placing]
  const blockHint = ['Thêm địa chỉ nhận hàng để tiếp tục.', 'Chọn khung giờ nhận hàng để tiếp tục.', ''][step]

  function goTo(next) {
    setStep(next)
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  async function placeOrder() {
    setPlacing(true)
    setPlaceError('')
    if (!idemKey.current) idemKey.current = newIdempotencyKey()
    try {
      const result = await checkoutApi.create(
        items,
        {
          fulfillmentMethod: isDelivery ? 'SHOP_DELIVERY' : 'PICKUP',
          shipAddressId: isDelivery ? selectedAddress.id : null,
          requestedDate: toIsoDate(date),
          timeWindow: slot.id,
          note: note.trim() || null,
        },
        idemKey.current,
      )
      idemKey.current = null
      setPlaced(result)
      clearCart() // đơn đã tạo — giỏ trống (giống backend: checkout làm trống giỏ)
      goTo(3)
    } catch (err) {
      if (err.status === 409 && err.payload?.shortages?.length) {
        idemKey.current = null // giỏ sẽ đổi → lần đặt sau là yêu cầu mới
        setShortages(err.payload.shortages)
      } else {
        setPlaceError(err.message || 'Không đặt được đơn. Vui lòng thử lại.')
      }
    } finally {
      setPlacing(false)
    }
  }

  function handlePrimary() {
    if (!stepReady[step]) return
    if (step < 2) goTo(step + 1)
    else placeOrder()
  }

  function reduceToAvailable(rows) {
    rows.forEach(({ item, availableQty }) => {
      if (availableQty > 0) updateQuantity(item.key, availableQty)
      else removeItem(item.key)
    })
    setShortages(null)
    setCartNotice('Đã cập nhật giỏ hàng theo số còn hàng. Kiểm tra lại rồi bấm Đặt hàng.')
  }

  const banner = (
    <PageBanner
      title="Thanh toán"
      breadcrumbs={[
        { label: 'Trang chủ', to: ROUTES.HOME },
        { label: 'Giỏ hàng', to: ROUTES.CART },
        { label: 'Thanh toán' },
      ]}
    />
  )

  /* Thanh bước (đã sang thanh toán thì không quay lại sửa được) */
  const stepper = (
    <ol className="checkout-steps">
      {STEPS.map((label, i) => {
        const state = i < step ? 'is-done' : i === step ? 'is-current' : ''
        return (
          <Fragment key={label}>
            {i > 0 && <li className={`checkout-steps__line ${i <= step ? 'is-done' : ''}`} aria-hidden="true" />}
            <li className={`checkout-step ${state}`}>
              <button
                type="button"
                disabled={i >= step || step === 3}
                onClick={() => goTo(i)}
                aria-current={i === step ? 'step' : undefined}
              >
                <span className="checkout-step__dot">{i < step ? <CheckIcon size={16} /> : i + 1}</span>
                <span className="checkout-step__label">{label}</span>
              </button>
            </li>
          </Fragment>
        )
      })}
    </ol>
  )

  /* ----- Bước 4: thanh toán PayOS (đơn đã tạo) ----- */
  if (placed) {
    return (
      <div className="checkout-page">
        {banner}
        <div className="container checkout-page__body">
          {stepper}
          <PaymentStep order={placed.order} payment={placed.payment} />
        </div>
      </div>
    )
  }

  /* ----- Giỏ trống / chưa đăng nhập ----- */
  if (items.length === 0 || !isLoggedIn) {
    return (
      <div className="checkout-page">
        {banner}
        <div className="container checkout-page__body">
          <div className="checkout-done">
            {items.length === 0 ? (
              <>
                <h2>Giỏ hàng đang trống</h2>
                <p>{cartNotice ? 'Các sản phẩm trong giỏ đã hết hàng.' : 'Hãy chọn sản phẩm trước khi thanh toán.'}</p>
                <div className="checkout-done__actions">
                  <Link to={ROUTES.PRODUCTS} className="btn btn--primary btn--pill">
                    Xem sản phẩm
                  </Link>
                </div>
              </>
            ) : (
              <>
                <h2>Đăng nhập để thanh toán</h2>
                <p>Bạn cần đăng nhập để cửa hàng xác nhận đơn và giao hàng đúng địa chỉ.</p>
                <div className="checkout-done__actions">
                  <button
                    type="button"
                    className="btn btn--primary btn--pill"
                    onClick={() => navigate(ROUTES.LOGIN, { state: { from: ROUTES.CHECKOUT } })}
                  >
                    Đăng nhập
                  </button>
                  <Link to={ROUTES.CART} className="btn btn--ghost btn--pill">
                    Quay lại giỏ hàng
                  </Link>
                </div>
              </>
            )}
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="checkout-page">
      {banner}

      <div className="container checkout-page__body cart-layout">
        <div className="checkout-main">
          {stepper}

          {/* ===== Bước 1: cách nhận + địa chỉ ===== */}
          {step === 0 && (
            <section className="checkout-card">
              <h2 className="checkout-card__title">
                <TruckIcon size={22} /> Cách nhận hàng
              </h2>
              <div className="checkout-options" role="radiogroup" aria-label="Cách nhận hàng">
                {[
                  { id: 'delivery', title: 'Giao tận nơi', desc: `Phí giao từ ${formatPrice(SHIPPING_FEE)}`, Icon: TruckIcon },
                  { id: 'pickup', title: 'Nhận tại cửa hàng', desc: 'Miễn phí · Đến lấy tại Hòa Bình', Icon: StoreIcon },
                ].map(({ id, title, desc, Icon }) => (
                  <button
                    key={id}
                    type="button"
                    role="radio"
                    aria-checked={method === id}
                    className={`checkout-option ${method === id ? 'is-active' : ''}`}
                    onClick={() => setMethod(id)}
                  >
                    <span className="checkout-option__icon">
                      <Icon size={24} />
                    </span>
                    <span className="checkout-option__text">
                      <strong>{title}</strong>
                      <span>{desc}</span>
                    </span>
                    <span className="checkout-radio" aria-hidden="true" />
                  </button>
                ))}
              </div>

              <div className="checkout-card__divider" />

              {isDelivery ? (
                <>
                  <div className="checkout-card__row">
                    <h2 className="checkout-card__title">
                      <MapPinIcon size={22} /> Địa chỉ nhận hàng
                    </h2>
                    {!addressesQuery.loading && !addressesQuery.error && (
                      <button type="button" className="checkout-link" onClick={() => setAddrDialog({})}>
                        <PlusIcon size={16} /> Thêm địa chỉ
                      </button>
                    )}
                  </div>

                  <LoadState
                    loading={addressesQuery.loading}
                    error={addressesQuery.error}
                    onRetry={addressesQuery.reload}
                    empty={addresses.length === 0}
                    emptyText="Bạn chưa có địa chỉ nhận hàng."
                    emptyAction={
                      <button type="button" className="btn btn--primary btn--pill" onClick={() => setAddrDialog({})}>
                        <PlusIcon size={18} /> Thêm địa chỉ
                      </button>
                    }
                  >
                    <div className="checkout-addresses" role="radiogroup" aria-label="Địa chỉ nhận hàng">
                      {addresses.map((a) => {
                        const active = selectedAddress?.id === a.id
                        return (
                          <div key={a.id} className={`checkout-address ${active ? 'is-active' : ''}`}>
                            <button
                              type="button"
                              role="radio"
                              aria-checked={active}
                              className="checkout-address__pick"
                              onClick={() => setSelectedAddressId(a.id)}
                            >
                              <span className={`checkout-radio ${active ? 'is-checked' : ''}`} aria-hidden="true" />
                              <span className="checkout-address__text">
                                {a.isDefault && (
                                  <span className="checkout-address__tag">
                                    <CheckIcon size={13} /> Mặc định
                                  </span>
                                )}
                                <strong>
                                  {a.recipientName} · {a.phone || 'Chưa có SĐT'}
                                </strong>
                                <span>{a.fullText}</span>
                              </span>
                            </button>
                            <button type="button" className="checkout-link" onClick={() => setAddrDialog({ address: a })}>
                              <PencilIcon size={16} /> Sửa
                            </button>
                          </div>
                        )
                      })}
                    </div>
                  </LoadState>
                </>
              ) : (
                <>
                  <h2 className="checkout-card__title">
                    <StoreIcon size={22} /> Nơi nhận hàng
                  </h2>
                  <div className="checkout-address is-active">
                    <span className="checkout-address__text">
                      <strong>{STORE_ADDRESS}</strong>
                      <span>Cửa hàng sẽ báo khi hàng sẵn sàng để bạn đến lấy.</span>
                    </span>
                  </div>
                </>
              )}
            </section>
          )}

          {/* ===== Bước 2: ngày + khung giờ ===== */}
          {step === 1 && (
            <section className="checkout-card">
              <h2 className="checkout-card__title">
                <CalendarIcon size={22} /> Ngày nhận hàng
              </h2>
              <DatePicker value={date} onChange={setDate} min={today} />

              <div className="checkout-card__divider" />

              <h2 className="checkout-card__title">
                <ClockIcon size={22} /> Khung giờ nhận hàng
              </h2>
              <p className="checkout-card__sub">
                {isDelivery ? 'Cửa hàng giao trong khung giờ bạn chọn.' : 'Bạn đến lấy hàng trong khung giờ này.'}
              </p>
              <div className="checkout-slots" role="radiogroup" aria-label="Khung giờ">
                {SLOTS.map((s) => {
                  const disabled = isSlotDisabled(s)
                  const active = slot?.id === s.id
                  return (
                    <button
                      key={s.id}
                      type="button"
                      role="radio"
                      aria-checked={active}
                      disabled={disabled}
                      className={`checkout-slot ${active ? 'is-active' : ''}`}
                      onClick={() => setSlotId(s.id)}
                    >
                      {s.label}
                      {disabled && <small>Đã hết giờ nhận</small>}
                      {active && <CheckIcon size={18} />}
                    </button>
                  )
                })}
              </div>

              <label className="checkout-note">
                <span>Ghi chú cho cửa hàng (không bắt buộc)</span>
                <textarea
                  rows={2}
                  value={note}
                  onChange={(e) => setNote(e.target.value)}
                  placeholder="Ví dụ: gọi trước khi giao, để hàng ở bảo vệ…"
                />
              </label>
            </section>
          )}

          {/* ===== Bước 3: xem lại ===== */}
          {step === 2 && (
            <section className="checkout-card">
              <h2 className="checkout-card__title">
                <ClipboardIcon size={22} /> Thông tin nhận hàng
              </h2>
              <dl className="checkout-review">
                <div>
                  <dt>Cách nhận</dt>
                  <dd>{isDelivery ? 'Giao tận nơi' : 'Nhận tại cửa hàng'}</dd>
                  <button type="button" className="checkout-link" onClick={() => goTo(0)}>
                    <PencilIcon size={16} /> Sửa
                  </button>
                </div>
                <div>
                  <dt>{isDelivery ? 'Địa chỉ' : 'Nơi nhận'}</dt>
                  <dd>
                    {isDelivery && selectedAddress ? (
                      <>
                        <strong>
                          {selectedAddress.recipientName} · {selectedAddress.phone || 'Chưa có SĐT'}
                        </strong>
                        <span>{selectedAddress.fullText}</span>
                      </>
                    ) : (
                      STORE_ADDRESS
                    )}
                  </dd>
                  <button type="button" className="checkout-link" onClick={() => goTo(0)}>
                    <PencilIcon size={16} /> Sửa
                  </button>
                </div>
                <div>
                  <dt>Ngày giờ nhận</dt>
                  <dd>
                    {formatFullDate(date)} · {slot?.label}
                  </dd>
                  <button type="button" className="checkout-link" onClick={() => goTo(1)}>
                    <PencilIcon size={16} /> Sửa
                  </button>
                </div>
                {note.trim() && (
                  <div>
                    <dt>Ghi chú</dt>
                    <dd>{note.trim()}</dd>
                    <button type="button" className="checkout-link" onClick={() => goTo(1)}>
                      <PencilIcon size={16} /> Sửa
                    </button>
                  </div>
                )}
              </dl>

              <div className="checkout-card__divider" />

              <h2 className="checkout-card__title">Phương thức thanh toán</h2>
              <div className="checkout-option is-active checkout-option--static">
                <span className="checkout-option__icon">
                  <QrIcon size={24} />
                </span>
                <span className="checkout-option__text">
                  <strong>Thanh toán qua PayOS</strong>
                  <span>Quét mã QR hoặc chuyển khoản ngân hàng</span>
                </span>
                <span className="checkout-radio is-checked" aria-hidden="true" />
              </div>

              <p className="checkout-hold">
                <ClockIcon size={18} />
                Hàng được giữ cho bạn trong thời gian thanh toán.
              </p>
            </section>
          )}

          <button
            type="button"
            className="checkout-back"
            onClick={() => (step === 0 ? navigate(ROUTES.CART) : goTo(step - 1))}
          >
            <ArrowLeftIcon size={20} />
            {step === 0 ? 'Quay lại giỏ hàng' : 'Quay lại'}
          </button>
        </div>

        {/* ===== Tóm tắt đơn (cùng kiểu trang Giỏ hàng) ===== */}
        <aside className="cart-summary" aria-labelledby="checkout-summary-title">
          <h2 id="checkout-summary-title">Đơn hàng của bạn</h2>

          {cartNotice && (
            <p className="checkout-sum__notice" role="status">
              {cartNotice}
            </p>
          )}

          <ul className="cart-summary__list">
            {items.map((item) => (
              <li key={item.key} className="cart-summary__item">
                <img src={item.image} alt="" />
                <p>
                  <strong>{item.name}</strong>
                  <span className="checkout-sum__variant">
                    {formatTrayLabel(item.variant.weightGrams)} × {item.quantity}
                  </span>
                </p>
                <span className="cart-summary__item-price">{formatPrice(item.variant.price * item.quantity)}</span>
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
              <strong>{shipping ? formatPrice(shipping) : 'Miễn phí'}</strong>
            </p>
          </div>

          <div className="cart-summary__total">
            <span>Tổng thanh toán</span>
            <strong>{formatPrice(total)}</strong>
          </div>

          <button
            type="button"
            className="cart-summary__pay checkout-sum__pay"
            disabled={!stepReady[step]}
            onClick={handlePrimary}
          >
            {step < 2 ? 'Tiếp tục' : placing ? 'Đang tạo đơn…' : 'Đặt hàng & thanh toán'}
            <ArrowRightIcon size={22} />
          </button>
          {!stepReady[step] && blockHint && <p className="checkout-sum__block">{blockHint}</p>}
          {placeError && (
            <p className="checkout-sum__block" role="alert">
              {placeError}
            </p>
          )}

          <p className="checkout-sum__secure">
            <ShieldIcon size={18} />
            Thanh toán an toàn qua PayOS
          </p>
        </aside>
      </div>

      {addrDialog && (
        <AddressFormDialog
          key={addrDialog.address?.id || 'new'}
          open
          address={addrDialog.address || null}
          forceDefault={addresses.length === 0}
          defaults={{ recipientName: user?.name || '', phone: user?.phone || '' }}
          onClose={() => setAddrDialog(null)}
          onSaved={(saved) => {
            setSelectedAddressId(saved.id)
            addressesQuery.reload()
          }}
        />
      )}

      {shortages && (
        <StockShortageDialog
          shortages={shortages}
          items={items}
          neededDate={date}
          onReduce={reduceToAvailable}
          onClose={() => setShortages(null)}
        />
      )}
    </div>
  )
}

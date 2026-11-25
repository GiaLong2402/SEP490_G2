import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ChevronDownIcon, ChevronUpIcon, InfoIcon, TruckIcon } from '@/components/icons/Icons'
import { salesRequestsApi } from '@/api/salesRequests'
import { useAuth } from '@/context/AuthContext'
import { ROUTES, requestDetailPath } from '@/constants/routes'
import './BulkOrderForm.css'

const EMPTY_FORM = {
  trays: '',
  date: '',
  phone: '',
  note: '',
}

/**
 * Kiểm tra dữ liệu form. Trả về object lỗi, ví dụ { phone: 'Số điện thoại chưa hợp lệ.' }
 * Không có lỗi => trả về {}
 */
function validate(form) {
  const errors = {}

  const trays = Number(form.trays)
  if (!Number.isInteger(trays) || trays < 1) {
    errors.trays = 'Nhập số khay nguyên dương.'
  }

  if (!form.date) {
    errors.date = 'Chọn ngày cần nhận.'
  } else {
    const today = new Date()
    today.setHours(0, 0, 0, 0)
    if (new Date(form.date) < today) errors.date = 'Ngày nhận phải từ hôm nay trở đi.'
  }

  // Số VN: bắt đầu bằng 0 hoặc +84, sau đó 8–10 chữ số
  if (!/^(0|\+84)\d{8,10}$/.test(String(form.phone || '').replace(/\s/g, ''))) {
    errors.phone = 'Số điện thoại chưa hợp lệ.'
  }

  return errors
}

function buildSummary({ productName, variantLabel, trays, phone, note }) {
  const parts = [
    `${productName} · ${variantLabel} × ${trays} khay`,
    `SĐT liên hệ: ${phone.replace(/\s/g, '')}`,
  ]
  const noteText = String(note || '').trim()
  if (noteText) parts.push(`Ghi chú: ${noteText}`)
  return parts.join(' — ')
}

/**
 * Nút "Đặt số lượng lớn" + form gửi yêu cầu tư vấn → POST /me/sales-requests.
 *
 * props:
 * - productName, variantLabel, variantId
 * - availableQty: tồn khay hiện tại (để lưu kèm; không bắt vượt tồn)
 */
export default function BulkOrderForm({ productName, variantLabel, variantId, availableQty = 0 }) {
  const navigate = useNavigate()
  const { isLoggedIn, user } = useAuth()
  const [isOpen, setIsOpen] = useState(false)
  const [form, setForm] = useState(EMPTY_FORM)
  const [errors, setErrors] = useState({})
  const [status, setStatus] = useState('idle') // idle | sending | sent
  const [submitError, setSubmitError] = useState('')
  const [created, setCreated] = useState(null)

  useEffect(() => {
    if (!isOpen || !user?.phone) return
    setForm((prev) => (prev.phone ? prev : { ...prev, phone: user.phone }))
  }, [isOpen, user?.phone])

  function updateField(field, value) {
    setForm((prev) => ({ ...prev, [field]: value }))
  }

  function openPanel() {
    if (!isLoggedIn) {
      navigate(ROUTES.LOGIN, {
        state: { from: typeof window !== 'undefined' ? window.location.pathname : ROUTES.PRODUCTS },
      })
      return
    }
    setIsOpen((open) => !open)
  }

  async function handleSubmit(e) {
    e.preventDefault()
    setSubmitError('')
    setCreated(null)

    if (!isLoggedIn) {
      navigate(ROUTES.LOGIN, {
        state: { from: typeof window !== 'undefined' ? window.location.pathname : ROUTES.PRODUCTS },
      })
      return
    }

    const newErrors = validate(form)
    setErrors(newErrors)
    if (Object.keys(newErrors).length > 0) return

    const trays = Number(form.trays)
    const stock = Math.max(0, Number(availableQty) || 0)
    // Portal cho phép chỉ requestedQty; nếu gửi cả available thì API yêu cầu vượt tồn.
    // Đặt số lượng lớn / tư vấn: chỉ gửi requestedQty (+ summary, ngày, SĐT).
    const payload = {
      summary: buildSummary({
        productName,
        variantLabel,
        trays,
        phone: form.phone,
        note: form.note,
      }),
      neededDate: form.date,
      requestedQty: trays,
      contactPhone: form.phone.replace(/\s/g, ''),
      lines: variantId
        ? [{ variantId: Number(variantId), unit: 'pack', qty: trays }]
        : undefined,
    }
    // Khi khách chọn nhiều hơn tồn, gắn availableQty để Sales thấy thiếu hàng (checkout-shortage style).
    if (trays > stock) {
      payload.availableQty = stock
    }

    setStatus('sending')
    try {
      const result = await salesRequestsApi.create(payload)
      setCreated(result)
      setStatus('sent')
      setForm({ ...EMPTY_FORM, phone: user?.phone || '' })
      setErrors({})
    } catch (err) {
      setStatus('idle')
      setSubmitError(err?.message || 'Không gửi được yêu cầu. Vui lòng thử lại.')
    }
  }

  return (
    <div className="bulk-order">
      <button
        type="button"
        className="bulk-order__toggle"
        aria-expanded={isOpen}
        aria-controls="bulk-order-panel"
        onClick={openPanel}
      >
        <TruckIcon size={26} />
        <span>Đặt số lượng lớn</span>
        {isOpen ? <ChevronUpIcon /> : <ChevronDownIcon />}
      </button>

      {isOpen && (
        <form id="bulk-order-panel" className="bulk-order__panel" onSubmit={handleSubmit} noValidate>
          <p className="bulk-order__hint">
            Cửa hàng sẽ liên hệ để xác nhận khả năng đáp ứng và báo giá.
          </p>
          <p className="bulk-order__product">
            {productName} &nbsp;-&nbsp; {variantLabel}
          </p>

          <div className="bulk-order__row">
            <label className="bulk-order__field">
              Số khay dự kiến
              <span className="bulk-order__input-suffix">
                <input
                  type="number"
                  min="1"
                  placeholder="Nhập số khay"
                  value={form.trays}
                  onChange={(e) => updateField('trays', e.target.value)}
                />
                <span>khay</span>
              </span>
              {errors.trays && <span className="field-error">{errors.trays}</span>}
            </label>

            <label className="bulk-order__field">
              Ngày cần nhận
              <input
                type="date"
                value={form.date}
                onChange={(e) => updateField('date', e.target.value)}
              />
              {errors.date && <span className="field-error">{errors.date}</span>}
            </label>
          </div>

          <label className="bulk-order__field">
            Số điện thoại liên hệ
            <input
              type="tel"
              placeholder="Nhập số điện thoại"
              value={form.phone}
              onChange={(e) => updateField('phone', e.target.value)}
            />
            {errors.phone && <span className="field-error">{errors.phone}</span>}
          </label>

          <label className="bulk-order__field">
            Ghi chú (không bắt buộc)
            <textarea
              rows={3}
              placeholder="Ví dụ: thời gian nhận, yêu cầu đóng gói..."
              value={form.note}
              onChange={(e) => updateField('note', e.target.value)}
            />
          </label>

          <button type="submit" className="bulk-order__submit" disabled={status === 'sending'}>
            {status === 'sending' ? 'Đang gửi...' : 'Gửi yêu cầu'}
          </button>

          {submitError ? (
            <p className="bulk-order__error" role="alert">
              {submitError}
            </p>
          ) : null}

          {status === 'sent' && created ? (
            <p className="bulk-order__success" role="status">
              Đã tạo yêu cầu <strong>{created.code}</strong>. Cửa hàng sẽ liên hệ lại sớm.{' '}
              <Link to={requestDetailPath(created.code)}>Xem yêu cầu</Link>
              {' · '}
              <Link to={ROUTES.ORDERS}>Đơn &amp; yêu cầu của tôi</Link>
            </p>
          ) : null}

          <p className="bulk-order__note">
            <InfoIcon size={22} />
            <span>
              Đây là yêu cầu tư vấn, chưa phải đơn hàng.
              <br />
              Bạn chưa cần thanh toán ở bước này.
            </span>
          </p>
        </form>
      )}
    </div>
  )
}

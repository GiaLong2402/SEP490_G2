import { useState } from 'react'
import { ChevronDownIcon, ChevronUpIcon, InfoIcon, TruckIcon } from '@/components/icons/Icons'
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
  if (!/^(0|\+84)\d{8,10}$/.test(form.phone.replace(/\s/g, ''))) {
    errors.phone = 'Số điện thoại chưa hợp lệ.'
  }

  return errors
}

/**
 * Nút "Đặt số lượng lớn" + form yêu cầu tư vấn (bấm nút để mở/đóng).
 *
 * props:
 * - productName:  tên sản phẩm, ví dụ "Tôm sú sơ chế"
 * - variantLabel: quy cách đang chọn, ví dụ "Khay 500 g"
 */
export default function BulkOrderForm({ productName, variantLabel }) {
  const [isOpen, setIsOpen] = useState(false)
  const [form, setForm] = useState(EMPTY_FORM)
  const [errors, setErrors] = useState({})
  const [status, setStatus] = useState('idle') // idle | sending | sent

  // Cập nhật 1 ô trong form, ví dụ updateField('phone', '0901...')
  function updateField(field, value) {
    setForm({ ...form, [field]: value })
  }

  function handleSubmit(e) {
    e.preventDefault()

    const newErrors = validate(form)
    setErrors(newErrors)
    if (Object.keys(newErrors).length > 0) return

    setStatus('sending')
    // DEMO: giả lập gửi mất 0.6 giây — sau này thay bằng gọi API Spring Boot
    window.setTimeout(() => {
      setStatus('sent')
      setForm(EMPTY_FORM)
    }, 600)
  }

  return (
    <div className="bulk-order">
      <button
        type="button"
        className="bulk-order__toggle"
        aria-expanded={isOpen}
        aria-controls="bulk-order-panel"
        onClick={() => setIsOpen(!isOpen)}
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

          {/* Hàng 1: số khay + ngày nhận (2 cột) */}
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

          {status === 'sent' && (
            <p className="bulk-order__success" role="status">
              Đã nhận yêu cầu. Cửa hàng sẽ liên hệ lại sớm.
            </p>
          )}

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

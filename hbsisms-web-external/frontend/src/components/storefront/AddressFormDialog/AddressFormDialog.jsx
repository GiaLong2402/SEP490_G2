import { useState } from 'react'
import Modal from '@/components/ui/Modal'
import Button from '@/components/ui/Button'
import { MapPinIcon } from '@/components/icons/Icons'
import { addressesApi } from '@/api/addresses'
import { isValidVietnamPhone } from '@/utils/phone'
import './AddressFormDialog.css'

const EMPTY = {
  recipientName: '',
  phone: '',
  line1: '',
  ward: '',
  district: '',
  province: 'Hải Phòng',
  isDefault: false,
}

function validate(form) {
  const errors = {}
  if (!form.recipientName.trim()) errors.recipientName = 'Nhập tên người nhận.'
  if (!isValidVietnamPhone(form.phone)) errors.phone = 'Số điện thoại chưa hợp lệ.'
  if (!form.line1.trim()) errors.line1 = 'Nhập số nhà, tên đường.'
  if (!form.province.trim()) errors.province = 'Nhập tỉnh / thành phố.'
  return errors
}

/**
 * Popup thêm / sửa địa chỉ nhận hàng — gọi addressesApi (POST/PUT /me/addresses).
 *
 * props:
 * - open, onClose
 * - address: địa chỉ đang sửa (null = thêm mới)
 * - forceDefault: true khi chưa có địa chỉ nào (địa chỉ đầu tiên luôn là mặc định)
 * - defaults: giá trị gợi ý khi thêm mới, vd. { recipientName, phone } từ tài khoản
 * - onSaved(address): nhận địa chỉ đã lưu (đã qua mapper)
 */
export default function AddressFormDialog({ open, onClose, address, forceDefault = false, defaults, onSaved }) {
  const isEdit = Boolean(address)
  const [form, setForm] = useState(() => ({ ...EMPTY, ...defaults, ...address, isDefault: forceDefault || Boolean(address?.isDefault) }))
  const [errors, setErrors] = useState({})
  const [saving, setSaving] = useState(false)
  const [formError, setFormError] = useState('')

  function update(key, value) {
    setForm((prev) => ({ ...prev, [key]: value }))
    if (errors[key]) setErrors((prev) => ({ ...prev, [key]: '' }))
  }

  async function handleSubmit(e) {
    e.preventDefault()
    const found = validate(form)
    setErrors(found)
    if (Object.keys(found).length) return

    setSaving(true)
    setFormError('')
    try {
      const saved = isEdit ? await addressesApi.update(address.id, form) : await addressesApi.create(form)
      onSaved?.(saved)
      onClose()
    } catch (err) {
      // 400 từ backend: { message, errors: { field: message } }
      if (err.payload?.errors) setErrors(err.payload.errors)
      setFormError(err.message || 'Không lưu được địa chỉ.')
    } finally {
      setSaving(false)
    }
  }

  const field = (key, label, props = {}) => (
    <label className={`addr-field ${props.full ? 'addr-field--full' : ''}`}>
      <span>
        {label}
        {props.required && <b aria-hidden="true"> *</b>}
      </span>
      <input
        value={form[key]}
        onChange={(e) => update(key, e.target.value)}
        aria-invalid={Boolean(errors[key])}
        placeholder={props.placeholder}
        type={props.type || 'text'}
        inputMode={props.inputMode}
        autoComplete={props.autoComplete}
      />
      {errors[key] && <small role="alert">{errors[key]}</small>}
    </label>
  )

  return (
    <Modal
      open={open}
      onClose={onClose}
      busy={saving}
      title={isEdit ? 'Sửa địa chỉ' : 'Thêm địa chỉ mới'}
      description="Địa chỉ dùng để cửa hàng giao hàng tới bạn."
      icon={<MapPinIcon size={24} />}
      footer={
        <>
          <Button type="button" variant="ghost" pill onClick={onClose} disabled={saving}>
            Hủy
          </Button>
          <Button type="submit" form="address-form" variant="primary" pill disabled={saving}>
            {saving ? 'Đang lưu…' : isEdit ? 'Lưu thay đổi' : 'Thêm địa chỉ'}
          </Button>
        </>
      }
    >
      <form id="address-form" className="addr-form" onSubmit={handleSubmit} noValidate>
        {field('recipientName', 'Tên người nhận', { required: true, placeholder: 'Nguyễn Văn An', autoComplete: 'name' })}
        {field('phone', 'Số điện thoại', { required: true, type: 'tel', inputMode: 'tel', placeholder: '0912 345 678', autoComplete: 'tel' })}
        {field('line1', 'Số nhà, tên đường', { required: true, full: true, placeholder: 'Số 15 Đường Hòa Bình', autoComplete: 'address-line1' })}
        {field('ward', 'Phường / Xã', { placeholder: 'Phường Trần Phú' })}
        {field('district', 'Quận / Huyện', { placeholder: 'Quận Hồng Bàng' })}
        {field('province', 'Tỉnh / Thành phố', { required: true, full: true })}

        <label className={`addr-default ${forceDefault ? 'is-locked' : ''}`}>
          <input
            type="checkbox"
            checked={form.isDefault}
            disabled={forceDefault || (isEdit && address.isDefault)}
            onChange={(e) => update('isDefault', e.target.checked)}
          />
          <span>
            Đặt làm địa chỉ mặc định
            <small>Được chọn sẵn khi thanh toán</small>
          </span>
        </label>

        {formError && (
          <p className="addr-form__error" role="alert">
            {formError}
          </p>
        )}
      </form>
    </Modal>
  )
}

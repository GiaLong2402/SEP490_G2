import { useEffect, useMemo, useRef, useState } from 'react'
import Modal from '@/components/ui/Modal'
import Button from '@/components/ui/Button'
import { MapPinIcon } from '@/components/icons/Icons'
import { addressesApi } from '@/api/addresses'
import { geoApi } from '@/api/geo'
import { isValidVietnamPhone } from '@/utils/phone'
import GoongMapPicker from '@/components/maps/GoongMapPicker'
import { applyReverseAddress } from '@/shared/geo/applyReverseAddress'
import { listProvinces, listWardsByProvinceCode, provinceCodeOf } from '@/shared/geo/vnAdmin'
import './AddressFormDialog.css'

const EMPTY = {
  recipientName: '',
  phone: '',
  line1: '',
  ward: '',
  district: '',
  province: '',
  latitude: null,
  longitude: null,
  distanceKm: null,
  isDefault: false,
}

const FALLBACK_SHOP = { lat: 21.012728, lng: 105.525905 }
const PROVINCES = listProvinces()
const GEOCODE_DEBOUNCE_MS = 700
const REVERSE_DEBOUNCE_MS = 450

function validate(form) {
  const errors = {}
  if (!form.recipientName.trim()) errors.recipientName = 'Nhập tên người nhận.'
  if (!isValidVietnamPhone(form.phone)) errors.phone = 'Số điện thoại chưa hợp lệ.'
  if (!form.line1.trim()) errors.line1 = 'Nhập số nhà, tên đường.'
  if (!form.province.trim()) errors.province = 'Chọn tỉnh / thành phố.'
  return errors
}

/**
 * Popup thêm / sửa địa chỉ khách.
 * Cascade Tỉnh → Phường/xã; map tự định vị khi đủ địa chỉ (không cần nút).
 */
export default function AddressFormDialog({ open, onClose, address, forceDefault = false, defaults, onSaved }) {
  const isEdit = Boolean(address)
  const [form, setForm] = useState(() => ({
    ...EMPTY,
    ...defaults,
    ...address,
    latitude: address?.latitude ?? null,
    longitude: address?.longitude ?? null,
    distanceKm: address?.distanceKm ?? null,
    isDefault: forceDefault || Boolean(address?.isDefault),
  }))
  const [errors, setErrors] = useState({})
  const [saving, setSaving] = useState(false)
  const [formError, setFormError] = useState('')
  const [shop, setShop] = useState(FALLBACK_SHOP)
  const [locating, setLocating] = useState(false)
  const [applyingMap, setApplyingMap] = useState(false)
  /** User kéo ghim → tạm không auto-geocode ghi đè đến khi đổi text địa chỉ. */
  const pinLockedRef = useRef(false)
  /** Click/kéo map → tự reverse vào ô input. */
  const mapDrivenRef = useRef(false)
  const lastQueryRef = useRef('')

  const provinceCode = provinceCodeOf(form.province)
  const wardOptions = useMemo(() => listWardsByProvinceCode(provinceCode), [provinceCode])

  const addressQuery = useMemo(
    () => [form.line1, form.ward, form.province].filter((p) => p && String(p).trim()).join(', '),
    [form.line1, form.ward, form.province],
  )

  useEffect(() => {
    if (!open) return
    pinLockedRef.current = false
    mapDrivenRef.current = false
    lastQueryRef.current = ''
    setForm({
      ...EMPTY,
      ...defaults,
      ...address,
      latitude: address?.latitude ?? null,
      longitude: address?.longitude ?? null,
      distanceKm: address?.distanceKm ?? null,
      isDefault: forceDefault || Boolean(address?.isDefault),
    })
    setErrors({})
    setFormError('')
    setLocating(false)
  }, [open, address, forceDefault, defaults])

  useEffect(() => {
    if (!open) return
    let cancelled = false
    geoApi
      .shop()
      .then((s) => {
        if (!cancelled) setShop({ lat: Number(s.latitude), lng: Number(s.longitude) })
      })
      .catch(() => {})
    return () => {
      cancelled = true
    }
  }, [open])

  // Tự geocode khi đủ số nhà + tỉnh (debounce).
  useEffect(() => {
    if (!open) return
    if (!form.line1.trim() || !form.province.trim()) return
    if (pinLockedRef.current && lastQueryRef.current === addressQuery) return

    const t = window.setTimeout(async () => {
      if (pinLockedRef.current && lastQueryRef.current === addressQuery) return
      setLocating(true)
      setFormError('')
      try {
        const hit = await geoApi.geocode(addressQuery)
        lastQueryRef.current = addressQuery
        pinLockedRef.current = false
        setForm((prev) => ({
          ...prev,
          latitude: Number(hit.latitude),
          longitude: Number(hit.longitude),
          distanceKm: Number(hit.distanceKm),
        }))
      } catch (err) {
        // Không chặn lưu — API vẫn geocode lúc save nếu cần.
        setFormError(err.message || 'Chưa định vị được trên bản đồ. Bạn vẫn có thể bấm map / kéo ghim hoặc lưu.')
      } finally {
        setLocating(false)
      }
    }, GEOCODE_DEBOUNCE_MS)

    return () => window.clearTimeout(t)
  }, [open, addressQuery, form.line1, form.province])

  function update(key, value) {
    pinLockedRef.current = false
    setForm((prev) => ({ ...prev, [key]: value }))
    if (errors[key]) setErrors((prev) => ({ ...prev, [key]: '' }))
  }

  async function reverseIntoForm(lat, lng) {
    setApplyingMap(true)
    setFormError('')
    try {
      const hit = await geoApi.reverse(lat, lng)
      const next = applyReverseAddress(hit)
      pinLockedRef.current = true
      lastQueryRef.current = [next.line1, next.ward, next.province]
        .filter((p) => p && String(p).trim())
        .join(', ')
      setForm((prev) => ({
        ...prev,
        ...next,
        district: '',
        recipientName: prev.recipientName,
        phone: prev.phone,
        isDefault: prev.isDefault,
      }))
    } catch (err) {
      setFormError(err.message || 'Không đọc được địa chỉ từ bản đồ.')
    } finally {
      setApplyingMap(false)
      mapDrivenRef.current = false
    }
  }

  // Click / kéo ghim → tự đổ số nhà + tỉnh + phường.
  useEffect(() => {
    if (!open || !mapDrivenRef.current) return
    if (form.latitude == null || form.longitude == null) return
    const lat = form.latitude
    const lng = form.longitude
    const t = window.setTimeout(() => {
      void reverseIntoForm(lat, lng)
    }, REVERSE_DEBOUNCE_MS)
    return () => window.clearTimeout(t)
    // eslint-disable-next-line react-hooks/exhaustive-deps -- only when pin moves from map
  }, [open, form.latitude, form.longitude])

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
        value={form[key] ?? ''}
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
      description="Điền địa chỉ và chọn tỉnh / phường. Bản đồ tự định vị — bấm map hoặc kéo ghim để chỉnh."
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

        <label className="addr-field">
          <span>
            Tỉnh / Thành phố
            <b aria-hidden="true"> *</b>
          </span>
          <select
            value={provinceCode || ''}
            aria-invalid={Boolean(errors.province)}
            onChange={(e) => {
              const code = e.target.value
              const p = PROVINCES.find((x) => x.code === code)
              pinLockedRef.current = false
              setForm((prev) => ({
                ...prev,
                province: p?.name || '',
                ward: '',
                district: '',
              }))
              if (errors.province) setErrors((prev) => ({ ...prev, province: '' }))
            }}
          >
            <option value="">— Chọn tỉnh/thành —</option>
            {PROVINCES.map((p) => (
              <option key={p.code} value={p.code}>
                {p.name}
              </option>
            ))}
          </select>
          {errors.province && <small role="alert">{errors.province}</small>}
        </label>

        <label className="addr-field">
          <span>Phường / Xã</span>
          <select
            value={wardOptions.find((w) => w.name === form.ward)?.code || ''}
            disabled={!provinceCode}
            onChange={(e) => {
              const w = wardOptions.find((x) => x.code === e.target.value)
              update('ward', w?.name || '')
            }}
          >
            <option value="">{provinceCode ? '— Chọn phường/xã —' : 'Chọn tỉnh trước'}</option>
            {wardOptions.map((w) => (
              <option key={w.code} value={w.code}>
                {w.name}
              </option>
            ))}
          </select>
        </label>

        <div className="addr-map-block">
          <div className="addr-map-actions">
            {applyingMap && <span className="addr-map-distance">Đang đọc địa chỉ từ bản đồ…</span>}
            {locating && <span className="addr-map-distance">Đang định vị trên bản đồ…</span>}
          </div>
          <GoongMapPicker
            open={open}
            latitude={form.latitude}
            longitude={form.longitude}
            fallbackCenter={shop}
            onChange={(pos) => {
              pinLockedRef.current = true
              mapDrivenRef.current = true
              setForm((prev) => ({
                ...prev,
                latitude: pos.lat,
                longitude: pos.lng,
                distanceKm: null,
              }))
            }}
          />
        </div>

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

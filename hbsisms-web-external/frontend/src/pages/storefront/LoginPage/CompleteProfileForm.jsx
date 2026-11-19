import { useId, useMemo, useState } from 'react'
import Button from '@/components/ui/Button'
import { CheckIcon, LockIcon } from '@/components/icons/Icons'
import { formatPhoneDisplay } from '@/utils/phone'
import { listProvinces, listWardsByProvinceCode, provinceCodeOf } from '@/shared/geo/vnAdmin'

const PROVINCES = listProvinces()

/**
 * Form hoàn tất hồ sơ — hiện sau khi bấm "Tạo tài khoản"
 * SĐT khóa (đã OTP). Tỉnh/Phường dropdown giống sổ địa chỉ khách.
 */
export default function CompleteProfileForm({ phone, submitting, onComplete }) {
  const nameId = useId()
  const emailId = useId()
  const streetId = useId()
  const wardId = useId()
  const provinceId = useId()
  const defaultId = useId()

  const [fullName, setFullName] = useState('')
  const [email, setEmail] = useState('')
  const [street, setStreet] = useState('')
  const [ward, setWard] = useState('')
  const [province, setProvince] = useState('')
  const [isDefaultAddress, setIsDefaultAddress] = useState(true)
  const [error, setError] = useState('')

  const provinceCode = provinceCodeOf(province)
  const wardOptions = useMemo(() => listWardsByProvinceCode(provinceCode), [provinceCode])

  async function handleSubmit(e) {
    e.preventDefault()
    if (submitting) return

    if (!fullName.trim()) {
      setError('Vui lòng nhập họ và tên.')
      return
    }
    if (!street.trim() || !ward.trim() || !province.trim()) {
      setError('Vui lòng chọn đủ tỉnh/thành, phường/xã và số nhà.')
      return
    }

    setError('')
    try {
      await onComplete({
        fullName: fullName.trim(),
        email: email.trim(),
        address: {
          street: street.trim(),
          ward: ward.trim(),
          district: '',
          province: province.trim(),
          isDefault: isDefaultAddress,
        },
      })
    } catch (err) {
      setError(err?.message || 'Không tạo được tài khoản. Thử lại.')
    }
  }

  return (
    <div className="auth-profile">
      <h2 id="auth-card-title" className="auth-profile__title">
        Hoàn tất hồ sơ
      </h2>
      <p className="auth-profile__lead">
        Bạn chỉ cần làm một lần để mua hàng nhanh hơn.
      </p>

      <form className="auth-profile__form" onSubmit={handleSubmit} noValidate>
        <div className="auth-field">
          <label className="auth-profile__label">Số điện thoại đăng nhập</label>
          <div className="auth-profile__locked">
            <span>{formatPhoneDisplay(phone)}</span>
            <span className="auth-profile__lock-icon" aria-hidden="true">
              <LockIcon size={18} />
            </span>
          </div>
          <p className="auth-profile__verified">
            <CheckIcon size={14} />
            Đã xác thực OTP
          </p>
        </div>

        <div className="auth-field">
          <label className="auth-profile__label" htmlFor={nameId}>
            Họ và tên
          </label>
          <input
            id={nameId}
            className="auth-profile__input"
            type="text"
            autoComplete="name"
            placeholder="Nguyễn Văn An"
            value={fullName}
            onChange={(e) => {
              setFullName(e.target.value)
              setError('')
            }}
          />
        </div>

        <div className="auth-field">
          <label className="auth-profile__label" htmlFor={emailId}>
            Email (không bắt buộc)
          </label>
          <input
            id={emailId}
            className="auth-profile__input"
            type="email"
            autoComplete="email"
            placeholder="an.nguyen@gmail.com"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
          <p className="auth-profile__hint">Nhận biên lai và thông báo đơn hàng.</p>
        </div>

        <h3 className="auth-profile__section">Địa chỉ nhận hàng đầu tiên</h3>

        <div className="auth-field">
          <label className="auth-profile__label" htmlFor={streetId}>
            Số nhà, tên đường
          </label>
          <input
            id={streetId}
            className="auth-profile__input"
            type="text"
            autoComplete="street-address"
            placeholder="Số 15 Đường Hòa Bình"
            value={street}
            onChange={(e) => {
              setStreet(e.target.value)
              setError('')
            }}
          />
        </div>

        <div className="auth-profile__row">
          <div className="auth-field">
            <label className="auth-profile__label" htmlFor={provinceId}>
              Tỉnh / Thành phố
            </label>
            <select
              id={provinceId}
              className="auth-profile__input"
              value={provinceCode || ''}
              onChange={(e) => {
                const p = PROVINCES.find((x) => x.code === e.target.value)
                setProvince(p?.name || '')
                setWard('')
                setError('')
              }}
            >
              <option value="">— Chọn tỉnh/thành —</option>
              {PROVINCES.map((p) => (
                <option key={p.code} value={p.code}>
                  {p.name}
                </option>
              ))}
            </select>
          </div>
          <div className="auth-field">
            <label className="auth-profile__label" htmlFor={wardId}>
              Phường / Xã
            </label>
            <select
              id={wardId}
              className="auth-profile__input"
              value={wardOptions.find((w) => w.name === ward)?.code || ''}
              disabled={!provinceCode}
              onChange={(e) => {
                const w = wardOptions.find((x) => x.code === e.target.value)
                setWard(w?.name || '')
                setError('')
              }}
            >
              <option value="">{provinceCode ? '— Chọn phường/xã —' : 'Chọn tỉnh trước'}</option>
              {wardOptions.map((w) => (
                <option key={w.code} value={w.code}>
                  {w.name}
                </option>
              ))}
            </select>
          </div>
        </div>

        <label className="auth-keep auth-profile__default" htmlFor={defaultId}>
          <input
            id={defaultId}
            type="checkbox"
            checked={isDefaultAddress}
            onChange={(e) => setIsDefaultAddress(e.target.checked)}
          />
          <span>Đặt làm địa chỉ mặc định</span>
        </label>

        {error && (
          <p className="auth-field__error" role="alert">
            {error}
          </p>
        )}

        <Button
          type="submit"
          variant="secondary"
          fullWidth
          className="auth-submit"
          disabled={submitting}
        >
          {submitting ? 'Đang lưu...' : 'Hoàn tất'}
        </Button>
      </form>
    </div>
  )
}

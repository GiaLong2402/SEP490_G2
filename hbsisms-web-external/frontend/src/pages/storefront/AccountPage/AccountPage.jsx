import { useMemo, useState } from 'react'
import AccountShell from '@/components/storefront/AccountShell/AccountShell'
import Button from '@/components/ui/Button'
import { CheckIcon, ClipboardIcon, MailIcon, PhoneIcon, TruckIcon, UserIcon } from '@/components/icons/Icons'
import { ROUTES } from '@/constants/routes'
import { useAuth } from '@/context/AuthContext'
import { getInitials } from '@/utils/initials'
import './AccountPage.css'

function formatJoined(date = new Date()) {
  const d = String(date.getDate()).padStart(2, '0')
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const y = date.getFullYear()
  return `${d}/${m}/${y}`
}

export default function AccountPage() {
  const { user, isLoggedIn } = useAuth()
  const [savedFlash, setSavedFlash] = useState(false)

  const displayName = user?.name || 'Khách hàng'
  const initials = getInitials(displayName)

  const [savedForm, setSavedForm] = useState(() => ({
    fullName: displayName,
    phone: user?.phone || '',
    email: user?.email || '',
    sendReceipt: true,
    sendOrderUpdates: true,
  }))
  const [form, setForm] = useState(savedForm)
  const isDirty = Object.keys(form).some((key) => form[key] !== savedForm[key])
  const hasEmail = form.email.trim() !== ''

  const joinedLabel = useMemo(() => formatJoined(), [])

  function updateField(key, value) {
    setForm((prev) => ({ ...prev, [key]: value }))
  }

  function handleSave(e) {
    e.preventDefault()
    setSavedForm(form)
    setSavedFlash(true)
    window.setTimeout(() => setSavedFlash(false), 2200)
  }

  function handleReset() {
    setForm(savedForm)
  }

  return (
    <AccountShell
      title="Tài khoản"
      breadcrumbs={[{ label: 'Trang chủ', to: ROUTES.HOME }, { label: 'Tài khoản' }]}
    >
      <form className="account-card account-card--flush" onSubmit={handleSave}>
        {/* Phần đầu: avatar + tên + mức hoàn thiện hồ sơ */}
        <div className="account-hero">
          <span className="account-hero__avatar" aria-hidden="true">
            {initials}
          </span>
          <div className="account-hero__meta">
            <h2>{form.fullName.trim() || displayName}</h2>
            <div className="account-hero__chips">
              <span className="account-chip account-chip--accent">
                {isLoggedIn ? 'Khách hàng' : 'Chưa đăng nhập'}
              </span>
              {isLoggedIn && <span className="account-chip">Tham gia {joinedLabel}</span>}
            </div>
          </div>
        </div>

        {/* Thông tin liên hệ */}
        <section className="account-section">
          <div className="account-grid">
            <label className="account-field account-field--full">
              <span className="account-field__label">Họ và tên</span>
              <span className="account-input">
                <UserIcon size={18} />
                <input
                  type="text"
                  value={form.fullName}
                  onChange={(e) => updateField('fullName', e.target.value)}
                  autoComplete="name"
                  placeholder="Nhập họ và tên"
                />
              </span>
            </label>

            <label className="account-field">
              <span className="account-field__label">Số điện thoại</span>
              <span className="account-input">
                <PhoneIcon size={18} />
                <input
                  type="tel"
                  value={form.phone}
                  onChange={(e) => updateField('phone', e.target.value)}
                  autoComplete="tel"
                  placeholder="Nhập số điện thoại"
                />
                <button type="button" className="account-input__action">
                  Đổi SĐT
                </button>
              </span>
            </label>

            <label className="account-field">
              <span className="account-field__label">Email</span>
              <span className="account-input">
                <MailIcon size={18} />
                <input
                  type="email"
                  value={form.email}
                  onChange={(e) => updateField('email', e.target.value)}
                  autoComplete="email"
                  placeholder="you@email.com"
                />
              </span>
            </label>
          </div>
        </section>

        {/* Thông báo qua email */}
        <section className="account-section">
          <div className="account-section__head">
            <h3>Thông báo qua email</h3>
            <p>Chọn những thông tin Hòa Bình gửi đến email của bạn.</p>
          </div>

          {!hasEmail && (
            <p className="account-note">Nhập email ở phần trên để bật các thông báo này.</p>
          )}

          <div className="account-toggles">
            <label className={`account-toggle ${hasEmail ? '' : 'is-disabled'}`}>
              <span className="account-toggle__icon" aria-hidden="true">
                <ClipboardIcon size={20} />
              </span>
              <span className="account-toggle__text">
                <strong>Gửi biên lai</strong>
                <span>Nhận hóa đơn sau mỗi lần thanh toán</span>
              </span>
              <input
                type="checkbox"
                checked={hasEmail && form.sendReceipt}
                disabled={!hasEmail}
                onChange={(e) => updateField('sendReceipt', e.target.checked)}
              />
              <span className="account-toggle__ui" aria-hidden="true" />
            </label>
            <label className={`account-toggle ${hasEmail ? '' : 'is-disabled'}`}>
              <span className="account-toggle__icon" aria-hidden="true">
                <TruckIcon size={20} />
              </span>
              <span className="account-toggle__text">
                <strong>Gửi cập nhật đơn hàng</strong>
                <span>Báo khi đơn được xác nhận, giao và hoàn tất</span>
              </span>
              <input
                type="checkbox"
                checked={hasEmail && form.sendOrderUpdates}
                disabled={!hasEmail}
                onChange={(e) => updateField('sendOrderUpdates', e.target.checked)}
              />
              <span className="account-toggle__ui" aria-hidden="true" />
            </label>
          </div>
        </section>

        {/* Lưu / hủy */}
        <div className="account-actions">
          {savedFlash && (
            <span className="account-actions__flash" role="status">
              <CheckIcon size={16} /> Đã lưu thay đổi
            </span>
          )}
          <Button type="button" variant="ghost" pill onClick={handleReset} disabled={!isDirty}>
            Hủy thay đổi
          </Button>
          <Button type="submit" variant="primary" pill disabled={!isDirty}>
            <CheckIcon size={18} />
            Lưu thay đổi
          </Button>
        </div>
      </form>
    </AccountShell>
  )
}

import { useEffect, useMemo, useState } from 'react'
import AccountShell from '@/components/storefront/AccountShell/AccountShell'
import Button from '@/components/ui/Button'
import Modal from '@/components/ui/Modal'
import { CheckIcon, ClipboardIcon, MailIcon, PhoneIcon, TruckIcon, UserIcon } from '@/components/icons/Icons'
import { profileApi } from '@/api/profile'
import {
  clearPhoneAuthSession,
  confirmPhoneOtp,
  mapFirebaseAuthError,
  sendPhoneOtp,
} from '@/auth/phoneAuth'
import { ROUTES } from '@/constants/routes'
import { useAuth } from '@/context/AuthContext'
import { isValidVietnamPhone } from '@/utils/phone'
import { getInitials } from '@/utils/initials'
import './AccountPage.css'

function formatJoined(date = new Date()) {
  const d = String(date.getDate()).padStart(2, '0')
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const y = date.getFullYear()
  return `${d}/${m}/${y}`
}

export default function AccountPage() {
  const { user, isLoggedIn, applyUser } = useAuth()
  const [savedFlash, setSavedFlash] = useState(false)
  const [saving, setSaving] = useState(false)
  const [saveError, setSaveError] = useState('')

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
  const isDirty = ['fullName', 'email', 'sendReceipt', 'sendOrderUpdates'].some(
    (key) => form[key] !== savedForm[key],
  )
  const hasEmail = form.email.trim() !== ''

  const [phoneDialog, setPhoneDialog] = useState(false)
  const [phoneStep, setPhoneStep] = useState('phone') // phone | code
  const [newPhone, setNewPhone] = useState('')
  const [otpCode, setOtpCode] = useState('')
  const [otpMode, setOtpMode] = useState('stub')
  const [phoneBusy, setPhoneBusy] = useState(false)
  const [phoneError, setPhoneError] = useState('')

  useEffect(() => {
    if (!user) return
    const next = {
      fullName: user.name || '',
      phone: user.phone || '',
      email: user.email || '',
      sendReceipt: savedForm.sendReceipt,
      sendOrderUpdates: savedForm.sendOrderUpdates,
    }
    setSavedForm(next)
    setForm(next)
    // eslint-disable-next-line react-hooks/exhaustive-deps -- sync when auth user identity changes
  }, [user?.id, user?.name, user?.phone, user?.email])

  const joinedLabel = useMemo(() => formatJoined(), [])

  function updateField(key, value) {
    setForm((prev) => ({ ...prev, [key]: value }))
  }

  async function handleSave(e) {
    e.preventDefault()
    if (!isLoggedIn) return
    setSaving(true)
    setSaveError('')
    try {
      const updated = await profileApi.update({
        fullName: form.fullName,
        email: form.email,
      })
      applyUser(updated)
      setSavedForm({
        ...form,
        fullName: updated.name || form.fullName,
        phone: updated.phone || form.phone,
        email: updated.email || '',
      })
      setForm((prev) => ({
        ...prev,
        fullName: updated.name || prev.fullName,
        phone: updated.phone || prev.phone,
        email: updated.email || '',
      }))
      setSavedFlash(true)
      window.setTimeout(() => setSavedFlash(false), 2200)
    } catch (err) {
      setSaveError(err.message || 'Không lưu được hồ sơ.')
    } finally {
      setSaving(false)
    }
  }

  function handleReset() {
    setForm(savedForm)
    setSaveError('')
  }

  function openPhoneDialog() {
    clearPhoneAuthSession()
    setPhoneDialog(true)
    setPhoneStep('phone')
    setNewPhone('')
    setOtpCode('')
    setPhoneError('')
    setOtpMode('stub')
  }

  function closePhoneDialog() {
    clearPhoneAuthSession()
    setPhoneDialog(false)
    setPhoneBusy(false)
    setPhoneError('')
  }

  async function submitNewPhone(e) {
    e.preventDefault()
    if (phoneBusy) return
    if (!isValidVietnamPhone(newPhone)) {
      setPhoneError('Vui lòng nhập số điện thoại Việt Nam hợp lệ.')
      return
    }
    setPhoneBusy(true)
    setPhoneError('')
    try {
      const { mode } = await sendPhoneOtp(newPhone)
      setOtpMode(mode)
      setPhoneStep('code')
      setOtpCode('')
    } catch (err) {
      setPhoneError(mapFirebaseAuthError(err))
      clearPhoneAuthSession()
    } finally {
      setPhoneBusy(false)
    }
  }

  async function submitPhoneOtp(e) {
    e.preventDefault()
    if (phoneBusy) return
    if (!/^\d{6}$/.test(otpCode.trim())) {
      setPhoneError('Nhập đủ 6 số OTP.')
      return
    }
    setPhoneBusy(true)
    setPhoneError('')
    try {
      const idToken = await confirmPhoneOtp(otpCode.trim(), newPhone)
      const updated = await profileApi.changePhone(idToken)
      applyUser(updated)
      setSavedForm((prev) => ({ ...prev, phone: updated.phone || '' }))
      setForm((prev) => ({ ...prev, phone: updated.phone || '' }))
      closePhoneDialog()
      setSavedFlash(true)
      window.setTimeout(() => setSavedFlash(false), 2200)
    } catch (err) {
      setPhoneError(err.message || mapFirebaseAuthError(err))
    } finally {
      setPhoneBusy(false)
    }
  }

  return (
    <AccountShell
      title="Tài khoản"
      breadcrumbs={[{ label: 'Trang chủ', to: ROUTES.HOME }, { label: 'Tài khoản' }]}
    >
      <form className="account-card account-card--flush" onSubmit={handleSave}>
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

        <section className="account-section">
          {saveError && (
            <p className="account-note" role="alert">
              {saveError}
            </p>
          )}
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
                  disabled={!isLoggedIn || saving}
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
                  readOnly
                  autoComplete="tel"
                  placeholder="Chưa có số điện thoại"
                />
                <button
                  type="button"
                  className="account-input__action"
                  onClick={openPhoneDialog}
                  disabled={!isLoggedIn || saving}
                >
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
                  disabled={!isLoggedIn || saving}
                />
              </span>
            </label>
          </div>
        </section>

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

        <div className="account-actions">
          {savedFlash && (
            <span className="account-actions__flash" role="status">
              <CheckIcon size={16} /> Đã lưu thay đổi
            </span>
          )}
          <Button type="button" variant="ghost" pill onClick={handleReset} disabled={!isDirty || saving}>
            Hủy thay đổi
          </Button>
          <Button type="submit" variant="primary" pill disabled={!isDirty || !isLoggedIn || saving}>
            <CheckIcon size={18} />
            {saving ? 'Đang lưu…' : 'Lưu thay đổi'}
          </Button>
        </div>
      </form>

      <Modal
        open={phoneDialog}
        onClose={closePhoneDialog}
        title="Đổi số điện thoại"
        size="sm"
      >
        <div id="recaptcha-container" />
        {phoneError && (
          <p className="account-note" role="alert">
            {phoneError}
          </p>
        )}
        {phoneStep === 'phone' ? (
          <form onSubmit={submitNewPhone} className="account-phone-form">
            <label className="account-field account-field--full">
              <span className="account-field__label">Số điện thoại mới</span>
              <span className="account-input">
                <PhoneIcon size={18} />
                <input
                  type="tel"
                  value={newPhone}
                  onChange={(e) => setNewPhone(e.target.value)}
                  placeholder="09xxxxxxxx"
                  autoComplete="tel"
                  disabled={phoneBusy}
                />
              </span>
            </label>
            <p className="account-note">Cần xác minh OTP trước khi đổi số đăng nhập.</p>
            <div className="account-actions">
              <Button type="button" variant="ghost" pill onClick={closePhoneDialog} disabled={phoneBusy}>
                Hủy
              </Button>
              <Button type="submit" variant="primary" pill disabled={phoneBusy}>
                {phoneBusy ? 'Đang gửi…' : 'Nhận mã OTP'}
              </Button>
            </div>
          </form>
        ) : (
          <form onSubmit={submitPhoneOtp} className="account-phone-form">
            <p className="account-note">
              {otpMode === 'stub'
                ? `Stub: nhập bất kỳ 6 số cho ${newPhone}.`
                : `Đã gửi OTP tới ${newPhone}.`}
            </p>
            <label className="account-field account-field--full">
              <span className="account-field__label">Mã OTP</span>
              <span className="account-input">
                <input
                  type="text"
                  inputMode="numeric"
                  maxLength={6}
                  value={otpCode}
                  onChange={(e) => setOtpCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
                  placeholder="6 số"
                  disabled={phoneBusy}
                />
              </span>
            </label>
            <div className="account-actions">
              <Button
                type="button"
                variant="ghost"
                pill
                onClick={() => {
                  clearPhoneAuthSession()
                  setPhoneStep('phone')
                  setOtpCode('')
                  setPhoneError('')
                }}
                disabled={phoneBusy}
              >
                Đổi số khác
              </Button>
              <Button type="submit" variant="primary" pill disabled={phoneBusy}>
                {phoneBusy ? 'Đang xác nhận…' : 'Xác nhận'}
              </Button>
            </div>
          </form>
        )}
      </Modal>
    </AccountShell>
  )
}

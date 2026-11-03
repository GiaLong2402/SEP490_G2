import { useId, useRef, useState } from 'react'
import Button from '@/components/ui/Button'
import { PhoneIcon } from '@/components/icons/Icons'
import { isValidVietnamPhone, formatPhoneDisplay } from '@/utils/phone'
import AuthProgress from './AuthProgress'
import OtpCodeForm from './OtpCodeForm'

const TOTAL_STEPS = 4

/**
 * Flow Đổi SĐT — 4 bước trong cùng 1 card
 * 1. Nhập số đang dùng
 * 2. OTP xác nhận số hiện tại
 * 3. Nhập số mới
 * 4. OTP xác nhận số mới
 */
export default function ChangePhoneFlow({
  onBackToLogin,
  onLostPhone,
  onFinished,
}) {
  const [stepIndex, setStepIndex] = useState(1) // 1 → 4
  const [currentPhone, setCurrentPhone] = useState('')
  const [newPhone, setNewPhone] = useState('')
  const [error, setError] = useState('')
  const [sending, setSending] = useState(false)

  const currentPhoneId = useId()
  const newPhoneId = useId()
  const currentInputRef = useRef(null)
  const newInputRef = useRef(null)

  function handleDigitsChange(setter) {
    return (e) => {
      const digits = e.target.value.replace(/\D/g, '').slice(0, 10)
      setter(digits)
      setError('')
    }
  }

  async function requestOtpThenGo(nextStep) {
    setSending(true)
    try {
      // Demo: giả lập gửi OTP
      await new Promise((resolve) => window.setTimeout(resolve, 600))
      setStepIndex(nextStep)
      setError('')
    } finally {
      setSending(false)
    }
  }

  async function handleSubmitCurrentPhone(e) {
    e.preventDefault()
    if (!isValidVietnamPhone(currentPhone)) {
      setError('Vui lòng nhập số điện thoại Việt Nam hợp lệ.')
      currentInputRef.current?.focus()
      return
    }
    await requestOtpThenGo(2)
  }

  async function handleSubmitNewPhone(e) {
    e.preventDefault()
    if (!isValidVietnamPhone(newPhone)) {
      setError('Vui lòng nhập số điện thoại Việt Nam hợp lệ.')
      newInputRef.current?.focus()
      return
    }
    if (newPhone.replace(/\D/g, '') === currentPhone.replace(/\D/g, '')) {
      setError('Số mới phải khác số đang dùng.')
      newInputRef.current?.focus()
      return
    }
    await requestOtpThenGo(4)
  }

  function handleCurrentOtpOk() {
    setStepIndex(3)
    setError('')
    window.setTimeout(() => newInputRef.current?.focus(), 0)
  }

  function handleNewOtpOk() {
    // Demo: đổi số xong → về đăng nhập với số mới
    onFinished(newPhone)
  }

  return (
    <div className="auth-step auth-step--change">
      <button type="button" className="auth-back" onClick={onBackToLogin}>
        <span className="auth-back__arrow" aria-hidden="true">
          ←
        </span>
        Quay lại đăng nhập
      </button>

      <AuthProgress current={stepIndex} total={TOTAL_STEPS} />

      {/* ---- Bước 1: số đang dùng ---- */}
      {stepIndex === 1 && (
        <>
          <h2 id="auth-card-title" className="auth-change__title">
            Đổi số điện thoại
          </h2>
          <p className="auth-change__lead">Nhập số bạn đang dùng để đăng nhập.</p>

          <form className="auth-form" onSubmit={handleSubmitCurrentPhone} noValidate>
            <PhoneInputBlock
              id={currentPhoneId}
              inputRef={currentInputRef}
              value={currentPhone}
              onChange={handleDigitsChange(setCurrentPhone)}
              error={error}
            />
            <Button
              type="submit"
              variant="secondary"
              fullWidth
              className="auth-submit"
              disabled={sending}
            >
              {sending ? 'Đang gửi...' : 'Nhận mã OTP'}
            </Button>
          </form>

          <button type="button" className="auth-recovery" onClick={onLostPhone}>
            Mất số điện thoại cũ?
          </button>
        </>
      )}

      {/* ---- Bước 2: OTP số hiện tại ---- */}
      {stepIndex === 2 && (
        <>
          <h2 id="auth-card-title" className="auth-change__title">
            Xác nhận số hiện tại
          </h2>
          <p className="auth-change__lead">
            Mã đã được gửi về số <strong>{formatPhoneDisplay(currentPhone)}</strong>.
          </p>
          <button type="button" className="auth-step__link" onClick={() => setStepIndex(1)}>
            Nhập lại số
          </button>
          <OtpCodeForm resetKey={2} onConfirm={handleCurrentOtpOk} />
        </>
      )}

      {/* ---- Bước 3: số mới ---- */}
      {stepIndex === 3 && (
        <>
          <h2 id="auth-card-title" className="auth-change__title">
            Nhập số mới
          </h2>
          <p className="auth-change__lead">Số mới sẽ dùng để đăng nhập từ lần sau.</p>

          <form className="auth-form" onSubmit={handleSubmitNewPhone} noValidate>
            <PhoneInputBlock
              id={newPhoneId}
              inputRef={newInputRef}
              value={newPhone}
              onChange={handleDigitsChange(setNewPhone)}
              error={error}
            />
            <Button
              type="submit"
              variant="secondary"
              fullWidth
              className="auth-submit"
              disabled={sending}
            >
              {sending ? 'Đang gửi...' : 'Nhận mã OTP'}
            </Button>
          </form>
        </>
      )}

      {/* ---- Bước 4: OTP số mới ---- */}
      {stepIndex === 4 && (
        <>
          <h2 id="auth-card-title" className="auth-change__title">
            Xác nhận số mới
          </h2>
          <p className="auth-change__lead">
            Mã đã được gửi về số <strong>{formatPhoneDisplay(newPhone)}</strong>.
          </p>
          <button type="button" className="auth-step__link" onClick={() => setStepIndex(3)}>
            Nhập lại số mới
          </button>
          <OtpCodeForm resetKey={4} onConfirm={handleNewOtpOk} />
        </>
      )}
    </div>
  )
}

/** Ô nhập SĐT +84 — copy layout giống form đăng nhập cho dễ nhận ra */
function PhoneInputBlock({ id, inputRef, value, onChange, error }) {
  return (
    <div className="auth-field">
      <div className="auth-field__label-row">
        <label htmlFor={id}>Số điện thoại</label>
        <span className="auth-field__helper">Định dạng +84</span>
      </div>
      <div className="auth-phone">
        <span className="auth-phone__icon" aria-hidden="true">
          <PhoneIcon size={22} />
        </span>
        <span className="auth-phone__prefix">+84</span>
        <span className="auth-phone__divider" aria-hidden="true" />
        <input
          ref={inputRef}
          id={id}
          type="tel"
          inputMode="numeric"
          autoComplete="tel-national"
          placeholder="Nhập số điện thoại"
          value={value}
          onChange={onChange}
          aria-invalid={Boolean(error)}
          aria-describedby={error ? `${id}-error` : undefined}
        />
      </div>
      {error && (
        <p id={`${id}-error`} className="auth-field__error" role="alert">
          {error}
        </p>
      )}
    </div>
  )
}

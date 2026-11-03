import { useEffect, useRef, useState } from 'react'
import Button from '@/components/ui/Button'

const OTP_LENGTH = 6
const RESEND_SECONDS = 45

/**
 * Form nhập OTP 6 ô — dùng chung cho đăng nhập và đổi SĐT
 * Demo: đủ 6 số là xác nhận được (chưa nối API).
 */
export default function OtpCodeForm({ onConfirm, resetKey = 0 }) {
  const [digits, setDigits] = useState(Array(OTP_LENGTH).fill(''))
  const [error, setError] = useState('')
  const [secondsLeft, setSecondsLeft] = useState(RESEND_SECONDS)
  const inputsRef = useRef([])

  // Mỗi lần đổi resetKey (sang bước OTP mới) → xóa mã + reset đếm ngược
  useEffect(() => {
    setDigits(Array(OTP_LENGTH).fill(''))
    setError('')
    setSecondsLeft(RESEND_SECONDS)
    window.setTimeout(() => inputsRef.current[0]?.focus(), 0)
  }, [resetKey])

  useEffect(() => {
    if (secondsLeft <= 0) return undefined
    const timer = window.setInterval(() => {
      setSecondsLeft((s) => (s <= 1 ? 0 : s - 1))
    }, 1000)
    return () => window.clearInterval(timer)
  }, [secondsLeft])

  function focusIndex(index) {
    inputsRef.current[index]?.focus()
  }

  function handleChange(index, value) {
    const onlyDigit = value.replace(/\D/g, '').slice(-1)
    const next = [...digits]
    next[index] = onlyDigit
    setDigits(next)
    setError('')
    if (onlyDigit && index < OTP_LENGTH - 1) focusIndex(index + 1)
  }

  function handleKeyDown(index, e) {
    if (e.key === 'Backspace' && !digits[index] && index > 0) {
      focusIndex(index - 1)
    }
  }

  function handlePaste(e) {
    e.preventDefault()
    const pasted = e.clipboardData.getData('text').replace(/\D/g, '').slice(0, OTP_LENGTH)
    if (!pasted) return
    const next = Array(OTP_LENGTH).fill('')
    pasted.split('').forEach((ch, i) => {
      next[i] = ch
    })
    setDigits(next)
    focusIndex(Math.min(pasted.length, OTP_LENGTH - 1))
  }

  function handleSubmit(e) {
    e.preventDefault()
    const code = digits.join('')
    if (code.length !== OTP_LENGTH) {
      setError('Vui lòng nhập đủ 6 số OTP.')
      return
    }
    onConfirm(code)
  }

  function handleResend() {
    if (secondsLeft > 0) return
    setDigits(Array(OTP_LENGTH).fill(''))
    setError('')
    setSecondsLeft(RESEND_SECONDS)
    focusIndex(0)
  }

  const mm = String(Math.floor(secondsLeft / 60)).padStart(2, '0')
  const ss = String(secondsLeft % 60).padStart(2, '0')

  return (
    <>
      <form className="auth-otp-form" onSubmit={handleSubmit}>
        <div className="auth-otp-inputs" onPaste={handlePaste}>
          {digits.map((digit, index) => (
            <input
              key={index}
              ref={(el) => {
                inputsRef.current[index] = el
              }}
              className={`auth-otp-inputs__box ${digit ? 'is-filled' : ''}`}
              type="text"
              inputMode="numeric"
              autoComplete={index === 0 ? 'one-time-code' : 'off'}
              maxLength={1}
              value={digit}
              aria-label={`Số OTP thứ ${index + 1}`}
              onChange={(e) => handleChange(index, e.target.value)}
              onKeyDown={(e) => handleKeyDown(index, e)}
            />
          ))}
        </div>

        {error && (
          <p className="auth-field__error" role="alert">
            {error}
          </p>
        )}

        <Button type="submit" variant="secondary" fullWidth className="auth-submit">
          Xác nhận
        </Button>
      </form>

      <p className="auth-otp-resend">
        {secondsLeft > 0 ? (
          <span>
            Nhận lại mã ({mm}:{ss})
          </span>
        ) : (
          <button type="button" className="auth-step__link" onClick={handleResend}>
            Nhận lại mã
          </button>
        )}
      </p>
    </>
  )
}

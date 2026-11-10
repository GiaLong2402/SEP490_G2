import { useId, useRef, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import Button from '@/components/ui/Button'
import { ROLE_LABELS } from '@/constants/roles'
import { ROUTES } from '@/constants/routes'
import { ApiError } from '@/api/client'
import {
  clearPhoneAuthSession,
  confirmPhoneOtp,
  mapFirebaseAuthError,
  sendPhoneOtp,
} from '@/auth/phoneAuth'
import { buildStubIdToken } from '@/auth/stubIdToken'
import { useAuth } from '@/context/AuthContext'
import { getHomePathForRole } from '@/auth/roleUtils'
import { isValidVietnamPhone } from '@/utils/phone'
import authSeaBoat from '@/assets/banners/auth-sea-boat.png'
import waveTealSimple from '@/assets/decorations/wave-teal-simple.png'
import PhoneLoginForm from './PhoneLoginForm'
import OtpVerifyForm from './OtpVerifyForm'
import CreateAccountPanel from './CreateAccountPanel'
import LostPhonePanel from './LostPhonePanel'
import ChangePhoneFlow from './ChangePhoneFlow'
import CompleteProfileForm from './CompleteProfileForm'
import './LoginPage.css'

/** Các bước trên card đăng nhập */
const STEP = {
  PHONE: 'phone',
  OTP: 'otp',
  CREATE: 'create',
  PROFILE: 'profile',
  LOST_PHONE: 'lost-phone',
  CHANGE_PHONE: 'change-phone',
}

export default function LoginPage() {
  const { login, loginWithOtp, registerWithOtp, isLoggedIn, role } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const redirectTo = location.state?.from || null

  const phoneInputRef = useRef(null)
  const phoneId = useId()
  const keepId = useId()

  // --- state form SĐT ---
  const [step, setStep] = useState(STEP.PHONE)
  const [phone, setPhone] = useState('')
  const [keepLoggedIn, setKeepLoggedIn] = useState(false)
  const [error, setError] = useState('')
  const [status, setStatus] = useState('idle') // idle | sending | error
  const [otpMode, setOtpMode] = useState('stub') // stub | firebase
  const [otpSubmitting, setOtpSubmitting] = useState(false)
  const [otpError, setOtpError] = useState('')
  const [pendingIdToken, setPendingIdToken] = useState(null)
  const [creating, setCreating] = useState(false)
  const [infoMessage, setInfoMessage] = useState('')

  // --- đăng nhập nội bộ qua API (username / password) ---
  const [showStaffDemo, setShowStaffDemo] = useState(false)
  const [staffUsername, setStaffUsername] = useState('admin')
  const [staffPassword, setStaffPassword] = useState('')
  const [staffError, setStaffError] = useState('')
  const [staffStatus, setStaffStatus] = useState('idle') // idle | sending | error

  function focusPhoneInput() {
    phoneInputRef.current?.focus()
    phoneInputRef.current?.select()
  }

  function goBackToPhone() {
    clearPhoneAuthSession()
    setStep(STEP.PHONE)
    setStatus('idle')
    setError('')
    setOtpError('')
    setOtpSubmitting(false)
    setPendingIdToken(null)
    setOtpMode('stub')
    setCreating(false)
    window.setTimeout(() => focusPhoneInput(), 0)
  }

  function handleChangePhoneFinished(newPhoneDigits) {
    clearPhoneAuthSession()
    setPhone(newPhoneDigits)
    setInfoMessage('Đã đổi số thành công. Hãy đăng nhập bằng số mới.')
    setStep(STEP.PHONE)
    setStatus('idle')
    setError('')
    setPendingIdToken(null)
    window.setTimeout(() => focusPhoneInput(), 0)
  }

  function handleChangePhone(e) {
    const digits = e.target.value.replace(/\D/g, '').slice(0, 10)
    setPhone(digits)
    if (error) setError('')
    if (infoMessage) setInfoMessage('')
    if (status === 'error') setStatus('idle')
  }

  async function handleRequestOtp(e) {
    e.preventDefault()
    if (status === 'sending') return

    if (!isValidVietnamPhone(phone)) {
      setError('Vui lòng nhập số điện thoại Việt Nam hợp lệ.')
      setStatus('error')
      focusPhoneInput()
      return
    }

    setError('')
    setOtpError('')
    setPendingIdToken(null)
    setStatus('sending')

    try {
      const { mode } = await sendPhoneOtp(phone)
      setOtpMode(mode)
      setStatus('idle')
      setStep(STEP.OTP)
    } catch (err) {
      setStatus('error')
      setError(mapFirebaseAuthError(err) || 'Không gửi được mã. Vui lòng thử lại.')
      clearPhoneAuthSession()
    }
  }

  async function handleOtpVerified(code) {
    setOtpError('')
    setOtpSubmitting(true)
    try {
      const idToken = await confirmPhoneOtp(code, phone)
      try {
        const loggedIn = await loginWithOtp({ idToken })
        clearPhoneAuthSession()
        navigate(redirectTo || getHomePathForRole(loggedIn.role), { replace: true })
      } catch (err) {
        // Chỉ "chưa cấp tài khoản" mới sang đăng ký — tránh 403 khóa/lỗi khác bị nhầm
        if (
          err instanceof ApiError &&
          err.status === 403 &&
          String(err.message || '').includes('chưa được cấp')
        ) {
          setPendingIdToken(idToken)
          setStep(STEP.CREATE)
          return
        }
        throw err
      }
    } catch (err) {
      setOtpError(
        err instanceof ApiError
          ? err.message
          : mapFirebaseAuthError(err) || 'Xác thực thất bại. Thử lại.',
      )
    } finally {
      setOtpSubmitting(false)
    }
  }

  function handleCreateAccount() {
    setStep(STEP.PROFILE)
  }

  async function handleCompleteProfile(profile) {
    setCreating(true)
    try {
      let idToken = pendingIdToken
      if (!idToken) {
        idToken = otpMode === 'firebase' ? null : buildStubIdToken(phone)
      }
      if (!idToken) {
        throw new Error('Phiên OTP hết hạn. Quay lại nhập số và nhận mã mới.')
      }
      const loggedIn = await registerWithOtp({
        idToken,
        fullName: profile.fullName,
        email: profile.email || undefined,
      })
      clearPhoneAuthSession()
      navigate(redirectTo || getHomePathForRole(loggedIn.role) || ROUTES.HOME, { replace: true })
    } catch (err) {
      throw err instanceof ApiError ? err : new Error(err?.message || 'Không tạo được tài khoản. Thử lại.')
    } finally {
      setCreating(false)
    }
  }

  async function handleStaffLogin(e) {
    e.preventDefault()
    if (staffStatus === 'sending') return
    if (!staffUsername.trim() || !staffPassword) {
      setStaffError('Nhập tên đăng nhập và mật khẩu.')
      setStaffStatus('error')
      return
    }
    setStaffError('')
    setStaffStatus('sending')
    try {
      const loggedIn = await login({
        username: staffUsername.trim(),
        password: staffPassword,
      })
      navigate(redirectTo || getHomePathForRole(loggedIn.role), { replace: true })
    } catch (err) {
      setStaffStatus('error')
      setStaffError(err instanceof ApiError ? err.message : 'Đăng nhập thất bại. Thử lại.')
    }
  }

  if (isLoggedIn) {
    return (
      <div className="auth-page auth-page--simple">
        <div className="auth-card auth-card--center">
          <h1>Bạn đã đăng nhập</h1>
          <p>Vai trò hiện tại: {ROLE_LABELS[role]}</p>
          <Button variant="secondary" pill onClick={() => navigate(getHomePathForRole(role))}>
            Tiếp tục
          </Button>
        </div>
      </div>
    )
  }

  return (
    <div className="auth-page">
      <img className="auth-page__bg" src={authSeaBoat} alt="" />
      {/* Firebase invisible reCAPTCHA mount point */}
      <div id="recaptcha-container" />

      <div className="auth-page__inner">
        <section className="auth-brand" aria-label="Giới thiệu Hòa Bình">
          <div className="auth-brand__smoke" aria-hidden="true" />
          <div className="auth-brand__content">
            <p className="auth-brand__eyebrow">
              HẢI SẢN HÒA BÌNH
              <span className="auth-brand__eyebrow-line" aria-hidden="true" />
            </p>
            <h1 className="auth-brand__title">
              <span>Tươi từ biển,</span>
              <span className="auth-brand__title-line">
                <span className="auth-brand__ngon">
                  ngon
                  <img
                    className="auth-brand__wave-mark"
                    src={waveTealSimple}
                    alt=""
                    aria-hidden="true"
                  />
                </span>
                {' '}
                đến bàn ăn
              </span>
            </h1>
            <p className="auth-brand__desc">
              Chăm chút từ sơ chế đến đóng khay,
              <br />
              tiện lợi cho bữa ăn gia đình.
            </p>
          </div>
        </section>

        <section className="auth-card" aria-labelledby="auth-card-title">
          {step === STEP.PHONE && (
            <PhoneLoginForm
              phoneId={phoneId}
              keepId={keepId}
              phone={phone}
              keepLoggedIn={keepLoggedIn}
              error={error}
              infoMessage={infoMessage}
              status={status}
              phoneInputRef={phoneInputRef}
              onChangePhone={handleChangePhone}
              onChangeKeepLoggedIn={(e) => setKeepLoggedIn(e.target.checked)}
              onChangePhoneClick={() => {
                setInfoMessage('')
                setStep(STEP.CHANGE_PHONE)
              }}
              onLostPhoneClick={() => setStep(STEP.LOST_PHONE)}
              onSubmit={handleRequestOtp}
            />
          )}

          {step === STEP.OTP && (
            <OtpVerifyForm
              phone={phone}
              otpMode={otpMode}
              onBackToPhone={goBackToPhone}
              onVerified={handleOtpVerified}
              submitting={otpSubmitting}
              error={otpError}
            />
          )}

          {step === STEP.CREATE && (
            <CreateAccountPanel
              phone={phone}
              creating={false}
              onCreateAccount={handleCreateAccount}
              onUseOtherPhone={goBackToPhone}
            />
          )}

          {step === STEP.PROFILE && (
            <CompleteProfileForm
              phone={phone}
              submitting={creating}
              onComplete={handleCompleteProfile}
            />
          )}

          {step === STEP.LOST_PHONE && (
            <LostPhonePanel onBackToLogin={goBackToPhone} />
          )}

          {step === STEP.CHANGE_PHONE && (
            <ChangePhoneFlow
              onBackToLogin={goBackToPhone}
              onLostPhone={() => setStep(STEP.LOST_PHONE)}
              onFinished={handleChangePhoneFinished}
            />
          )}

          {step === STEP.PHONE && (
            <div className="auth-staff-demo">
              <button
                type="button"
                className="auth-staff-demo__toggle"
                aria-expanded={showStaffDemo}
                onClick={() => setShowStaffDemo((v) => !v)}
              >
                Đăng nhập nội bộ (API)
              </button>
              {showStaffDemo && (
                <form className="auth-staff-demo__form" onSubmit={handleStaffLogin}>
                  <label className="auth-staff-demo__field">
                    <span>Tên đăng nhập</span>
                    <input
                      value={staffUsername}
                      onChange={(e) => {
                        setStaffUsername(e.target.value)
                        if (staffError) setStaffError('')
                      }}
                      autoComplete="username"
                      placeholder="admin"
                    />
                  </label>
                  <label className="auth-staff-demo__field">
                    <span>Mật khẩu</span>
                    <input
                      type="password"
                      value={staffPassword}
                      onChange={(e) => {
                        setStaffPassword(e.target.value)
                        if (staffError) setStaffError('')
                      }}
                      autoComplete="current-password"
                      placeholder="SEED_ADMIN_PASSWORD"
                    />
                  </label>
                  {staffError ? <p className="auth-staff-demo__error">{staffError}</p> : null}
                  <Button type="submit" variant="navy" fullWidth disabled={staffStatus === 'sending'}>
                    {staffStatus === 'sending' ? 'Đang đăng nhập…' : 'Đăng nhập API'}
                  </Button>
                </form>
              )}
            </div>
          )}
        </section>
      </div>
    </div>
  )
}

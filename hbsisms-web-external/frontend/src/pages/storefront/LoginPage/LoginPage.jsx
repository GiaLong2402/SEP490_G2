import { useId, useRef, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import Button from '@/components/ui/Button'
import { ROLES, ROLE_LABELS } from '@/constants/roles'
import { ROUTES } from '@/constants/routes'
import { ApiError } from '@/api/client'
import { useAuth } from '@/context/AuthContext'
import { getHomePathForRole } from '@/auth/roleUtils'
import { isValidVietnamPhone, normalizeVietnamPhone } from '@/utils/phone'
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
  const { login, loginDemo, isLoggedIn, role } = useAuth()
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
    setStep(STEP.PHONE)
    setStatus('idle')
    setError('')
    setCreating(false)
    // Đợi render xong rồi focus ô SĐT
    window.setTimeout(() => focusPhoneInput(), 0)
  }

  function handleChangePhoneFinished(newPhoneDigits) {
    setPhone(newPhoneDigits)
    setInfoMessage('Đã đổi số thành công. Hãy đăng nhập bằng số mới.')
    setStep(STEP.PHONE)
    setStatus('idle')
    setError('')
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
    setStatus('sending')

    // Demo: giả lập gọi API gửi OTP — sau này thay bằng Spring Boot
    try {
      await new Promise((resolve) => window.setTimeout(resolve, 700))
      setStatus('idle')
      setStep(STEP.OTP)
    } catch {
      setStatus('error')
      setError('Không gửi được mã. Vui lòng thử lại.')
    }
  }

  function handleOtpVerified() {
    // Demo: luôn coi số mới → hiện khung tạo tài khoản (ảnh 3)
    setStep(STEP.CREATE)
  }

  function handleCreateAccount() {
    // Sang form hoàn tất hồ sơ (chưa login ngay)
    setStep(STEP.PROFILE)
  }

  async function handleCompleteProfile(profile) {
    setCreating(true)
    try {
      await new Promise((resolve) => window.setTimeout(resolve, 500))
      const local = normalizeVietnamPhone(phone)
      loginDemo({
        name: profile.fullName,
        role: ROLES.CUSTOMER,
        phone: `+84${local}`,
        keepLoggedIn,
        // Demo: giữ địa chỉ/email trên user sau này khi nối API
        email: profile.email || null,
        address: profile.address,
      })
      navigate(redirectTo || ROUTES.HOME, { replace: true })
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

      <div className="auth-page__inner">
        {/* Cột trái — brand */}
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

        {/* Cột phải — card đổi nội dung theo bước */}
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
              onBackToPhone={goBackToPhone}
              onVerified={handleOtpVerified}
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

          {/* Đăng nhập nội bộ qua API — chỉ hiện ở bước nhập SĐT */}
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

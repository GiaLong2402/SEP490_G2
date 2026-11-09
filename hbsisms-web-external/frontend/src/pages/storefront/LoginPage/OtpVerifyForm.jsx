import { formatPhoneDisplay } from '@/utils/phone'
import OtpCodeForm from './OtpCodeForm'

/**
 * Bước 2 đăng nhập: nhập mã OTP 6 số.
 * @param {'stub'|'firebase'} otpMode
 */
export default function OtpVerifyForm({
  phone,
  otpMode = 'stub',
  onBackToPhone,
  onVerified,
  submitting = false,
  error = '',
}) {
  const lead =
    otpMode === 'firebase'
      ? <>Mã đã được gửi về số <strong>{formatPhoneDisplay(phone)}</strong>.</>
      : <>Phase 1 (stub): nhập bất kỳ 6 số cho số <strong>{formatPhoneDisplay(phone)}</strong>.</>

  return (
    <div className="auth-step auth-step--otp">
      <h2 id="auth-card-title">Nhập mã xác thực</h2>
      <p className="auth-card__lead">{lead}</p>

      <button type="button" className="auth-step__link" onClick={onBackToPhone} disabled={submitting}>
        Nhập lại số
      </button>

      <OtpCodeForm
        resetKey="login-otp"
        onConfirm={onVerified}
        submitting={submitting}
        externalError={error}
      />
    </div>
  )
}

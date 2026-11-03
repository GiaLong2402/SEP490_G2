import { formatPhoneDisplay } from '@/utils/phone'
import OtpCodeForm from './OtpCodeForm'

/**
 * Bước 2 đăng nhập: nhập mã OTP 6 số → Xác nhận
 */
export default function OtpVerifyForm({ phone, onBackToPhone, onVerified }) {
  return (
    <div className="auth-step auth-step--otp">
      <h2 id="auth-card-title">Nhập mã xác thực</h2>
      <p className="auth-card__lead">
        Mã đã được gửi về số <strong>{formatPhoneDisplay(phone)}</strong>.
      </p>

      <button type="button" className="auth-step__link" onClick={onBackToPhone}>
        Nhập lại số
      </button>

      <OtpCodeForm resetKey="login-otp" onConfirm={onVerified} />
    </div>
  )
}

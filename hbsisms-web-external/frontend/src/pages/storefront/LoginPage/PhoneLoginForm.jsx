import { Link } from 'react-router-dom'
import Button from '@/components/ui/Button'
import { ArrowRightIcon, PhoneIcon } from '@/components/icons/Icons'
import { ROUTES } from '@/constants/routes'

/**
 * Bước 1: nhập số điện thoại → Nhận mã OTP
 * (tooltip dấu ? dùng CSS hover — xem LoginPage.css)
 */
export default function PhoneLoginForm({
  phoneId,
  keepId,
  phone,
  keepLoggedIn,
  error,
  infoMessage,
  status,
  phoneInputRef,
  onChangePhone,
  onChangeKeepLoggedIn,
  onChangePhoneClick,
  onLostPhoneClick,
  onSubmit,
}) {
  return (
    <>
      <h2 id="auth-card-title">Đăng nhập / Đăng ký</h2>
      <p className="auth-card__lead">
        Nhập số điện thoại để tiếp tục mua hàng tại Hòa Bình.
      </p>

      {infoMessage && (
        <p className="auth-info" role="status">
          {infoMessage}
        </p>
      )}

      <form className="auth-form" onSubmit={onSubmit} noValidate>
        <div className="auth-field">
          <div className="auth-field__label-row">
            <label htmlFor={phoneId}>Số điện thoại</label>
            <span className="auth-field__helper">Định dạng +84</span>
          </div>

          <div className="auth-phone">
            <span className="auth-phone__icon" aria-hidden="true">
              <PhoneIcon size={22} />
            </span>
            <span className="auth-phone__prefix">+84</span>
            <span className="auth-phone__divider" aria-hidden="true" />
            <input
              ref={phoneInputRef}
              id={phoneId}
              type="tel"
              inputMode="numeric"
              autoComplete="tel-national"
              placeholder="Nhập số điện thoại"
              value={phone}
              onChange={onChangePhone}
              aria-invalid={Boolean(error)}
              aria-describedby={error ? `${phoneId}-error` : undefined}
            />
          </div>
          {error && (
            <p id={`${phoneId}-error`} className="auth-field__error" role="alert">
              {error}
            </p>
          )}
        </div>

        <div className="auth-options">
          <label className="auth-keep" htmlFor={keepId}>
            <input
              id={keepId}
              type="checkbox"
              checked={keepLoggedIn}
              onChange={onChangeKeepLoggedIn}
            />
            <span>Duy trì đăng nhập</span>

            {/* Dấu ? — hover hiện tooltip; preventDefault để không bật/tắt checkbox */}
            <span
              className="auth-keep__tip"
              onClick={(e) => e.preventDefault()}
              onKeyDown={(e) => e.stopPropagation()}
            >
              <span className="auth-keep__info" tabIndex={0} aria-describedby={`${keepId}-tip`}>
                <span aria-hidden="true">?</span>
              </span>
              <span id={`${keepId}-tip`} className="auth-keep__tooltip" role="tooltip">
                Duy trì đăng nhập kể cả khi đóng trình duyệt. Không nên dùng trên
                thiết bị dùng chung hoặc công cộng.
              </span>
            </span>
          </label>

          <button type="button" className="auth-change-phone" onClick={onChangePhoneClick}>
            Đổi SĐT
          </button>
        </div>

        <Button
          type="submit"
          variant="secondary"
          fullWidth
          className="auth-submit"
          disabled={status === 'sending'}
        >
          {status === 'sending' ? 'Đang gửi...' : 'Nhận mã OTP'}
          {status !== 'sending' && <ArrowRightIcon size={22} />}
        </Button>
      </form>

      <button type="button" className="auth-recovery" onClick={onLostPhoneClick}>
        Mất số điện thoại cũ?
      </button>

      <p className="auth-terms">
        Bằng việc tiếp tục, bạn đồng ý với{' '}
        <Link to={ROUTES.CONTACT}>Điều khoản dịch vụ</Link>
        {' '}và{' '}
        <Link to={ROUTES.CONTACT}>Chính sách bảo mật</Link>
        {' '}của Hải sản Hòa Bình.
      </p>
    </>
  )
}

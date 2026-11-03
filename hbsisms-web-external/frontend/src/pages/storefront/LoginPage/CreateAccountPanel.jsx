import Button from '@/components/ui/Button'
import { UserIcon } from '@/components/icons/Icons'
import { formatPhoneDisplay } from '@/utils/phone'

/**
 * Bước 3: số chưa có tài khoản → tạo tài khoản mới
 * (demo: chưa gọi backend, chỉ UI + callback)
 */
export default function CreateAccountPanel({
  phone,
  onCreateAccount,
  onUseOtherPhone,
  creating,
}) {
  return (
    <div className="auth-step auth-step--create">
      <div className="auth-create__avatar" aria-hidden="true">
        <UserIcon size={36} />
      </div>

      <h2 id="auth-card-title">Số này chưa có tài khoản</h2>
      <p className="auth-card__lead">
        Số <strong>{formatPhoneDisplay(phone)}</strong> chưa có tài khoản tại Hải sản
        Hòa Bình. Tạo tài khoản mới với số này?
      </p>

      <div className="auth-create__note">
        <span className="auth-create__check" aria-hidden="true">
          ✓
        </span>
        <p>Bạn đã xác thực số này bằng mã OTP, không cần nhập lại.</p>
      </div>

      <Button
        type="button"
        variant="secondary"
        fullWidth
        className="auth-submit"
        disabled={creating}
        onClick={onCreateAccount}
      >
        {creating ? 'Đang tạo...' : 'Tạo tài khoản'}
      </Button>

      <button type="button" className="auth-step__link auth-create__other" onClick={onUseOtherPhone}>
        Nhập số khác
      </button>
    </div>
  )
}

import Button from '@/components/ui/Button'

const STEPS = [
  {
    title: 'Liên hệ cửa hàng',
    desc: 'Gọi hoặc đến quầy trong giờ mở cửa 06:00 – 19:00.',
  },
  {
    title: 'Cho cửa hàng biết thông tin để xác minh',
    desc: 'Họ tên, địa chỉ nhận hàng và một đơn gần nhất của bạn.',
  },
  {
    title: 'Đăng nhập bằng số mới',
    desc: 'Cửa hàng đổi số xong, bạn đăng nhập lại như bình thường.',
  },
]

/**
 * Màn hướng dẫn khi mất số điện thoại cũ
 * (cùng card trắng với các bước đăng nhập khác)
 */
export default function LostPhonePanel({ onBackToLogin }) {
  return (
    <div className="auth-step auth-step--lost">
      <button type="button" className="auth-back" onClick={onBackToLogin}>
        <span className="auth-back__arrow" aria-hidden="true">
          ←
        </span>
        Quay lại đăng nhập
      </button>

      <h2 id="auth-card-title" className="auth-lost__title">
        Mất số điện thoại cũ?
      </h2>
      <p className="auth-lost__lead">
        Mã OTP chỉ gửi về số bạn đã đăng ký. Nếu không còn dùng số đó, cửa hàng sẽ
        đổi số giúp bạn.
      </p>

      <ol className="auth-lost__steps">
        {STEPS.map((item, index) => (
          <li key={item.title} className="auth-lost__step">
            <span className="auth-lost__badge" aria-hidden="true">
              {index + 1}
            </span>
            <div className="auth-lost__step-body">
              <p className="auth-lost__step-title">{item.title}</p>
              <p className="auth-lost__step-desc">{item.desc}</p>
            </div>
          </li>
        ))}
      </ol>

      <div className="auth-lost__store">
        <p className="auth-lost__store-name">Cửa hàng Hải sản Hòa Bình</p>
        <p>Số điện thoại: 0203 888 999</p>
        <p>Địa chỉ: Móng Cái, Quảng Ninh</p>
      </div>

      <Button
        type="button"
        variant="secondary"
        fullWidth
        className="auth-submit"
        onClick={onBackToLogin}
      >
        Quay lại đăng nhập
      </Button>
    </div>
  )
}

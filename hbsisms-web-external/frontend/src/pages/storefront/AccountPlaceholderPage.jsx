import { Link } from 'react-router-dom'
import { ROUTES } from '@/constants/routes'

/**
 * Trang tạm cho các mục menu tài khoản (sau này thay bằng trang thật)
 */
export default function AccountPlaceholderPage({ title, description }) {
  return (
    <div className="container" style={{ paddingBlock: '48px', maxWidth: 640 }}>
      <h1 style={{ color: 'var(--color-brand)', marginBottom: 12 }}>{title}</h1>
      <p style={{ color: 'var(--color-muted)', marginBottom: 24, lineHeight: 1.6 }}>
        {description || 'Nội dung trang sẽ được bổ sung ở bước sau.'}
      </p>
      <Link
        to={ROUTES.HOME}
        style={{
          display: 'inline-flex',
          alignItems: 'center',
          minHeight: 44,
          padding: '0 20px',
          borderRadius: 8,
          background: 'var(--color-action)',
          color: '#fff',
          fontWeight: 600,
        }}
      >
        Về trang chủ
      </Link>
    </div>
  )
}

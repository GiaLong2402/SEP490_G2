import { Link } from 'react-router-dom'
import { ROUTES } from '@/constants/routes'
import Button from '@/components/ui/Button'

export default function UnauthorizedPage() {
  return (
    <div className="container" style={{ padding: '64px 24px', textAlign: 'center' }}>
      <h1 style={{ color: 'var(--color-brand)' }}>Không có quyền truy cập</h1>
      <p style={{ color: 'var(--color-muted)', margin: '12px 0 24px' }}>
        Tài khoản hiện tại không được phép vào trang này.
      </p>
      <Link to={ROUTES.HOME}>
        <Button variant="navy">Về trang chủ</Button>
      </Link>
    </div>
  )
}

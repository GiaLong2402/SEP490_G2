import { Link } from 'react-router-dom'
import { ROUTES } from '@/constants/routes'
import Button from '@/components/ui/Button'

export default function NotFoundPage() {
  return (
    <div className="container" style={{ padding: '64px 24px', textAlign: 'center' }}>
      <h1 style={{ color: 'var(--color-brand)' }}>Không tìm thấy trang</h1>
      <p style={{ color: 'var(--color-muted)', margin: '12px 0 24px' }}>
        Đường dẫn không tồn tại hoặc đã được đổi tên.
      </p>
      <Link to={ROUTES.HOME}>
        <Button variant="secondary">Về trang chủ</Button>
      </Link>
    </div>
  )
}

import { Navigate } from 'react-router-dom'
import { ROUTES } from '@/constants/routes'

/** Cũ: /tai-khoan/so-dia-chi → gộp vào trang Tài khoản. */
export default function AddressBookPage() {
  return <Navigate to={`${ROUTES.ACCOUNT}#dia-chi`} replace />
}

import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '@/context/AuthContext'
import { hasRole } from '@/auth/roleUtils'
import { ROUTES } from '@/constants/routes'

/**
 * Bọc route cần đăng nhập / đúng role.
 *
 * Cách dùng:
 *   <ProtectedRoute allowedRoles={[ROLES.ADMIN]}>
 *     <AdminPage />
 *   </ProtectedRoute>
 */
export default function ProtectedRoute({ children, allowedRoles = [] }) {
  const { user, isLoggedIn } = useAuth()
  const location = useLocation()

  if (!isLoggedIn) {
    return <Navigate to={ROUTES.LOGIN} replace state={{ from: location.pathname }} />
  }

  if (!hasRole(user.role, allowedRoles)) {
    return <Navigate to={ROUTES.UNAUTHORIZED} replace />
  }

  return children
}

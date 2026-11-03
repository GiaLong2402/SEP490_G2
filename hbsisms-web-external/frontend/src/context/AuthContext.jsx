import { createContext, useContext, useMemo, useState } from 'react'
import { ROLES } from '@/constants/roles'

const AuthContext = createContext(null)

/**
 * Auth đơn giản cho beginner.
 * - Chưa gọi API thật; login demo chọn role để test phân quyền.
 * - Sau này thay loginDemo bằng gọi Spring Boot /api/auth/login.
 */
export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)

  const isLoggedIn = Boolean(user)

  function loginDemo({ name, role, phone, keepLoggedIn, email, address }) {
    setUser({
      id: `demo-${role}`,
      name: name || 'Người dùng demo',
      role,
      phone: phone || null,
      email: email || null,
      address: address || null,
      keepLoggedIn: Boolean(keepLoggedIn),
    })
  }

  function logout() {
    setUser(null)
  }

  const value = useMemo(
    () => ({
      user,
      role: user?.role || ROLES.GUEST,
      isLoggedIn,
      loginDemo,
      logout,
    }),
    [user, isLoggedIn],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) {
    throw new Error('useAuth phải dùng bên trong AuthProvider')
  }
  return ctx
}

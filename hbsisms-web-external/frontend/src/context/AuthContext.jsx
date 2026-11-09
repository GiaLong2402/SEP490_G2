import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { authApi } from '@/api/auth'
import { setUnauthorizedHandler, tokenStore } from '@/api/client'
import { mapApiUser } from '@/auth/mapRole'
import { ROLES } from '@/constants/roles'

const AuthContext = createContext(null)

/**
 * Auth nối Spring Boot JWT.
 * - login(username, password) → POST /auth/login
 * - loginDemo vẫn giữ cho luồng OTP/SĐT (chưa có API phone)
 */
export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  const [loading, setLoading] = useState(() => Boolean(tokenStore.get()))

  const clearSession = useCallback(() => {
    tokenStore.set(null)
    setUser(null)
  }, [])

  useEffect(() => {
    setUnauthorizedHandler(clearSession)
    const token = tokenStore.get()
    if (!token) {
      setLoading(false)
      return
    }
    authApi
      .me()
      .then((me) => setUser(mapApiUser(me)))
      .catch(() => clearSession())
      .finally(() => setLoading(false))
  }, [clearSession])

  const login = useCallback(async ({ username, password }) => {
    const result = await authApi.login({ username, password })
    tokenStore.set(result.token)
    setUser(mapApiUser(result.user))
    return mapApiUser(result.user)
  }, [])

  const loginDemo = useCallback(({ name, role, phone, keepLoggedIn, email, address }) => {
    tokenStore.set(null)
    setUser({
      id: `demo-${role}`,
      name: name || 'Người dùng demo',
      role,
      phone: phone || null,
      email: email || null,
      address: address || null,
      keepLoggedIn: Boolean(keepLoggedIn),
    })
  }, [])

  const logout = useCallback(() => {
    if (tokenStore.get()) {
      authApi.logout().catch(() => {})
    }
    clearSession()
  }, [clearSession])

  const isLoggedIn = Boolean(user)

  const value = useMemo(
    () => ({
      user,
      role: user?.role || ROLES.GUEST,
      isLoggedIn,
      loading,
      login,
      loginDemo,
      logout,
    }),
    [user, isLoggedIn, loading, login, loginDemo, logout],
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

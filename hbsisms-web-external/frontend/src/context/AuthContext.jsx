import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { authApi } from '@/api/auth'
import { setUnauthorizedHandler, tokenStore } from '@/api/client'
import { mapApiUser } from '@/auth/mapRole'
import { ROLES } from '@/constants/roles'

const AuthContext = createContext(null)

/**
 * Auth nối Spring Boot JWT.
 * - login(username, password) → POST /auth/login
 * - loginWithOtp / registerWithOtp → stub hoặc Firebase idToken
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

  const applyLoginResult = useCallback((result) => {
    tokenStore.set(result.token)
    const mapped = mapApiUser(result.user)
    setUser(mapped)
    return mapped
  }, [])

  const login = useCallback(
    async ({ username, password }) => {
      const result = await authApi.login({ username, password })
      return applyLoginResult(result)
    },
    [applyLoginResult],
  )

  const loginWithOtp = useCallback(
    async ({ idToken }) => {
      const result = await authApi.loginOtp({ idToken })
      return applyLoginResult(result)
    },
    [applyLoginResult],
  )

  const registerWithOtp = useCallback(
    async ({ idToken, fullName, email }) => {
      const body = { idToken, fullName }
      if (email) body.email = email
      const result = await authApi.registerOtp(body)
      return applyLoginResult(result)
    },
    [applyLoginResult],
  )

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
      loginWithOtp,
      registerWithOtp,
      logout,
    }),
    [user, isLoggedIn, loading, login, loginWithOtp, registerWithOtp, logout],
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

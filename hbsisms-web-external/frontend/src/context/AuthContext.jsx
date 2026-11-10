import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { authApi } from '@/api/auth'
import { ApiError, setUnauthorizedHandler, tokenStore } from '@/api/client'
import { mapApiUser } from '@/auth/mapRole'
import { isStaff } from '@/auth/roleUtils'
import { ROLES } from '@/constants/roles'

const AuthContext = createContext(null)

/**
 * Auth nối Spring Boot JWT.
 * - login(username, password) → POST /auth/login
 * - loginWithOtp / registerWithOtp → stub hoặc Firebase idToken
 * Web external chỉ cho khách: tài khoản nội bộ (NV, chủ, admin) coi như không tồn tại.
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
      .then((me) => {
        const mapped = mapApiUser(me)
        if (isStaff(mapped.role)) clearSession()
        else setUser(mapped)
      })
      .catch(() => clearSession())
      .finally(() => setLoading(false))
  }, [clearSession])

  const applyLoginResult = useCallback(async (result) => {
    const mapped = mapApiUser(result.user)
    tokenStore.set(result.token)
    if (isStaff(mapped.role)) {
      // Thu hồi token vừa cấp rồi báo như số chưa có tài khoản (404, không phải 403 = "tạo tài khoản")
      await authApi.logout().catch(() => {})
      tokenStore.set(null)
      throw new ApiError(404, 'Tài khoản không tồn tại.')
    }
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

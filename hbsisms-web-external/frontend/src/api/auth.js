import { api } from './client'

export const authApi = {
  login: (body) => api.post('/auth/login', body),
  /** Phase 1 stub / Phase 2 Firebase — POST /auth/login/otp */
  loginOtp: (body) => api.post('/auth/login/otp', body),
  /** Customer self-register — POST /auth/register/otp */
  registerOtp: (body) => api.post('/auth/register/otp', body),
  /** Precheck chống spam OTP — POST /auth/otp/precheck (429 nếu khoá) */
  otpPrecheck: (body) => api.post('/auth/otp/precheck', body),
  me: () => api.get('/auth/me'),
  logout: () => api.post('/auth/logout'),
}

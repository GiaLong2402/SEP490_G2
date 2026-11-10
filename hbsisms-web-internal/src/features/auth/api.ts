import { api } from '@/shared/api/client';
import type { CurrentUser, LoginRequest, LoginResponse, OtpLoginRequest } from './types';

export const authApi = {
  login: (body: LoginRequest) => api.post<LoginResponse>('/auth/login', body),
  loginOtp: (body: OtpLoginRequest) => api.post<LoginResponse>('/auth/login/otp', body),
  me: () => api.get<CurrentUser>('/auth/me'),
  logout: () => api.post<void>('/auth/logout'),
};

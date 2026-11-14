import { api } from '@/shared/api/client';
import { API_PATHS } from '@/shared/api/httpPaths';
import type { CurrentUser, LoginRequest, LoginResponse, OtpLoginRequest } from './types';

export const authApi = {
  login: (body: LoginRequest) => api.post<LoginResponse>(API_PATHS.auth.login, body),
  loginOtp: (body: OtpLoginRequest) => api.post<LoginResponse>('/auth/login/otp', body),
  me: () => api.get<CurrentUser>(API_PATHS.auth.me),
  logout: () => api.post<void>(API_PATHS.auth.logout),
};

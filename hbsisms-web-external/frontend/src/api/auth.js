import { api } from './client'

export const authApi = {
  login: (body) => api.post('/auth/login', body),
  me: () => api.get('/auth/me'),
  logout: () => api.post('/auth/logout'),
}

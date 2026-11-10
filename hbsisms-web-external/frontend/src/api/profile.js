import { api } from './client'
import { mapApiUser } from '@/auth/mapRole'

/**
 * Hồ sơ khách đang đăng nhập — Inter 2 #26.
 * Hợp đồng: API_CONTRACT.md § Hồ sơ.
 */
export const profileApi = {
  async update({ fullName, email }) {
    const data = await api.patch('/me/profile', {
      fullName: fullName.trim(),
      email: email?.trim() ? email.trim() : null,
    })
    return mapApiUser(data)
  },

  async changePhone(idToken) {
    const data = await api.post('/me/phone', { idToken })
    return mapApiUser(data)
  },
}

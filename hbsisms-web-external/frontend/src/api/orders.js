import { api, ApiError } from './client'
import { fromOrderDto } from './mappers/order'

/**
 * Đơn của khách đang đăng nhập — task "Đơn của tôi" + "Khách hủy đơn chưa thanh toán" (Duy).
 * Hợp đồng: API_CONTRACT.md § Đơn hàng. Đơn của người khác → 404.
 */
export const ordersApi = {
  async listMine(signal) {
    try {
      const data = await api.get('/me/orders', { page: 1, pageSize: 100 }, signal)
      return (data?.items || []).map(fromOrderDto)
    } catch (err) {
      // /me/orders chưa nối backend — tránh chặn tab Yêu cầu (/me/sales-requests đã có).
      if (err instanceof ApiError && err.status === 404) return []
      throw err
    }
  },

  async getMine(code, signal) {
    return fromOrderDto(await api.get(`/me/orders/${encodeURIComponent(code)}`, null, signal))
  },

  async cancel(code) {
    return fromOrderDto(await api.post(`/me/orders/${encodeURIComponent(code)}/cancel`))
  },
}

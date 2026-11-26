import { api } from './client'
import { fromOrderDto } from './mappers/order'

/**
 * Đơn của khách đang đăng nhập — task "Đơn của tôi" + "Khách hủy đơn chưa thanh toán" (Duy).
 * Hợp đồng: API_CONTRACT.md § Đơn hàng. Đơn của người khác → 404.
 */
export const ordersApi = {
  async listMine(signal) {
    const data = await api.get('/me/orders', { page: 1, pageSize: 100 }, signal)
    return (data?.items || []).map(fromOrderDto)
  },

  async getMine(code, signal) {
    return fromOrderDto(await api.get(`/me/orders/${encodeURIComponent(code)}`, null, signal))
  },

  async cancel(code) {
    return fromOrderDto(await api.post(`/me/orders/${encodeURIComponent(code)}/cancel`))
  },

  /** Tạo / lấy lại link PayOS đúng số tiền cọc (đơn đặt trước). */
  async createDepositPayosLink(code) {
    const data = await api.post(`/me/orders/${encodeURIComponent(code)}/deposit-payos-link`)
    return {
      payosOrderCode: data.payosOrderCode,
      amount: data.amount != null ? Number(data.amount) : null,
      checkoutUrl: data.checkoutUrl || null,
      qrCode: data.qrCode || null,
      expiresAt: data.expiresAt || null,
    }
  },

  /** Tạo / lấy lại link PayOS tất toán phần còn lại (đơn đặt trước đã đủ hàng). */
  async createBalancePayosLink(code) {
    const data = await api.post(`/me/orders/${encodeURIComponent(code)}/balance-payos-link`)
    return {
      payosOrderCode: data.payosOrderCode,
      amount: data.amount != null ? Number(data.amount) : null,
      checkoutUrl: data.checkoutUrl || null,
      qrCode: data.qrCode || null,
      expiresAt: data.expiresAt || null,
    }
  },
}

import { api } from './client'
import { checkDto } from './mappers/checkDto'
import { fromOrderDto } from './mappers/order'

/**
 * Thanh toán PayOS phía khách — task "Trạng thái khi khách quay về" + tạo lại link (Long).
 * Hợp đồng: API_CONTRACT.md § PayOS.
 * status: PENDING | PAID | CANCELLED | FAILED | EXPIRED
 */
export const paymentsApi = {
  /** Trang returnUrl gọi API này — KHÔNG tin status trên URL PayOS trả về */
  async getReturnStatus(payosOrderCode, signal) {
    const data = await api.get('/payments/payos/return', { orderCode: payosOrderCode }, signal)
    checkDto(data, ['status', 'amount', 'order'], 'GET /payments/payos/return', '5. PayOS')
    return { ...data, order: data.order ? fromOrderDto(data.order) : null }
  },

  /** Tạo lại link thanh toán (đơn còn giữ hàng). Trả { checkoutUrl, qrCode, amount, expiresAt, payosOrderCode } */
  async recreateLink(payosOrderCode) {
    const data = await api.post(`/payments/payos/${payosOrderCode}/link`)
    checkDto(data, ['checkoutUrl', 'expiresAt'], 'POST /payments/payos/{orderCode}/link', '5. PayOS')
    return { ...data, order: data.order ? fromOrderDto(data.order) : null }
  },
}

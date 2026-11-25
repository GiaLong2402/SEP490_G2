import { api } from './client'
import { fromSalesRequestDto } from './mappers/salesRequest'

/**
 * Yêu cầu mua số lượng lớn / vượt tồn của khách — task "Gửi yêu cầu mua vượt tồn" (Duy).
 * Hợp đồng: API_CONTRACT.md § Yêu cầu.
 */
export const salesRequestsApi = {
  async listMine(signal) {
    const data = await api.get('/me/sales-requests', { page: 1, pageSize: 100 }, signal)
    return (data?.items || []).map(fromSalesRequestDto)
  },

  async getMine(code, signal) {
    return fromSalesRequestDto(await api.get(`/me/sales-requests/${encodeURIComponent(code)}`, null, signal))
  },

  /**
   * input: {
   *   summary?, neededDate?, requestedQty?, availableQty?, contactPhone?,
   *   lines?: [{ variantId, unit: 'pack'|'case', qty }]
   * }
   */
  async create(input) {
    return fromSalesRequestDto(await api.post('/me/sales-requests', { channel: 'WEB', ...input }))
  },

  /** Khách đồng ý báo giá → tạo đơn. */
  async accept(code) {
    return fromSalesRequestDto(await api.post(`/me/sales-requests/${encodeURIComponent(code)}/accept`))
  },

  /** Khách từ chối báo giá — bắt buộc lý do. */
  async reject(code, rejectReason) {
    return fromSalesRequestDto(
      await api.post(`/me/sales-requests/${encodeURIComponent(code)}/reject`, { rejectReason }),
    )
  },
}

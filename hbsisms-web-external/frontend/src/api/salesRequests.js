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
   * input: { summary, neededDate?, requestedQty?, availableQty?, items?: [{ variantId, requestedQty, availableQty }] }
   */
  async create(input) {
    return fromSalesRequestDto(await api.post('/me/sales-requests', { channel: 'WEB', ...input }))
  },
}

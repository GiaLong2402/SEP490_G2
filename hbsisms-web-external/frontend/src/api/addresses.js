import { api } from './client'
import { fromAddressDto, toAddressRequest } from './mappers/address'

/**
 * Sổ địa chỉ của khách đang đăng nhập — task "Hồ sơ & địa chỉ của tôi" (Duy).
 * Hợp đồng: API_CONTRACT.md § Địa chỉ.
 */
export const addressesApi = {
  async list(signal) {
    const data = await api.get('/me/addresses', null, signal)
    return (Array.isArray(data) ? data : data?.items || []).map(fromAddressDto)
  },

  async create(form) {
    return fromAddressDto(await api.post('/me/addresses', toAddressRequest(form)))
  },

  async update(id, form) {
    return fromAddressDto(await api.put(`/me/addresses/${id}`, toAddressRequest(form)))
  },

  async setDefault(address) {
    return fromAddressDto(await api.put(`/me/addresses/${address.id}`, toAddressRequest({ ...address, isDefault: true })))
  },

  remove(id) {
    return api.delete(`/me/addresses/${id}`)
  },
}

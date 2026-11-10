import { api } from './client'
import { mapApiProduct } from './mappers/product'

export const productsApi = {
  async list({ q, categoryId, page = 1, pageSize = 50 } = {}) {
    const data = await api.get('/products', { q, categoryId, page, pageSize })
    const items = (data.items || []).map(mapApiProduct)
    return {
      items,
      total: data.total ?? items.length,
      page: data.page ?? page,
      pageSize: data.pageSize ?? pageSize,
    }
  },

  async getBySlug(slug) {
    const data = await api.get(`/products/slug/${encodeURIComponent(slug)}`)
    return mapApiProduct(data)
  },

  async getById(id) {
    const data = await api.get(`/products/${id}`)
    return mapApiProduct(data)
  },
}

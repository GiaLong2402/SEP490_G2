import { api } from './client'

const SESSION_KEY = 'hbsisms.external.cartSession'
export const CART_SESSION_HEADER = 'X-Cart-Session'

export function getCartSessionToken() {
  try {
    let token = localStorage.getItem(SESSION_KEY)
    if (!token) {
      token = crypto.randomUUID()
      localStorage.setItem(SESSION_KEY, token)
    }
    return token
  } catch {
    return crypto.randomUUID()
  }
}

function sessionHeaders() {
  return { [CART_SESSION_HEADER]: getCartSessionToken() }
}

function mapCart(data) {
  const items = (data.items || []).map((line) => ({
    key: String(line.id),
    itemId: line.id,
    productId: String(line.productId),
    slug: line.productSlug,
    name: line.productName,
    image: line.imageUrl || '/images/demo-products/product-tom-su.png',
    quantity: line.qty,
    unitPrice: Number(line.unitPrice || 0),
    priceBasis: line.priceBasis,
    lineTotal: Number(line.lineTotal || 0),
    variant: {
      id: String(line.variantId),
      label: line.packLabel || line.sku || 'Quy cách',
      weightGrams: line.unitWeightG || 0,
      price: Number(line.unitPrice || 0),
      sku: line.sku,
    },
  }))

  return {
    id: data.id,
    sessionToken: data.sessionToken,
    status: data.status,
    items,
    totalItems: data.totalQty ?? items.reduce((s, i) => s + i.quantity, 0),
    totalAmount: Number(data.totalAmount || 0),
  }
}

export const cartApi = {
  async get() {
    const data = await api.request('GET', '/carts/current', { headers: sessionHeaders() })
    return mapCart(data)
  },

  async addItem(variantId, qty = 1) {
    const data = await api.post(
      '/carts/current/items',
      { variantId: Number(variantId), qty: Number(qty) },
      { headers: sessionHeaders() },
    )
    return mapCart(data)
  },

  async updateQty(itemId, qty) {
    const data = await api.put(
      `/carts/current/items/${itemId}`,
      { qty: Number(qty) },
      { headers: sessionHeaders() },
    )
    return mapCart(data)
  },

  async removeItem(itemId) {
    const data = await api.request('DELETE', `/carts/current/items/${itemId}`, {
      headers: sessionHeaders(),
    })
    return mapCart(data)
  },

  async clear() {
    const data = await api.request('DELETE', '/carts/current/items', {
      headers: sessionHeaders(),
    })
    return mapCart(data)
  },
}

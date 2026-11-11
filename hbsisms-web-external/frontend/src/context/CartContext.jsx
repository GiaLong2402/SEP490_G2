import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { cartApi } from '@/api/cart'

const CartContext = createContext(null)

/**
 * Giỏ B2C gắn API /carts/current (X-Cart-Session). Giá từ PriceResolver phía server.
 */
export function CartProvider({ children }) {
  const [items, setItems] = useState([])
  const [totalItems, setTotalItems] = useState(0)
  const [totalAmount, setTotalAmount] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const applyCart = useCallback((cart) => {
    setItems(cart.items || [])
    setTotalItems(cart.totalItems || 0)
    setTotalAmount(cart.totalAmount || 0)
  }, [])

  const refreshCart = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const cart = await cartApi.get()
      applyCart(cart)
    } catch (err) {
      setError(err?.message || 'Không tải được giỏ hàng')
      setItems([])
      setTotalItems(0)
      setTotalAmount(0)
    } finally {
      setLoading(false)
    }
  }, [applyCart])

  useEffect(() => {
    refreshCart()
  }, [refreshCart])

  async function addToCart({ product, variant, quantity = 1 }) {
    const variantId = variant?.id
    if (!variantId) throw new Error('Thiếu quy cách sản phẩm')
    setError('')
    try {
      const cart = await cartApi.addItem(variantId, quantity)
      applyCart(cart)
      return cart
    } catch (err) {
      setError(err?.message || 'Không thêm được vào giỏ')
      throw err
    }
  }

  async function updateQuantity(key, quantity) {
    const item = items.find((row) => row.key === key)
    if (!item) return
    setError('')
    try {
      if (quantity < 1) {
        const cart = await cartApi.removeItem(item.itemId)
        applyCart(cart)
        return
      }
      const cart = await cartApi.updateQty(item.itemId, quantity)
      applyCart(cart)
    } catch (err) {
      setError(err?.message || 'Không cập nhật được số lượng')
      throw err
    }
  }

  async function removeItem(key) {
    const item = items.find((row) => row.key === key)
    if (!item) return
    setError('')
    try {
      const cart = await cartApi.removeItem(item.itemId)
      applyCart(cart)
    } catch (err) {
      setError(err?.message || 'Không xóa được dòng giỏ')
      throw err
    }
  }

  async function clearCart() {
    setError('')
    try {
      const cart = await cartApi.clear()
      applyCart(cart)
    } catch (err) {
      setError(err?.message || 'Không xóa được giỏ hàng')
      throw err
    }
  }

  const value = useMemo(
    () => ({
      items,
      totalItems,
      totalAmount,
      loading,
      error,
      refreshCart,
      addToCart,
      updateQuantity,
      removeItem,
      clearCart,
    }),
    [items, totalItems, totalAmount, loading, error, refreshCart],
  )

  return <CartContext.Provider value={value}>{children}</CartContext.Provider>
}

export function useCart() {
  const ctx = useContext(CartContext)
  if (!ctx) {
    throw new Error('useCart phải dùng bên trong CartProvider')
  }
  return ctx
}

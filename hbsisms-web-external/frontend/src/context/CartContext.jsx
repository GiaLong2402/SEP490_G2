import { createContext, useContext, useMemo, useState } from 'react'

const CartContext = createContext(null)

/**
 * Giỏ hàng đơn giản (lưu trong memory).
 * Mỗi dòng: { key, productId, name, image, variant, quantity }
 */
export function CartProvider({ children }) {
  const [items, setItems] = useState([])

  function addToCart({ product, variant, quantity = 1 }) {
    const key = `${product.id}__${variant.id}`

    setItems((prev) => {
      const existing = prev.find((item) => item.key === key)
      if (existing) {
        return prev.map((item) =>
          item.key === key
            ? { ...item, quantity: item.quantity + quantity }
            : item,
        )
      }

      return [
        ...prev,
        {
          key,
          productId: product.id,
          slug: product.slug,
          name: product.name,
          image: product.primaryImageUrl,
          variant,
          quantity,
        },
      ]
    })
  }

  function updateQuantity(key, quantity) {
    if (quantity < 1) {
      removeItem(key)
      return
    }
    setItems((prev) =>
      prev.map((item) => (item.key === key ? { ...item, quantity } : item)),
    )
  }

  function removeItem(key) {
    setItems((prev) => prev.filter((item) => item.key !== key))
  }

  function clearCart() {
    setItems([])
  }

  const totalItems = items.reduce((sum, item) => sum + item.quantity, 0)
  const totalAmount = items.reduce(
    (sum, item) => sum + item.variant.price * item.quantity,
    0,
  )

  const value = useMemo(
    () => ({
      items,
      totalItems,
      totalAmount,
      addToCart,
      updateQuantity,
      removeItem,
      clearCart,
    }),
    [items, totalItems, totalAmount],
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

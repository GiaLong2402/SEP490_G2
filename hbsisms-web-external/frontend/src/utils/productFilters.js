import { PRICE_RANGES } from '@/constants/categories'

/**
 * Lọc + sắp xếp danh sách sản phẩm (dùng cho trang Products).
 * Viết rõ ràng từng bước để beginner dễ đọc.
 */
export function filterProducts(products, filters) {
  const { categoryId, priceRangeId, inStockOnly, sortId } = filters

  let result = [...products]

  // 1. Lọc theo danh mục
  if (categoryId && categoryId !== 'all') {
    result = result.filter((p) => p.categoryId === categoryId)
  }

  // 2. Lọc theo khoảng giá (giá của biến thể mặc định)
  if (priceRangeId && priceRangeId !== 'all') {
    const range = PRICE_RANGES.find((r) => r.id === priceRangeId)
    if (range) {
      result = result.filter((p) => {
        const price = getDefaultPrice(p)
        return price >= range.min && price <= range.max
      })
    }
  }

  // 3. Chỉ còn hàng
  if (inStockOnly) {
    result = result.filter((p) => p.inStock)
  }

  // 4. Sắp xếp
  if (sortId === 'price-asc') {
    result.sort((a, b) => getDefaultPrice(a) - getDefaultPrice(b))
  } else if (sortId === 'price-desc') {
    result.sort((a, b) => getDefaultPrice(b) - getDefaultPrice(a))
  } else if (sortId === 'newest') {
    result.sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt))
  }

  return result
}

/** Lấy giá biến thể mặc định (thường là 500g) */
export function getDefaultPrice(product) {
  const variant =
    product.variants.find((v) => v.id === product.defaultVariantId) ||
    product.variants[0]
  return variant ? variant.price : 0
}

/** Lấy biến thể mặc định */
export function getDefaultVariant(product) {
  return (
    product.variants.find((v) => v.id === product.defaultVariantId) ||
    product.variants[0]
  )
}

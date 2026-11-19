/**
 * Map ProductResponse (API) → dữ liệu sản phẩm dùng trên giao diện storefront.
 */
export function mapApiProduct(product) {
  const variants = (product.variants || [])
    .filter((v) => v.isActive !== false)
    .map((v) => ({
      id: String(v.id),
      label: v.packLabel || v.sku || 'Quy cách',
      weightGrams: v.unitWeightG || 0,
      price: Number(v.listPrice || 0),
      // List guest: available 0|1. Detail guest: real tray qty — display only, not cart max.
      stock: Number(v.available || 0),
      tiers: (v.tiers || []).map((t) => ({
        minQtyKhay: Number(t.minQtyKhay || 0),
        unitPrice: Number(t.unitPrice || 0),
      })),
    }))

  const primaryImageUrl = product.imageUrl || '/images/demo-products/product-tom-su.png'
  const gallery = Array.isArray(product.gallery) && product.gallery.length
    ? product.gallery
    : [primaryImageUrl]

  const inStock = variants.some((v) => v.stock > 0)
  const defaultVariantId = variants[0]?.id || null

  return {
    id: String(product.id),
    slug: product.slug || String(product.id),
    name: product.name,
    categoryId: product.categoryId != null ? String(product.categoryId) : 'all',
    categoryName: product.categoryName || 'Sản phẩm',
    description: product.description || '',
    primaryImageUrl,
    galleryImages: gallery,
    inStock,
    defaultVariantId,
    createdAt: product.createdAt || new Date().toISOString(),
    storageGuide: product.storageNote || '',
    specs: {
      productName: product.name,
      category: product.categoryName || '',
      packaging: variants[0]?.label || '',
      unit: 'khay',
      origin: product.origin || '',
    },
    receiveNotes: '',
    variants,
    isPublished: product.isPublished,
    isActive: product.isActive,
  }
}

export function getListingItemsFromProducts(products) {
  const items = []
  products.forEach((product) => {
    product.variants.forEach((variant) => {
      items.push({
        listingId: `${product.id}__${variant.id}`,
        productId: product.id,
        slug: product.slug,
        name: product.name,
        categoryId: product.categoryId,
        primaryImageUrl: product.primaryImageUrl,
        inStock: product.inStock && variant.stock > 0,
        variant,
        createdAt: product.createdAt,
      })
    })
  })
  return items
}

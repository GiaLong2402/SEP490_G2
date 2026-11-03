/**
 * Dữ liệu demo — sau này thay bằng API Spring Boot.
 * Mỗi sản phẩm dùng chung 1 ảnh cho trang chủ / danh sách / chi tiết.
 */

const IMG = {
  tom: '/images/demo-products/product-tom-su.png',
  muc: '/images/demo-products/product-muc-ong.png',
  cha: '/images/demo-products/product-cha-muc.png',
  ca: '/images/demo-products/product-ca-thu.png',
}

export const DEMO_PRODUCTS = [
  {
    id: 'p-tom-su',
    slug: 'tom-su-so-che',
    name: 'Tôm sú sơ chế',
    categoryId: 'tom',
    categoryName: 'Tôm',
    description:
      'Tôm sú tươi được sơ chế sạch, đóng khay rõ khối lượng — thuận tiện cho bữa cơm gia đình.',
    primaryImageUrl: IMG.tom,
    galleryImages: [IMG.tom],
    inStock: true,
    defaultVariantId: 'tom-500',
    createdAt: '2026-10-01',
    storageGuide:
      'Bảo quản ngăn đông. Rã đông trong ngăn mát trước khi chế biến. Dùng trong ngày sau khi rã đông.',
    specs: {
      productName: 'Tôm sú sơ chế',
      category: 'Tôm',
      packaging: 'Khay',
      unit: 'khay',
    },
    receiveNotes:
      'Kiểm tra niêm phong khay khi nhận. Nếu có bất thường, liên hệ cửa hàng ngay để được hỗ trợ.',
    variants: [
      { id: 'tom-500', label: 'Khay 500g', weightGrams: 500, price: 165000, stock: 40 },
      { id: 'tom-1000', label: 'Khay 1kg', weightGrams: 1000, price: 330000, stock: 20 },
    ],
  },
  {
    id: 'p-muc-ong',
    slug: 'muc-ong-lam-sach',
    name: 'Mực ống làm sạch',
    categoryId: 'muc',
    categoryName: 'Mực',
    description:
      'Mực ống đã làm sạch, sẵn sàng chế biến — giữ độ tươi và vị ngọt tự nhiên.',
    primaryImageUrl: IMG.muc,
    galleryImages: [IMG.muc],
    inStock: true,
    defaultVariantId: 'muc-500',
    createdAt: '2026-10-02',
    storageGuide:
      'Bảo quản ngăn đông. Không để tan đông rồi đông lại nhiều lần.',
    specs: {
      productName: 'Mực ống làm sạch',
      category: 'Mực',
      packaging: 'Khay',
      unit: 'khay',
    },
    receiveNotes:
      'Kiểm tra khối lượng và tình trạng khay khi nhận hàng.',
    variants: [
      { id: 'muc-500', label: 'Khay 500g', weightGrams: 500, price: 145000, stock: 35 },
      { id: 'muc-1000', label: 'Khay 1kg', weightGrams: 1000, price: 290000, stock: 15 },
    ],
  },
  {
    id: 'p-cha-muc',
    slug: 'cha-muc-hoa-binh',
    name: 'Chả mực Hòa Bình',
    categoryId: 'che-bien',
    categoryName: 'Hải sản chế biến',
    description:
      'Chả mực vàng giòn, đậm vị biển — phù hợp chiên hoặc hấp cho bữa cơm nhà.',
    primaryImageUrl: IMG.cha,
    galleryImages: [IMG.cha],
    inStock: true,
    defaultVariantId: 'cha-500',
    createdAt: '2026-10-03',
    storageGuide:
      'Bảo quản ngăn mát hoặc ngăn đông theo hướng dẫn trên bao bì.',
    specs: {
      productName: 'Chả mực Hòa Bình',
      category: 'Hải sản chế biến',
      packaging: 'Khay',
      unit: 'khay',
    },
    receiveNotes:
      'Giữ lạnh sau khi nhận. Dùng sớm để giữ độ ngon.',
    variants: [
      { id: 'cha-500', label: 'Khay 500g', weightGrams: 500, price: 180000, stock: 25 },
    ],
  },
  {
    id: 'p-ca-thu',
    slug: 'ca-thu-cat-khuc',
    name: 'Cá thu cắt khúc',
    categoryId: 'ca',
    categoryName: 'Cá biển',
    description:
      'Cá thu cắt khúc sẵn, da bạc thịt nhạt — thuận tiện kho, chiên hoặc nướng.',
    primaryImageUrl: IMG.ca,
    galleryImages: [IMG.ca],
    inStock: true,
    defaultVariantId: 'ca-500',
    createdAt: '2026-10-04',
    storageGuide:
      'Bảo quản ngăn đông. Rã đông chậm trong ngăn mát.',
    specs: {
      productName: 'Cá thu cắt khúc',
      category: 'Cá biển',
      packaging: 'Khay',
      unit: 'khay',
    },
    receiveNotes:
      'Kiểm tra độ tươi và niêm phong khi nhận.',
    variants: [
      { id: 'ca-500', label: 'Khay 500g', weightGrams: 500, price: 125000, stock: 30 },
    ],
  },
]

/**
 * Trang danh sách hiển thị mỗi quy cách (Khay 500g, Khay 1kg) thành 1 card riêng.
 * Đây là "lựa chọn hàng/quy cách" minh họa — không nhân bản sản phẩm thật.
 */
export function getListingItems(products = DEMO_PRODUCTS) {
  const items = []

  products.forEach((product) => {
    product.variants.forEach((variant) => {
      items.push({
        // id riêng cho mỗi quy cách trên grid
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

export function findProductBySlug(slug) {
  return DEMO_PRODUCTS.find((p) => p.slug === slug) || null
}

export function getRelatedProducts(product, limit = 3) {
  return DEMO_PRODUCTS.filter((p) => p.id !== product.id).slice(0, limit)
}

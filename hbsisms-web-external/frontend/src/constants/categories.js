/**
 * 4 danh mục đã chốt — KHÔNG có cua/ghẹ/ngao/sò.
 */
export const CATEGORIES = [
  { id: 'all', slug: 'tat-ca', name: 'Tất cả', shortName: 'Tất cả' },
  { id: 'tom', slug: 'tom', name: 'Tôm', shortName: 'Tôm' },
  { id: 'ca', slug: 'ca-bien', name: 'Cá biển', shortName: 'Cá' },
  { id: 'muc', slug: 'muc', name: 'Mực', shortName: 'Mực' },
  {
    id: 'che-bien',
    slug: 'hai-san-che-bien',
    name: 'Hải sản chế biến',
    shortName: 'Chế biến',
  },
]

/** Danh mục hiển thị vòng tròn trên homepage (không gồm "Tất cả") */
export const HOME_CATEGORIES = CATEGORIES.filter((c) => c.id !== 'all')

/** Khoảng giá đã chốt trên trang sản phẩm */
export const PRICE_RANGES = [
  { id: 'all', label: 'Tất cả mức giá' },
  { id: 'under-100', label: 'Dưới 100.000đ', min: 0, max: 99999 },
  { id: '100-200', label: '100.000đ–200.000đ', min: 100000, max: 200000 },
  { id: 'over-200', label: 'Trên 200.000đ', min: 200001, max: Infinity },
]

/** Tuỳ chọn sắp xếp */
export const SORT_OPTIONS = [
  { id: 'default', label: 'Sắp xếp: Mặc định' },
  { id: 'price-asc', label: 'Giá tăng dần' },
  { id: 'price-desc', label: 'Giá giảm dần' },
  { id: 'newest', label: 'Mới nhất' },
]

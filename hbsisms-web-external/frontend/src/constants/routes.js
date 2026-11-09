/**
 * Đường dẫn dùng chung — đổi 1 chỗ là toàn app cập nhật.
 */
export const ROUTES = {
  // Storefront (guest + customer)
  HOME: '/',
  PRODUCTS: '/san-pham',
  PRODUCT_DETAIL: '/san-pham/:slug',
  ABOUT: '/ve-hoa-binh',
  CONTACT: '/lien-he',
  LOGIN: '/dang-nhap',
  CART: '/gio-hang',
  ACCOUNT: '/tai-khoan',
  ADDRESSES: '/tai-khoan/so-dia-chi',
  ORDERS: '/don-hang',
  DISPLAY_SETTINGS: '/cai-dat-hien-thi',

  // Khu vực nhân viên / quản trị
  SALE_DASHBOARD: '/nv-ban-hang',
  WAREHOUSE_DASHBOARD: '/nv-kho',
  OWNER_DASHBOARD: '/chu-cua-hang',
  ADMIN_DASHBOARD: '/quan-tri',

  // Chung
  UNAUTHORIZED: '/khong-co-quyen',
  NOT_FOUND: '*',
}

/** Tạo URL chi tiết sản phẩm từ slug */
export function productDetailPath(slug) {
  return `/san-pham/${slug}`
}

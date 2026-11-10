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
  CHECKOUT: '/thanh-toan',
  PAYMENT_RESULT: '/thanh-toan/ket-qua',
  ACCOUNT: '/tai-khoan',
  ADDRESSES: '/tai-khoan/so-dia-chi',
  ORDERS: '/don-hang',
  ORDER_DETAIL: '/don-hang/:code',
  ORDER_RECEIPT: '/don-hang/:code/bien-lai',
  REQUEST_DETAIL: '/yeu-cau/:code',
  DISPLAY_SETTINGS: '/cai-dat-hien-thi',


  // Chung
  NOT_FOUND: '*',
}

/** Tạo URL chi tiết sản phẩm từ slug */
export function productDetailPath(slug) {
  return `/san-pham/${slug}`
}

/** URL chi tiết đơn: "HB00123" hoặc "#HB00123" -> /don-hang/HB00123 */
export function orderDetailPath(code) {
  return `/don-hang/${encodeURIComponent(code.replace('#', ''))}`
}

/** URL biên lai của đơn */
export function orderReceiptPath(code) {
  return `${orderDetailPath(code)}/bien-lai`
}

/** URL chi tiết yêu cầu: "YC-0007" -> /yeu-cau/YC-0007 */
export function requestDetailPath(code) {
  return `/yeu-cau/${encodeURIComponent(code)}`
}

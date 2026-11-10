/**
 * Vai trò người dùng trong hệ thống Hòa Bình.
 * Dùng hằng số này mọi nơi — tránh gõ sai chuỗi.
 */
export const ROLES = {
  GUEST: 'guest',
  CUSTOMER: 'customer',
  SALE_STAFF: 'sale_staff',
  WAREHOUSE_STAFF: 'warehouse_staff',
  OWNER: 'owner',
  ADMIN: 'admin',
}

/** Nhãn tiếng Việt để hiển thị */
export const ROLE_LABELS = {
  [ROLES.GUEST]: 'Khách vãng lai',
  [ROLES.CUSTOMER]: 'Khách hàng',
  [ROLES.SALE_STAFF]: 'Nhân viên bán hàng',
  [ROLES.WAREHOUSE_STAFF]: 'Nhân viên kho',
  [ROLES.OWNER]: 'Chủ cửa hàng',
  [ROLES.ADMIN]: 'Quản trị viên',
}

/** Tài khoản nội bộ — làm việc ở web nội bộ, không dùng web external */
export const STAFF_ROLES = [
  ROLES.SALE_STAFF,
  ROLES.WAREHOUSE_STAFF,
  ROLES.OWNER,
  ROLES.ADMIN,
]

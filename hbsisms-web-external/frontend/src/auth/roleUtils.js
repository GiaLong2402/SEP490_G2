import { STAFF_ROLES } from '@/constants/roles'
import { ROUTES } from '@/constants/routes'

/**
 * Web external chỉ dành cho khách (guest / customer).
 * Tài khoản nội bộ (NV bán hàng, NV kho, chủ cửa hàng, admin) bị từ chối khi đăng nhập
 * ("Tài khoản không tồn tại." — xem AuthContext) và làm việc ở hbsisms-web-internal.
 */
export function isStaff(role) {
  return STAFF_ROLES.includes(role)
}

/** Sau đăng nhập luôn về trang chủ bán hàng */
export function getHomePathForRole() {
  return ROUTES.HOME
}

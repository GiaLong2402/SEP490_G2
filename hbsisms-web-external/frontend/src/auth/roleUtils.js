import { ADMIN_ROLES, ROLES, STAFF_ROLES } from '@/constants/roles'
import { ROUTES } from '@/constants/routes'

/** Kiểm tra user có nằm trong danh sách role cho phép không */
export function hasRole(userRole, allowedRoles = []) {
  if (!allowedRoles.length) return true
  return allowedRoles.includes(userRole)
}

export function isStaff(role) {
  return STAFF_ROLES.includes(role)
}

export function isAdminLevel(role) {
  return ADMIN_ROLES.includes(role)
}

/**
 * Sau đăng nhập, đưa user về đúng khu vực theo role.
 * Guest/Customer → trang chủ bán hàng.
 */
export function getHomePathForRole(role) {
  switch (role) {
    case ROLES.SALE_STAFF:
      return ROUTES.SALE_DASHBOARD
    case ROLES.WAREHOUSE_STAFF:
      return ROUTES.WAREHOUSE_DASHBOARD
    case ROLES.OWNER:
      return ROUTES.OWNER_DASHBOARD
    case ROLES.ADMIN:
      return ROUTES.ADMIN_DASHBOARD
    case ROLES.CUSTOMER:
    case ROLES.GUEST:
    default:
      return ROUTES.HOME
  }
}

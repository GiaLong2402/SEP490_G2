import { ROLES } from '@/constants/roles'

/** Map roleCode từ API (sales/warehouse/…) → role UI của web external. */
export function mapApiRoleCode(roleCode) {
  switch (roleCode) {
    case 'admin':
      return ROLES.ADMIN
    case 'owner':
      return ROLES.OWNER
    case 'sales':
      return ROLES.SALE_STAFF
    case 'warehouse':
      return ROLES.WAREHOUSE_STAFF
    case 'customer':
      return ROLES.CUSTOMER
    default:
      return ROLES.GUEST
  }
}

export function mapApiUser(apiUser) {
  if (!apiUser) return null
  return {
    id: String(apiUser.id),
    name: apiUser.fullName || apiUser.username,
    username: apiUser.username,
    email: apiUser.email || null,
    role: mapApiRoleCode(apiUser.roleCode),
    roleCode: apiUser.roleCode,
    roleName: apiUser.roleName,
    permissions: apiUser.permissions || [],
    phone: null,
    address: null,
    keepLoggedIn: true,
  }
}

import type { CurrentUser } from './types'

/** Customer chỉ dùng web external — không vào internal. */
export const STAFF_ONLY_MESSAGE =
  'Tài khoản khách hàng không dùng được hệ thống nội bộ. Hãy đăng nhập trên web bán hàng.'

export function isCustomerRole(roleCode: string | null | undefined): boolean {
  return roleCode === 'customer'
}

export function assertInternalStaff(user: CurrentUser): void {
  if (isCustomerRole(user.roleCode)) {
    throw new Error(STAFF_ONLY_MESSAGE)
  }
}

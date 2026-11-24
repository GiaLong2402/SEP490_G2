/** Role codes khớp DefaultRoles API. Owner và admin chung quyền cao (sơ đồ Inter). */

export function isOwnerOrAdmin(roleCode: string | null | undefined): boolean {
  return roleCode === 'admin' || roleCode === 'owner';
}

/** Thao tác kho/xưởng: warehouse + owner + admin. */
export function canWarehouseOps(roleCode: string | null | undefined): boolean {
  return isOwnerOrAdmin(roleCode) || roleCode === 'warehouse';
}

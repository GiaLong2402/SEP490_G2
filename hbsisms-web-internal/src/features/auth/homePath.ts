/** Trang mặc định sau đăng nhập theo roleCode API. */
export function homePathForRole(roleCode: string | null | undefined): string {
  switch (roleCode) {
    case 'warehouse':
      return '/inventory'
    case 'sales':
      return '/pos'
    case 'admin':
      return '/admin/users'
    case 'owner':
      return '/'
    default:
      return '/'
  }
}

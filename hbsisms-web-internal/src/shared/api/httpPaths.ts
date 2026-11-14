/**
 * Contract path bán hàng (Inter 2 — Task 10).
 * Đối chiếu `features/orders|requests|pos|payments|pricing` + BE controllers.
 *
 * Hybrid gắn dần: set `VITE_API_HTTP_PATHS` (CSV prefix) khi `VITE_API_MODE=mock`
 * để chỉ các path này đi HTTP; còn lại giữ mock. `/pos` có trong catalog nhưng
 * mặc định không gắn HTTP (chưa có PosController).
 */

export const API_PATHS = {
  auth: {
    login: '/auth/login',
    me: '/auth/me',
    logout: '/auth/logout',
  },
  orders: {
    root: '/orders',
    byId: (id: string | number) => `/orders/${id}`,
    transition: (id: string | number) => `/orders/${id}/transition`,
    cancel: (id: string | number) => `/orders/${id}/cancel`,
    receipt: (id: string | number) => `/orders/${id}/receipt`,
    completeAtCounter: (id: string | number) => `/orders/${id}/complete-at-counter`,
  },
  salesRequests: {
    root: '/sales-requests',
    byId: (id: string | number) => `/sales-requests/${id}`,
    feasibility: (id: string | number) => `/sales-requests/${id}/feasibility`,
    reject: (id: string | number) => `/sales-requests/${id}/reject`,
  },
  payments: {
    root: '/payments',
    refund: '/payments/refund',
    reconcile: (id: string | number) => `/payments/${id}/reconcile`,
  },
  pricing: {
    resolve: '/pricing/resolve',
  },
  pos: {
    catalog: '/pos/catalog',
    checkout: '/pos/checkout',
  },
  sales: {
    holdExpire: '/sales/hold-expire',
  },
} as const;

/** Prefix gắn HTTP khi hybrid (orders / requests / payments / pricing + auth/customers hỗ trợ form). Không gồm /pos. */
export const SALES_READY_HTTP_PATH_PREFIXES = [
  '/auth',
  '/orders',
  '/sales-requests',
  '/payments',
  '/pricing',
  '/customers',
] as const;

/** Giá trị gợi ý cho `.env` khi gắn Sales (không gồm /pos — vẫn mock). */
export const SALES_DEFAULT_HTTP_PATHS_CSV = SALES_READY_HTTP_PATH_PREFIXES.join(',');

/** Toàn bộ prefix contract bán hàng (kể cả POS mock-only). */
export const SALES_CONTRACT_PATH_PREFIXES = [
  ...SALES_READY_HTTP_PATH_PREFIXES,
  '/pos',
  '/sales',
] as const;

/** Parse `VITE_API_HTTP_PATHS` CSV → danh sách prefix đã chuẩn hoá. */
export function parseHttpPathPrefixes(raw: string | undefined | null): string[] {
  if (!raw || !raw.trim()) return [];
  return raw
    .split(',')
    .map((p) => p.trim())
    .filter(Boolean)
    .map((p) => (p.startsWith('/') ? p : `/${p}`))
    .map((p) => (p.length > 1 && p.endsWith('/') ? p.slice(0, -1) : p));
}

export function pathMatchesHttpPrefix(path: string, prefixes: string[]): boolean {
  if (prefixes.length === 0) return false;
  const bare = path.split('?')[0] || path;
  return prefixes.some((prefix) => bare === prefix || bare.startsWith(`${prefix}/`));
}

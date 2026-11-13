import { parseHttpPathPrefixes } from './httpPaths';

export const API_MODE = import.meta.env.VITE_API_MODE === 'http' ? 'http' : 'mock';
export const API_BASE_URL: string = import.meta.env.VITE_API_BASE_URL ?? '/api/v1';
export const isMockMode = API_MODE === 'mock';

/**
 * Khi `VITE_API_MODE=mock` và biến này có giá trị: các prefix khớp đi HTTP,
 * path còn lại giữ mock (gắn dần Sales). Full `http` mode bỏ qua biến này.
 * Ví dụ: `/auth,/orders,/sales-requests,/payments,/pricing,/customers`
 */
export const HTTP_PATH_PREFIXES: string[] = parseHttpPathPrefixes(
  import.meta.env.VITE_API_HTTP_PATHS as string | undefined,
);

export const isHybridMode = isMockMode && HTTP_PATH_PREFIXES.length > 0;

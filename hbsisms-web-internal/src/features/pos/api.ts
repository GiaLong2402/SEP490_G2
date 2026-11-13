import { api } from '@/shared/api/client';
import { API_PATHS } from '@/shared/api/httpPaths';
import type { CatalogItem, CheckoutInput, Receipt } from './types';

/** POS: contract path đã đăng ký; BE `/pos/*` chưa có — giữ mock (không đưa `/pos` vào VITE_API_HTTP_PATHS). */
export const posApi = {
  catalog: () => api.get<CatalogItem[]>(API_PATHS.pos.catalog),
  checkout: (body: CheckoutInput) => api.post<Receipt>(API_PATHS.pos.checkout, body),
};
